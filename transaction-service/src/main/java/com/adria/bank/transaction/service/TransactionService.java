package com.adria.bank.transaction.service;

import com.adria.bank.transaction.client.AccountGateway;
import com.adria.bank.transaction.client.AccountRestClient.AccountDto;
import com.adria.bank.transaction.config.TransactionProperties;
import com.adria.bank.transaction.domain.AccountServiceUnavailableException;
import com.adria.bank.transaction.domain.BusinessRuleException;
import com.adria.bank.transaction.domain.Transaction;
import com.adria.bank.transaction.domain.TransactionNotFoundException;
import com.adria.bank.transaction.domain.TransactionType;
import com.adria.bank.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// Single entry point used by the REST, GraphQL and MCP connectors
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {
    private static final String TRANSFER_CATEGORY = "TRANSFER";
    private static final String TRANSFER_COUNTRY = "N/A";

    private final TransactionRepository transactionRepository;
    private final TransactionStore transactionStore;
    private final AccountGateway accountGateway;
    private final TransactionProperties properties;

    public Transaction getTransaction(UUID id) {
        return transactionRepository.findById(id).orElseThrow(() -> new TransactionNotFoundException(id));
    }

    public List<Transaction> transactionsByAccount(UUID accountId) {
        return transactionRepository.findByAccountIdOrderByCreatedAtDesc(accountId);
    }

    public Transaction createTransaction(UUID accountId, BigDecimal amount, String currency,
                                         String merchantCategory, String country) {
        checkAmount(amount);
        AccountDto account = accountGateway.getAccount(accountId);
        checkOperable(account, currency);
        checkBalance(account, amount);

        Transaction pending = new Transaction(accountId, amount, currency.toUpperCase(), merchantCategory, country,
                TransactionType.DEBIT, null);
        transactionStore.savePending(List.of(pending));
        return settle(List.of(pending), () -> accountGateway.debit(pending.getId(), accountId, amount, pending.getCurrency())).get(0);
    }

    public List<Transaction> transfer(UUID sourceId, UUID targetId, BigDecimal amount, String currency) {
        checkAmount(amount);
        if (sourceId.equals(targetId)) {
            throw new BusinessRuleException("SAME_ACCOUNT", "Source and target accounts must be different");
        }
        AccountDto source = accountGateway.getAccount(sourceId);
        AccountDto target = accountGateway.getAccount(targetId);
        checkOperable(source, currency);
        checkOperable(target, currency);
        checkBalance(source, amount);

        UUID transferId = UUID.randomUUID();
        String normalizedCurrency = currency.toUpperCase();
        List<Transaction> legs = List.of(
                new Transaction(sourceId, amount, normalizedCurrency, TRANSFER_CATEGORY, TRANSFER_COUNTRY, TransactionType.DEBIT, transferId),
                new Transaction(targetId, amount, normalizedCurrency, TRANSFER_CATEGORY, TRANSFER_COUNTRY, TransactionType.CREDIT, transferId));
        transactionStore.savePending(legs);
        return settle(legs, () -> accountGateway.transfer(transferId, sourceId, targetId, amount, normalizedCurrency));
    }

    // Outcome of the balance update : success -> COMPLETED + event, refusal -> REJECTED,
    // outage -> stays PENDING because the update may or may not have been applied ; the reconciler finishes the job
    List<Transaction> settle(List<Transaction> transactions, Runnable balanceUpdate) {
        List<UUID> ids = transactions.stream().map(Transaction::getId).toList();
        try {
            balanceUpdate.run();
            return transactionStore.complete(ids);
        } catch (BusinessRuleException e) {
            transactionStore.reject(ids, e.getCode());
            throw e;
        } catch (AccountServiceUnavailableException e) {
            log.warn("Balance update postponed for transactions {}", ids);
            return transactions;
        }
    }

    private void checkAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException("INVALID_AMOUNT", "The amount must be strictly positive");
        }
        // Otherwise the stored amount could be rounded and differ from what is debited
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new BusinessRuleException("INVALID_AMOUNT", "The amount cannot have more than 2 decimals");
        }
        if (amount.compareTo(properties.getMaxAmount()) > 0) {
            throw new BusinessRuleException("AMOUNT_ABOVE_LIMIT", "The amount exceeds the ceiling of " + properties.getMaxAmount());
        }
    }

    private void checkOperable(AccountDto account, String currency) {
        if (!"ACTIVE".equals(account.status())) {
            throw new BusinessRuleException("ACCOUNT_NOT_ACTIVE", "Account " + account.id() + " is not active");
        }
        if (!account.currency().equalsIgnoreCase(currency)) {
            throw new BusinessRuleException("CURRENCY_MISMATCH", "Account " + account.id() + " is in " + account.currency());
        }
    }

    private void checkBalance(AccountDto account, BigDecimal amount) {
        if (BigDecimal.valueOf(account.balance()).compareTo(amount) < 0) {
            throw new BusinessRuleException("INSUFFICIENT_BALANCE", "Insufficient balance on account " + account.id());
        }
    }
}
