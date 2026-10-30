package com.adria.bank.transaction.web.graphql;

import com.adria.bank.transaction.domain.AccountNotFoundException;
import com.adria.bank.transaction.domain.AccountServiceUnavailableException;
import com.adria.bank.transaction.domain.BusinessRuleException;
import com.adria.bank.transaction.domain.TransactionNotFoundException;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;

@Component
public class GraphqlExceptionResolver extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(Throwable e, DataFetchingEnvironment env) {
        if (e instanceof AccountNotFoundException || e instanceof TransactionNotFoundException) {
            return GraphqlErrorBuilder.newError(env).errorType(ErrorType.NOT_FOUND).message(e.getMessage()).build();
        }
        if (e instanceof BusinessRuleException) {
            return GraphqlErrorBuilder.newError(env).errorType(ErrorType.BAD_REQUEST).message(e.getMessage()).build();
        }
        if (e instanceof AccountServiceUnavailableException) {
            return GraphqlErrorBuilder.newError(env).errorType(ErrorType.INTERNAL_ERROR).message(e.getMessage()).build();
        }
        return null;
    }
}
