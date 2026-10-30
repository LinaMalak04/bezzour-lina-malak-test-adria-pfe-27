package com.adria.bank.transaction.mcp;

import com.adria.bank.transaction.domain.Transaction;
import com.adria.bank.transaction.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TransactionMcpTools {
    private final TransactionService transactionService;

    @Tool(name = "getTransaction", description = "Returns one transaction from its id")
    public Transaction getTransaction(@ToolParam(description = "The transaction id (UUID)") String transactionId) {
        return transactionService.getTransaction(UUID.fromString(transactionId));
    }

    @Tool(name = "listAccountTransactions", description = "Returns the transactions of an account, most recent first")
    public List<Transaction> listAccountTransactions(@ToolParam(description = "The account id (UUID)") String accountId) {
        return transactionService.transactionsByAccount(UUID.fromString(accountId));
    }

    @Tool(name = "createTransaction", description = "Records a payment that debits an account. Refused if the amount exceeds the ceiling, "
            + "if the account is not active, if the currency differs or if the balance is insufficient")
    public Transaction createTransaction(@ToolParam(description = "The account id (UUID)") String accountId,
                                         @ToolParam(description = "The amount, strictly positive") BigDecimal amount,
                                         @ToolParam(description = "The ISO currency code of the account, for example EUR") String currency,
                                         @ToolParam(description = "The merchant category, for example GROCERY") String merchantCategory,
                                         @ToolParam(description = "The ISO country code, for example MA") String country) {
        return transactionService.createTransaction(UUID.fromString(accountId), amount, currency, merchantCategory, country);
    }

    @Tool(name = "transfer", description = "Transfers an amount from a source account to a target account in the same currency")
    public List<Transaction> transfer(@ToolParam(description = "The source account id (UUID)") String sourceAccountId,
                                      @ToolParam(description = "The target account id (UUID)") String targetAccountId,
                                      @ToolParam(description = "The amount, strictly positive") BigDecimal amount,
                                      @ToolParam(description = "The ISO currency code of both accounts") String currency) {
        return transactionService.transfer(UUID.fromString(sourceAccountId), UUID.fromString(targetAccountId), amount, currency);
    }
}
