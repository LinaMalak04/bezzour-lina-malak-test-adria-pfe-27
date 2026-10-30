package com.adria.bank.transaction.service;

import com.adria.bank.transaction.client.AccountGateway;
import com.adria.bank.transaction.domain.BusinessRuleException;
import com.adria.bank.transaction.domain.Transaction;
import com.adria.bank.transaction.domain.TransactionStatus;
import com.adria.bank.transaction.domain.TransactionType;
import com.adria.bank.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// Finishes the transactions left PENDING by an outage : same operation id, so a debit already applied is not repeated
@Component
@RequiredArgsConstructor
public class PendingTransactionReconciler {
    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;
    private final AccountGateway accountGateway;

    @Value("${bank.reconciliation.min-age-seconds}")
    private long minAgeSeconds;

    @Scheduled(fixedDelayString = "${bank.reconciliation.delay-ms}")
    public void reconcile() {
        Instant olderThan = Instant.now().minusSeconds(minAgeSeconds);
        Set<UUID> transfersDone = new HashSet<>();
        for (Transaction transaction : transactionRepository.findByStatusAndCreatedAtBefore(TransactionStatus.PENDING, olderThan)) {
            try {
                if (transaction.getTransferId() == null) {
                    transactionService.settle(List.of(transaction), () -> accountGateway.debit(transaction.getId(),
                            transaction.getAccountId(), transaction.getAmount(), transaction.getCurrency()));
                } else if (transfersDone.add(transaction.getTransferId())) {
                    settleTransfer(transaction.getTransferId());
                }
            } catch (BusinessRuleException ignored) {
                // Already recorded as REJECTED by settle : nobody is waiting for this answer anymore
            }
        }
    }

    private void settleTransfer(UUID transferId) {
        List<Transaction> legs = transactionRepository.findByTransferId(transferId);
        Transaction debit = legs.stream().filter(leg -> leg.getType() == TransactionType.DEBIT).findFirst().orElseThrow();
        Transaction credit = legs.stream().filter(leg -> leg.getType() == TransactionType.CREDIT).findFirst().orElseThrow();
        transactionService.settle(legs, () -> accountGateway.transfer(transferId, debit.getAccountId(), credit.getAccountId(),
                debit.getAmount(), debit.getCurrency()));
    }
}
