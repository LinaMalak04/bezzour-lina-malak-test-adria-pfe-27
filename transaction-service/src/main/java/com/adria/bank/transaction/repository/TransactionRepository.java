package com.adria.bank.transaction.repository;

import com.adria.bank.transaction.domain.Transaction;
import com.adria.bank.transaction.domain.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    List<Transaction> findByAccountIdOrderByCreatedAtDesc(UUID accountId);

    List<Transaction> findByTransferId(UUID transferId);

    List<Transaction> findByStatusAndCreatedAtBefore(TransactionStatus status, Instant before);
}
