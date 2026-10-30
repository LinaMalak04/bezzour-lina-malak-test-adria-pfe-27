package com.adria.bank.account.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

// Remembers the balance operations already applied, so a retried call never moves the money twice
@Entity
@Getter
@NoArgsConstructor
public class AppliedOperation {
    @Id
    private UUID operationId;
    private Instant appliedAt;

    public AppliedOperation(UUID operationId) {
        this.operationId = operationId;
        this.appliedAt = Instant.now();
    }
}
