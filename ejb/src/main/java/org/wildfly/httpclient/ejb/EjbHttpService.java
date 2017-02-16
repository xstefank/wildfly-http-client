package org.wildfly.httpclient.ejb;

import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.AllowedMethodsHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.util.Methods;
import org.jboss.ejb.server.Association;
import org.wildfly.transaction.client.LocalTransactionContext;

import java.util.concurrent.ExecutorService;

/**
 * @author Stuart Douglas
 */
public class EjbHttpService {

    static final String JSESSIONID = "JSESSIONID";

    private final Association association;
    private final ExecutorService executorService;
    private final LocalTransactionContext localTransactionContext;

    public EjbHttpService(Association association, ExecutorService executorService, LocalTransactionContext localTransactionContext) {
        this.association = association;
        this.executorService = executorService;
        this.localTransactionContext = localTransactionContext;
    }

    public HttpHandler createHttpHandler() {
        PathHandler pathHandler = new PathHandler();
        pathHandler.addPrefixPath("/v1/invoke", new HttpInvocationHandler(association, executorService, localTransactionContext))
                .addPrefixPath("/v1/open", new HttpSessionOpenHandler(association, executorService, localTransactionContext));
        return new AllowedMethodsHandler(pathHandler, Methods.POST);
    }

}
