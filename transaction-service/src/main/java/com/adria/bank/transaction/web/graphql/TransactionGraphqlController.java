package com.adria.bank.transaction.web.graphql;

import com.adria.bank.transaction.domain.Transaction;
import com.adria.bank.transaction.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class TransactionGraphqlController {
    private final TransactionService transactionService;

    public record CreateTransactionInput(UUID accountId, BigDecimal amount, String currency,
                                         String merchantCategory, String country) {
    }

    public record TransferInput(UUID sourceAccountId, UUID targetAccountId, BigDecimal amount, String currency) {
    }

    @QueryMapping
    public Transaction transaction(@Argument UUID id) {
        return transactionService.getTransaction(id);
    }

    @QueryMapping
    public List<Transaction> transactionsByAccount(@Argument UUID accountId) {
        return transactionService.transactionsByAccount(accountId);
    }

    @MutationMapping
    public Transaction createTransaction(@Argument CreateTransactionInput input) {
        return transactionService.createTransaction(input.accountId(), input.amount(), input.currency(),
                input.merchantCategory(), input.country());
    }

    @MutationMapping
    public List<Transaction> transfer(@Argument TransferInput input) {
        return transactionService.transfer(input.sourceAccountId(), input.targetAccountId(), input.amount(), input.currency());
    }
}
