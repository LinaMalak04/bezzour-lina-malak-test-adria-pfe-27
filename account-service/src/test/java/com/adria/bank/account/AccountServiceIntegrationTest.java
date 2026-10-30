package com.adria.bank.account;

import com.adria.bank.account.domain.Account;
import com.adria.bank.account.domain.AccountStatus;
import com.adria.bank.account.domain.BusinessRuleException;
import com.adria.bank.account.service.AccountService;
import com.adria.bank.account.service.AccountService.BalanceOperationType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AccountServiceIntegrationTest {
    @Autowired
    private AccountService accountService;

    private Account newAccount(double balance) {
        return accountService.createAccount("CUST-TEST", "EUR", balance);
    }

    @Test
    void replayedOperationIsAppliedOnlyOnce() {
        Account account = newAccount(100);
        UUID operationId = UUID.randomUUID();

        accountService.applyBalanceOperation(operationId, account.getId(), BalanceOperationType.DEBIT, new BigDecimal("30"), "EUR");
        accountService.applyBalanceOperation(operationId, account.getId(), BalanceOperationType.DEBIT, new BigDecimal("30"), "EUR");

        assertThat(accountService.getAccount(account.getId()).getBalance()).isEqualTo(70);
    }

    @Test
    void decimalArithmeticDoesNotDrift() {
        Account account = newAccount(0.3);

        accountService.applyBalanceOperation(UUID.randomUUID(), account.getId(), BalanceOperationType.DEBIT, new BigDecimal("0.1"), "EUR");

        assertThat(accountService.getAccount(account.getId()).getBalance()).isEqualTo(0.2);
    }

    @Test
    void blockedAccountRejectsOperations() {
        Account account = newAccount(100);
        accountService.changeStatus(account.getId(), AccountStatus.BLOCKED);

        assertThatThrownBy(() -> accountService.applyBalanceOperation(UUID.randomUUID(), account.getId(),
                BalanceOperationType.DEBIT, BigDecimal.TEN, "EUR"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void failedTransferMovesNothing() {
        Account source = newAccount(100);
        Account target = newAccount(100);
        accountService.changeStatus(target.getId(), AccountStatus.BLOCKED);

        assertThatThrownBy(() -> accountService.transfer(UUID.randomUUID(), source.getId(), target.getId(), BigDecimal.TEN, "EUR"))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(accountService.getAccount(source.getId()).getBalance()).isEqualTo(100);
        assertThat(accountService.getAccount(target.getId()).getBalance()).isEqualTo(100);
    }

    @Test
    void concurrentDebitsNeverOverdraw() throws InterruptedException {
        Account account = newAccount(100);
        AtomicInteger succeeded = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(8);

        for (int i = 0; i < 20; i++) {
            pool.submit(() -> {
                try {
                    accountService.applyBalanceOperation(UUID.randomUUID(), account.getId(),
                            BalanceOperationType.DEBIT, BigDecimal.TEN, "EUR");
                    succeeded.incrementAndGet();
                } catch (BusinessRuleException ignored) {
                    // insufficient balance : expected once the account is empty
                }
            });
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(succeeded.get()).isEqualTo(10);
        assertThat(accountService.getAccount(account.getId()).getBalance()).isZero();
    }
}
