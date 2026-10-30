package com.adria.bank.transaction;

import com.adria.bank.transaction.client.AccountRestClient;
import com.adria.bank.transaction.client.AccountRestClient.AccountDto;
import com.adria.bank.transaction.config.TransactionProperties;
import com.adria.bank.transaction.domain.AccountServiceUnavailableException;
import com.adria.bank.transaction.domain.BusinessRuleException;
import com.adria.bank.transaction.domain.Transaction;
import com.adria.bank.transaction.domain.TransactionStatus;
import com.adria.bank.transaction.messaging.TransactionEventPublisher;
import com.adria.bank.transaction.repository.TransactionRepository;
import com.adria.bank.transaction.service.PendingTransactionReconciler;
import com.adria.bank.transaction.service.TransactionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import feign.Request;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 3, topics = TransactionEventPublisher.TOPIC)
@TestPropertySource(properties = "bank.reconciliation.min-age-seconds=-60")
class TransactionFlowIntegrationTest {
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID TARGET_ID = UUID.randomUUID();

    @Autowired
    private TransactionService transactionService;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private PendingTransactionReconciler reconciler;
    @Autowired
    private TransactionProperties properties;
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;
    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;
    @MockitoBean
    private AccountRestClient accountRestClient;

    @BeforeEach
    void setUp() {
        reset(accountRestClient);
        transactionRepository.deleteAll();
        circuitBreakerRegistry.circuitBreaker("accountService").reset();
        when(accountRestClient.getAccount(ACCOUNT_ID)).thenReturn(new AccountDto(ACCOUNT_ID, "CUST-1", "ACTIVE", "EUR", 500));
        when(accountRestClient.getAccount(TARGET_ID)).thenReturn(new AccountDto(TARGET_ID, "CUST-2", "ACTIVE", "EUR", 0));
    }

    private Transaction pay(String amount) {
        return transactionService.createTransaction(ACCOUNT_ID, new BigDecimal(amount), "EUR", "GROCERY", "MA");
    }

    private FeignException serverDown() {
        Request request = Request.create(Request.HttpMethod.POST, "/", Map.of(), new byte[0], StandardCharsets.UTF_8, null);
        return new FeignException.ServiceUnavailable("down", request, null, Map.of());
    }

