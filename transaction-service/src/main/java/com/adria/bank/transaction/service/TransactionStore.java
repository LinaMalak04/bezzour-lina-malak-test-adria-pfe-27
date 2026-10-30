package com.adria.bank.transaction.service;

import com.adria.bank.transaction.domain.Transaction;
import com.adria.bank.transaction.domain.TransactionNotFoundException;
import com.adria.bank.transaction.domain.TransactionStatus;
import com.adria.bank.transaction.messaging.TransactionCreatedEvent;
import com.adria.bank.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

// Short local transactions : the database connection is never held during a remote call to account-service
@Component
@RequiredArgsConstructor
public class TransactionStore {
    private final TransactionRepository transactionRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public List<Transaction> savePending(List<Transaction> transactions) {
        return transactionRepository.saveAll(transactions);
    }

    @Transactional
    public List<Transaction> complete(List<UUID> ids) {
        List<Transaction> transactions = load(ids);
        transactions.stream()
                .filter(transaction -> transaction.getStatus() == TransactionStatus.PENDING)
                .forEach(transaction -> {
                    transaction.complete();
                    eventPublisher.publishEvent(TransactionCreatedEvent.of(transaction));
                });
        return transactions;
    }

    @Transactional
    public List<Transaction> reject(List<UUID> ids, String reason) {
        List<Transaction> transactions = load(ids);
        transactions.stream()
                .filter(transaction -> transaction.getStatus() == TransactionStatus.PENDING)
                .forEach(transaction -> transaction.reject(reason));
        return transactions;
    }

    private List<Transaction> load(List<UUID> ids) {
        return ids.stream()
                .map(id -> transactionRepository.findById(id).orElseThrow(() -> new TransactionNotFoundException(id)))
                .toList();
    }
}
