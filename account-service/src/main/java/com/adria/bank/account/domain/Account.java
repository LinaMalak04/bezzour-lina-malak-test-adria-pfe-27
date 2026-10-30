package com.adria.bank.account.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor
public class Account {
    @Id
    private UUID id;
    private String customerReference;
    @Enumerated(EnumType.STRING)
    private AccountStatus status;
    private String currency;
    // double is imposed by the specification : arithmetic goes through BigDecimal to avoid cumulating rounding errors
    private double balance;

    public Account(UUID id, String customerReference, String currency, double balance) {
        this.id = id;
        this.customerReference = customerReference;
        this.status = AccountStatus.ACTIVE;
        this.currency = currency;
        this.balance = balance;
    }

    public void changeStatus(AccountStatus status) {
        this.status = status;
    }

    public void debit(BigDecimal amount, String operationCurrency) {
        checkOperable(amount, operationCurrency);
        BigDecimal current = BigDecimal.valueOf(balance);
        if (current.compareTo(amount) < 0) {
            throw new BusinessRuleException("INSUFFICIENT_BALANCE", "Insufficient balance on account " + id);
        }
        balance = current.subtract(amount).doubleValue();
    }

    public void credit(BigDecimal amount, String operationCurrency) {
        checkOperable(amount, operationCurrency);
        balance = BigDecimal.valueOf(balance).add(amount).doubleValue();
    }

    private void checkOperable(BigDecimal amount, String operationCurrency) {
        if (amount.signum() <= 0) {
            throw new BusinessRuleException("INVALID_AMOUNT", "The amount must be strictly positive");
        }
        if (status != AccountStatus.ACTIVE) {
            throw new BusinessRuleException("ACCOUNT_NOT_ACTIVE", "Account " + id + " is not active");
        }
        if (!currency.equalsIgnoreCase(operationCurrency)) {
            throw new BusinessRuleException("CURRENCY_MISMATCH", "Account " + id + " is in " + currency);
        }
    }
}
