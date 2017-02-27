package org.wildfly.httpclient.ejb;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import org.jboss.ejb.server.Association;
import org.jboss.ejb.server.CancelHandle;
import org.wildfly.transaction.client.LocalTransactionContext;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.AllowedMethodsHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.util.Methods;

/**
 * @author Stuart Douglas
 */
public class EjbHttpService {

    static final String JSESSIONID = "JSESSIONID";

    private final Association association;
    private final ExecutorService executorService;
    private final LocalTransactionContext localTransactionContext;

    private final Map<InvocationIdentifier, CancelHandle> cancellationFlags = new ConcurrentHashMap<>();

    public EjbHttpService(Association association, ExecutorService executorService, LocalTransactionContext localTransactionContext) {
        this.association = association;
        this.executorService = executorService;
        this.localTransactionContext = localTransactionContext;
    }

    public HttpHandler createHttpHandler() {
        PathHandler pathHandler = new PathHandler();
        pathHandler.addPrefixPath("/v1/invoke", new AllowedMethodsHandler(new HttpInvocationHandler(association, executorService, localTransactionContext, cancellationFlags), Methods.POST))
                .addPrefixPath("/v1/open", new AllowedMethodsHandler(new HttpSessionOpenHandler(association, executorService, localTransactionContext), Methods.POST))
                .addPrefixPath("/v1/cancel", new AllowedMethodsHandler(new HttpCancelHandler(association, executorService, localTransactionContext, cancellationFlags), Methods.DELETE));
        return pathHandler;
    }

}
