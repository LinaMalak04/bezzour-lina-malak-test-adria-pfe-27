package com.adria.bank.transaction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bank_transaction")
@Getter
@NoArgsConstructor
public class Transaction {
    @Id
    private UUID id;
    private UUID accountId;
    @Column(precision = 19, scale = 4)
    private BigDecimal amount;
    private String currency;
    private String merchantCategory;
    private String country;
    private Instant createdAt;
    // Beyond the minimal model : direction, lifecycle and link between the two legs of a transfer
    @Enumerated(EnumType.STRING)
    private TransactionType type;
    @Enumerated(EnumType.STRING)
    private TransactionStatus status;
    private UUID transferId;
    private String failureReason;

    public Transaction(UUID accountId, BigDecimal amount, String currency, String merchantCategory, String country,
                       TransactionType type, UUID transferId) {
        this.id = UUID.randomUUID();
        this.accountId = accountId;
        this.amount = amount;
        this.currency = currency;
        this.merchantCategory = merchantCategory;
        this.country = country;
        this.createdAt = Instant.now();
        this.type = type;
        this.status = TransactionStatus.PENDING;
        this.transferId = transferId;
    }

    public void complete() {
        this.status = TransactionStatus.COMPLETED;
    }

    public void reject(String reason) {
        this.status = TransactionStatus.REJECTED;
        this.failureReason = reason;
    }
}
