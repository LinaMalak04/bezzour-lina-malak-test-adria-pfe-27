package com.adria.bank.account.service;

import com.adria.bank.account.domain.Account;
import com.adria.bank.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DemoDataLoader implements CommandLineRunner {
    private final AccountRepository accountRepository;

    @Override
    public void run(String... args) {
        if (accountRepository.count() > 0) {
            return;
        }
        accountRepository.saveAll(List.of(
                new Account(UUID.fromString("11111111-1111-1111-1111-111111111111"), "CUST-001", "EUR", 5000),
                new Account(UUID.fromString("22222222-2222-2222-2222-222222222222"), "CUST-002", "EUR", 1200),
                new Account(UUID.fromString("33333333-3333-3333-3333-333333333333"), "CUST-003", "MAD", 20000)));
    }
}
