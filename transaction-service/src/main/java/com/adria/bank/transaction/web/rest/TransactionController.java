package com.adria.bank.transaction.web.rest;

import com.adria.bank.transaction.domain.Transaction;
import com.adria.bank.transaction.domain.TransactionStatus;
import com.adria.bank.transaction.service.TransactionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {
    private final TransactionService transactionService;

    public record CreateTransactionRequest(@NotNull UUID accountId, @NotNull @Positive BigDecimal amount,
                                           @NotBlank String currency, @NotBlank String merchantCategory,
                                           @NotBlank String country) {
    }

    public record TransferRequest(@NotNull UUID sourceAccountId, @NotNull UUID targetAccountId,
                                  @NotNull @Positive BigDecimal amount, @NotBlank String currency) {
    }

    @GetMapping
    public List<Transaction> byAccount(@RequestParam UUID accountId) {
        return transactionService.transactionsByAccount(accountId);
    }

    @GetMapping("/{id}")
    public Transaction transaction(@PathVariable UUID id) {
        return transactionService.getTransaction(id);
    }

    @PostMapping
    public ResponseEntity<Transaction> create(@Valid @RequestBody CreateTransactionRequest request) {
        Transaction transaction = transactionService.createTransaction(request.accountId(), request.amount(),
                request.currency(), request.merchantCategory(), request.country());
        return ResponseEntity.status(statusOf(transaction)).body(transaction);
    }

    @PostMapping("/transfers")
    public ResponseEntity<List<Transaction>> transfer(@Valid @RequestBody TransferRequest request) {
        List<Transaction> legs = transactionService.transfer(request.sourceAccountId(), request.targetAccountId(),
                request.amount(), request.currency());
        return ResponseEntity.status(statusOf(legs.get(0))).body(legs);
    }

    // 202 : recorded, but the balance update will only be finished by the reconciler
    private HttpStatus statusOf(Transaction transaction) {
        return transaction.getStatus() == TransactionStatus.PENDING ? HttpStatus.ACCEPTED : HttpStatus.CREATED;
    }
}
