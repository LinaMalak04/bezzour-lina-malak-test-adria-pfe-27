package com.adria.bank.account.service;

import com.adria.bank.account.domain.Account;
import com.adria.bank.account.domain.AccountNotFoundException;
import com.adria.bank.account.domain.AccountStatus;
import com.adria.bank.account.domain.AppliedOperation;
import com.adria.bank.account.domain.BusinessRuleException;
import com.adria.bank.account.repository.AccountRepository;
import com.adria.bank.account.repository.AppliedOperationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// Single entry point used by the REST, GraphQL and MCP connectors
@Service
@RequiredArgsConstructor
public class AccountService {
    public enum BalanceOperationType { DEBIT, CREDIT }

    private final AccountRepository accountRepository;
    private final AppliedOperationRepository appliedOperationRepository;

    @Transactional(readOnly = true)
    public List<Account> listAccounts() {
        return accountRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Account getAccount(UUID id) {
        return accountRepository.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }

    @Transactional
    public Account createAccount(String customerReference, String currency, double initialBalance) {
        if (initialBalance < 0) {
            throw new BusinessRuleException("INVALID_AMOUNT", "The initial balance cannot be negative");
        }
        return accountRepository.save(new Account(UUID.randomUUID(), customerReference, currency.toUpperCase(), initialBalance));
    }

    @Transactional
    public Account changeStatus(UUID id, AccountStatus status) {
        Account account = lock(id);
        account.changeStatus(status);
        return account;
    }

    // The account row is locked first, so two calls with the same operationId cannot both pass the check
    @Transactional
    public Account applyBalanceOperation(UUID operationId, UUID accountId, BalanceOperationType type,
                                         BigDecimal amount, String currency) {
        Account account = lock(accountId);
        if (appliedOperationRepository.existsById(operationId)) {
            return account;
        }
        if (type == BalanceOperationType.DEBIT) {
            account.debit(amount, currency);
        } else {
            account.credit(amount, currency);
        }
        appliedOperationRepository.save(new AppliedOperation(operationId));
        return account;
    }

    // Both accounts live in this database : a local ACID transaction is simpler and safer than a distributed saga
    @Transactional
    public void transfer(UUID operationId, UUID sourceId, UUID targetId, BigDecimal amount, String currency) {
        if (sourceId.equals(targetId)) {
            throw new BusinessRuleException("SAME_ACCOUNT", "Source and target accounts must be different");
        }
        // Always locking in the same order prevents a deadlock between A->B and B->A
        Account first = lock(sourceId.compareTo(targetId) < 0 ? sourceId : targetId);
        Account second = lock(sourceId.compareTo(targetId) < 0 ? targetId : sourceId);
        if (appliedOperationRepository.existsById(operationId)) {
            return;
        }
        Account source = first.getId().equals(sourceId) ? first : second;
        Account target = first.getId().equals(targetId) ? first : second;
        source.debit(amount, currency);
        target.credit(amount, currency);
        appliedOperationRepository.save(new AppliedOperation(operationId));
    }

    private Account lock(UUID id) {
        return accountRepository.findByIdForUpdate(id).orElseThrow(() -> new AccountNotFoundException(id));
    }
}
