package com.adria.bank.transaction.client;

import com.adria.bank.transaction.client.AccountRestClient.AccountDto;
import com.adria.bank.transaction.client.AccountRestClient.BalanceOperationRequest;
import com.adria.bank.transaction.client.AccountRestClient.TransferRequest;
import com.adria.bank.transaction.domain.AccountServiceUnavailableException;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Fault tolerance around account-service. The retries are safe because the balance operations are idempotent.
 * The fallbacks fail closed on purpose : inventing a default account or balance would let a bank
 * accept an operation it cannot verify. Business exceptions match no fallback and go through unchanged.
 */
@Component
@RequiredArgsConstructor
public class AccountGateway {
    private static final String ACCOUNT_SERVICE = "accountService";

    private final AccountRestClient accountRestClient;

    @Retry(name = ACCOUNT_SERVICE, fallbackMethod = "unavailableAccount")
    @CircuitBreaker(name = ACCOUNT_SERVICE)
    public AccountDto getAccount(UUID id) {
        return accountRestClient.getAccount(id);
    }

    @Retry(name = ACCOUNT_SERVICE, fallbackMethod = "unavailableOperation")
    @CircuitBreaker(name = ACCOUNT_SERVICE)
    public void debit(UUID operationId, UUID accountId, BigDecimal amount, String currency) {
        accountRestClient.applyBalanceOperation(accountId, new BalanceOperationRequest(operationId, "DEBIT", amount, currency));
    }

    @Retry(name = ACCOUNT_SERVICE, fallbackMethod = "unavailableTransfer")
    @CircuitBreaker(name = ACCOUNT_SERVICE)
    public void transfer(UUID operationId, UUID sourceId, UUID targetId, BigDecimal amount, String currency) {
        accountRestClient.transfer(new TransferRequest(operationId, sourceId, targetId, amount, currency));
    }

    private AccountDto unavailableAccount(UUID id, FeignException e) {
        throw new AccountServiceUnavailableException(e);
    }

    private AccountDto unavailableAccount(UUID id, CallNotPermittedException e) {
        throw new AccountServiceUnavailableException(e);
    }

    private void unavailableOperation(UUID operationId, UUID accountId, BigDecimal amount, String currency, FeignException e) {
        throw new AccountServiceUnavailableException(e);
    }

    private void unavailableOperation(UUID operationId, UUID accountId, BigDecimal amount, String currency, CallNotPermittedException e) {
        throw new AccountServiceUnavailableException(e);
    }

    private void unavailableTransfer(UUID operationId, UUID sourceId, UUID targetId, BigDecimal amount, String currency, FeignException e) {
        throw new AccountServiceUnavailableException(e);
    }

    private void unavailableTransfer(UUID operationId, UUID sourceId, UUID targetId, BigDecimal amount, String currency, CallNotPermittedException e) {
        throw new AccountServiceUnavailableException(e);
    }
}