    @Test
    void validTransactionIsCompletedAndPublishedWithAccountIdAsKey() throws Exception {
        Map<String, Object> consumerProps = new HashMap<>(KafkaTestUtils.consumerProps("flow-test", "true", embeddedKafka));
        consumerProps.put("auto.offset.reset", "earliest");
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(consumerProps,
                new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            embeddedKafka.consumeFromAnEmbeddedTopic(consumer, TransactionEventPublisher.TOPIC);

            Transaction transaction = pay("250.00");

            assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
            verify(accountRestClient).applyBalanceOperation(eq(ACCOUNT_ID), any());
            // The topic also holds the events of the other tests : only this transaction is looked at
            ConsumerRecord<String, String> record = null;
            long deadline = System.currentTimeMillis() + 15_000;
            while (record == null && System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> candidate : consumer.poll(Duration.ofMillis(500))) {
                    if (candidate.value().contains(transaction.getId().toString())) {
                        record = candidate;
                    }
                }
            }
            assertThat(record).isNotNull();
            assertThat(record.key()).isEqualTo(ACCOUNT_ID.toString());
            JsonNode event = new ObjectMapper().readTree(record.value());
            assertThat(event.get("eventType").asText()).isEqualTo("TransactionCreated");
            assertThat(event.get("amount").decimalValue()).isEqualByComparingTo("250.00");
        }
    }

    @Test
    void amountAboveTheConfiguredCeilingIsRefusedWithoutTouchingTheBalance() {
        assertThatThrownBy(() -> pay("1000.01")).isInstanceOf(BusinessRuleException.class).hasMessageContaining("ceiling");
        verify(accountRestClient, never()).applyBalanceOperation(any(), any());
    }

    @Test
    void amountWithMoreThanTwoDecimalsIsRefused() {
        assertThatThrownBy(() -> pay("10.005")).isInstanceOf(BusinessRuleException.class).hasMessageContaining("2 decimals");
        verify(accountRestClient, never()).applyBalanceOperation(any(), any());
    }

    @Test
    void ceilingFollowsTheConfigurationWithoutRestart() {
        properties.setMaxAmount(new BigDecimal("100"));
        try {
            assertThatThrownBy(() -> pay("200")).isInstanceOf(BusinessRuleException.class);
        } finally {
            properties.setMaxAmount(new BigDecimal("1000"));
        }
    }

    @Test
    void blockedAccountWrongCurrencyAndInsufficientBalanceAreRefused() {
        assertThatThrownBy(() -> pay("600")).isInstanceOf(BusinessRuleException.class).hasMessageContaining("Insufficient");
        assertThatThrownBy(() -> transactionService.createTransaction(ACCOUNT_ID, BigDecimal.TEN, "USD", "GROCERY", "MA"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("is in EUR");
        when(accountRestClient.getAccount(ACCOUNT_ID)).thenReturn(new AccountDto(ACCOUNT_ID, "CUST-1", "BLOCKED", "EUR", 500));
        assertThatThrownBy(() -> pay("10")).isInstanceOf(BusinessRuleException.class).hasMessageContaining("not active");
    }

    @Test
    void businessRefusalIsNotRetriedAndMarksTheTransactionRejected() {
        doThrow(new BusinessRuleException("INSUFFICIENT_BALANCE", "Insufficient balance"))
                .when(accountRestClient).applyBalanceOperation(any(), any());

        assertThatThrownBy(() -> pay("10")).isInstanceOf(BusinessRuleException.class);

        verify(accountRestClient, times(1)).applyBalanceOperation(any(), any());
        assertThat(transactionRepository.findAll()).singleElement()
                .satisfies(t -> assertThat(t.getStatus()).isEqualTo(TransactionStatus.REJECTED));
    }

    @Test
    void readOutageFailsClosedAfterRetries() {
        when(accountRestClient.getAccount(ACCOUNT_ID)).thenThrow(serverDown());

        assertThatThrownBy(() -> pay("10")).isInstanceOf(AccountServiceUnavailableException.class);

        verify(accountRestClient, times(3)).getAccount(ACCOUNT_ID);
        assertThat(transactionRepository.findAll()).isEmpty();
    }

    @Test
    void writeOutageStaysPendingThenTheReconcilerFinishesWithTheSameOperationId() {
        doThrow(serverDown()).when(accountRestClient).applyBalanceOperation(any(), any());

        Transaction pending = pay("10");
        assertThat(pending.getStatus()).isEqualTo(TransactionStatus.PENDING);
        verify(accountRestClient, times(3)).applyBalanceOperation(any(), any());

        reset(accountRestClient);
        circuitBreakerRegistry.circuitBreaker("accountService").reset();
        reconciler.reconcile();

        verify(accountRestClient, times(1)).applyBalanceOperation(eq(ACCOUNT_ID),
                org.mockito.ArgumentMatchers.argThat(request -> request.operationId().equals(pending.getId())));
        assertThat(transactionService.getTransaction(pending.getId()).getStatus()).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    void transferCreatesTwoLegsAndCallsTheAtomicOperationOnce() {
        var legs = transactionService.transfer(ACCOUNT_ID, TARGET_ID, new BigDecimal("100"), "EUR");

        assertThat(legs).hasSize(2).allSatisfy(leg -> assertThat(leg.getStatus()).isEqualTo(TransactionStatus.COMPLETED));
        assertThat(legs.get(0).getTransferId()).isEqualTo(legs.get(1).getTransferId());
        verify(accountRestClient, times(1)).transfer(any());
    }

    @Test
    void transferToTheSameAccountIsRefused() {
        assertThatThrownBy(() -> transactionService.transfer(ACCOUNT_ID, ACCOUNT_ID, BigDecimal.TEN, "EUR"))
                .isInstanceOf(BusinessRuleException.class);
    }
}
