package com.adria.bank.transaction.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class TransactionEventPublisher {
    public static final String TOPIC = "bank.transactions.created";

    private final KafkaTemplate<String, TransactionCreatedEvent> kafkaTemplate;

    @Bean
    public NewTopic transactionsCreatedTopic() {
        return TopicBuilder.name(TOPIC).partitions(3).replicas(1).build();
    }

    // After the commit only : a rollback can never publish an event for a transaction that does not exist.
    // The accountId key keeps the events of one account ordered in a single partition.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(TransactionCreatedEvent event) {
        try {
            kafkaTemplate.send(TOPIC, event.accountId().toString(), event).whenComplete((result, error) -> {
                if (error != null) {
                    log.error("Event {} of transaction {} not published", event.eventId(), event.transactionId(), error);
                }
            });
        } catch (RuntimeException e) {
            // The money has moved : a Kafka outage must not turn a committed transaction into an API error
            log.error("Event {} of transaction {} not published", event.eventId(), event.transactionId(), e);
        }
    }
}
