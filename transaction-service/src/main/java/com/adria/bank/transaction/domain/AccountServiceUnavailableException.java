package com.adria.bank.transaction.domain;

public class AccountServiceUnavailableException extends RuntimeException {
    public AccountServiceUnavailableException(Throwable cause) {
        super("account-service is unavailable", cause);
    }
}
