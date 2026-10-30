package com.adria.bank.account.web.graphql;

import com.adria.bank.account.domain.AccountNotFoundException;
import com.adria.bank.account.domain.BusinessRuleException;
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
        if (e instanceof AccountNotFoundException) {
            return GraphqlErrorBuilder.newError(env).errorType(ErrorType.NOT_FOUND).message(e.getMessage()).build();
        }
        if (e instanceof BusinessRuleException) {
            return GraphqlErrorBuilder.newError(env).errorType(ErrorType.BAD_REQUEST).message(e.getMessage()).build();
        }
        return null;
    }
}
