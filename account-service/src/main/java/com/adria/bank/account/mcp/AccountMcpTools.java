package com.adria.bank.account.mcp;

import com.adria.bank.account.domain.Account;
import com.adria.bank.account.domain.AccountStatus;
import com.adria.bank.account.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AccountMcpTools {
    private final AccountService accountService;

    @Tool(name = "listAccounts", description = "Returns all the bank accounts with their status, currency and balance")
    public List<Account> listAccounts() {
        return accountService.listAccounts();
    }

    @Tool(name = "getAccount", description = "Returns one bank account (status, currency, balance) from its id")
    public Account getAccount(@ToolParam(description = "The account id (UUID)") String accountId) {
        return accountService.getAccount(UUID.fromString(accountId));
    }

    @Tool(name = "createAccount", description = "Creates a new active bank account")
    public Account createAccount(@ToolParam(description = "The customer reference, for example CUST-010") String customerReference,
                                 @ToolParam(description = "The ISO currency code, for example EUR") String currency,
                                 @ToolParam(description = "The initial balance, zero or more") double initialBalance) {
        return accountService.createAccount(customerReference, currency, initialBalance);
    }

    @Tool(name = "changeAccountStatus", description = "Blocks or reactivates a bank account")
    public Account changeAccountStatus(@ToolParam(description = "The account id (UUID)") String accountId,
                                       @ToolParam(description = "The new status : ACTIVE or BLOCKED") AccountStatus status) {
        return accountService.changeStatus(UUID.fromString(accountId), status);
    }
}
