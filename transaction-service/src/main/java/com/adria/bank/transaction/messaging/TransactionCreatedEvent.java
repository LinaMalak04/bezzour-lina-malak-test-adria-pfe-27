package com.adria.bank.transaction.messaging;

import com.adria.bank.transaction.domain.Transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionCreatedEvent(UUID eventId, String eventType, UUID transactionId, UUID accountId,
                                      BigDecimal amount, String currency, String occurredAt) {

    public static TransactionCreatedEvent of(Transaction transaction) {
        return new TransactionCreatedEvent(UUID.randomUUID(), "TransactionCreated", transaction.getId(), transaction.getAccountId(),
                transaction.getAmount(), transaction.getCurrency(), Instant.now().toString());
    }
}
