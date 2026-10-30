package com.adria.bank.account.web.graphql;

import com.adria.bank.account.domain.Account;
import com.adria.bank.account.domain.AccountStatus;
import com.adria.bank.account.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class AccountGraphqlController {
    private final AccountService accountService;

    public record CreateAccountInput(String customerReference, String currency, double initialBalance) {
    }

    @QueryMapping
    public List<Account> accounts() {
        return accountService.listAccounts();
    }

    @QueryMapping
    public Account account(@Argument UUID id) {
        return accountService.getAccount(id);
    }

    @MutationMapping
    public Account createAccount(@Argument CreateAccountInput input) {
        return accountService.createAccount(input.customerReference(), input.currency(), input.initialBalance());
    }

    @MutationMapping
    public Account changeAccountStatus(@Argument UUID id, @Argument AccountStatus status) {
        return accountService.changeStatus(id, status);
    }
}
