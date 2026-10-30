package com.adria.bank.account.web.rest;

import com.adria.bank.account.domain.Account;
import com.adria.bank.account.domain.AccountStatus;
import com.adria.bank.account.service.AccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {
    private final AccountService accountService;

    public record CreateAccountRequest(@NotBlank String customerReference,
                                       @NotBlank String currency,
                                       @PositiveOrZero double initialBalance) {
    }

    public record ChangeStatusRequest(@NotNull AccountStatus status) {
    }

    @GetMapping
    public List<Account> accounts() {
        return accountService.listAccounts();
    }

    @GetMapping("/{id}")
    public Account account(@PathVariable UUID id) {
        return accountService.getAccount(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Account create(@Valid @RequestBody CreateAccountRequest request) {
        return accountService.createAccount(request.customerReference(), request.currency(), request.initialBalance());
    }

    @PatchMapping("/{id}/status")
    public Account changeStatus(@PathVariable UUID id, @Valid @RequestBody ChangeStatusRequest request) {
        return accountService.changeStatus(id, request.status());
    }
}
