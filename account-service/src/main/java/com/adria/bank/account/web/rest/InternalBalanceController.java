package com.adria.bank.account.web.rest;

import com.adria.bank.account.domain.Account;
import com.adria.bank.account.service.AccountService;
import com.adria.bank.account.service.AccountService.BalanceOperationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

// Reserved to transaction-service : not routed by the gateway and not exposed as MCP tools,
// otherwise a balance could be changed without going through the transaction rules
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalBalanceController {
    private final AccountService accountService;

    public record BalanceOperationRequest(@NotNull UUID operationId, @NotNull BalanceOperationType type,
                                          @NotNull @Positive BigDecimal amount, @NotBlank String currency) {
    }

    public record TransferRequest(@NotNull UUID operationId, @NotNull UUID sourceAccountId, @NotNull UUID targetAccountId,
                                  @NotNull @Positive BigDecimal amount, @NotBlank String currency) {
    }

    @PostMapping("/accounts/{id}/balance-operations")
    public Account applyBalanceOperation(@PathVariable UUID id, @Valid @RequestBody BalanceOperationRequest request) {
        return accountService.applyBalanceOperation(request.operationId(), id, request.type(), request.amount(), request.currency());
    }

    @PostMapping("/transfers")
    public void transfer(@Valid @RequestBody TransferRequest request) {
        accountService.transfer(request.operationId(), request.sourceAccountId(), request.targetAccountId(),
                request.amount(), request.currency());
    }
}
