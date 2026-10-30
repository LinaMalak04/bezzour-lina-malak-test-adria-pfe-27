package com.adria.bank.transaction.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.util.UUID;

@FeignClient(name = "account-service")
public interface AccountRestClient {

    record AccountDto(UUID id, String customerReference, String status, String currency, double balance) {
    }

    record BalanceOperationRequest(UUID operationId, String type, BigDecimal amount, String currency) {
    }

    record TransferRequest(UUID operationId, UUID sourceAccountId, UUID targetAccountId, BigDecimal amount, String currency) {
    }

    @GetMapping("/api/accounts/{id}")
    AccountDto getAccount(@PathVariable UUID id);

    @PostMapping("/internal/accounts/{id}/balance-operations")
    AccountDto applyBalanceOperation(@PathVariable UUID id, @RequestBody BalanceOperationRequest request);

    @PostMapping("/internal/transfers")
    void transfer(@RequestBody TransferRequest request);
}
