package com.adria.bank.transaction.client;

import com.adria.bank.transaction.domain.AccountNotFoundException;
import com.adria.bank.transaction.domain.BusinessRuleException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

// Separates the business refusals of account-service from its technical failures
@Component
public class AccountErrorDecoder implements ErrorDecoder {
    private final ErrorDecoder defaultDecoder = new Default();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Exception decode(String methodKey, Response response) {
        if (response.status() == 404) {
            return new AccountNotFoundException("Account not found");
        }
        if (response.status() == 422) {
            JsonNode body = readBody(response);
            return new BusinessRuleException(body.path("code").asText("BUSINESS_RULE"), body.path("detail").asText("Operation refused"));
        }
        return defaultDecoder.decode(methodKey, response);
    }

    private JsonNode readBody(Response response) {
        if (response.body() == null) {
            return objectMapper.createObjectNode();
        }
        try (InputStream body = response.body().asInputStream()) {
            return objectMapper.readTree(body);
        } catch (IOException e) {
            return objectMapper.createObjectNode();
        }
    }
}
