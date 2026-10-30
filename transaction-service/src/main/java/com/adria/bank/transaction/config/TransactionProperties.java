package com.adria.bank.transaction.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

// A JavaBean (not a record) : /actuator/refresh rebinds it, so the ceiling changes without a restart
@ConfigurationProperties("bank.transaction")
@Validated
@Getter
@Setter
public class TransactionProperties {
    @NotNull
    @Positive
    private BigDecimal maxAmount;
}
