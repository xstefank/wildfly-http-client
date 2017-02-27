package org.wildfly.httpclient.ejb;

import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.jboss.ejb.server.Association;
import org.jboss.ejb.server.CancelHandle;
import org.wildfly.httpclient.common.ContentType;
import org.wildfly.transaction.client.LocalTransactionContext;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;

/**
 * @author Stuart Douglas
 */
class HttpCancelHandler extends RemoteHTTPHandler {

    private final Association association;
    private final ExecutorService executorService;
    private final LocalTransactionContext localTransactionContext;
    private final Map<InvocationIdentifier, CancelHandle> cancellationFlags;

    HttpCancelHandler(Association association, ExecutorService executorService, LocalTransactionContext localTransactionContext, Map<InvocationIdentifier, CancelHandle> cancellationFlags) {
        super(executorService);
        this.association = association;
        this.executorService = executorService;
        this.localTransactionContext = localTransactionContext;
        this.cancellationFlags = cancellationFlags;
    }

    @Override
    protected void handleInternal(HttpServerExchange exchange) throws Exception {
        String ct = exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE);
        ContentType contentType = ContentType.parse(ct);
        if (contentType != null) {
            exchange.setStatusCode(StatusCodes.BAD_REQUEST);
            EjbHttpClientMessages.MESSAGES.debugf("Bad content type %s", ct);
            return;
        }

        String relativePath = exchange.getRelativePath();
        if (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }
        String[] parts = relativePath.split("/");
        if (parts.length != 6) {
            exchange.setStatusCode(StatusCodes.NOT_FOUND);
            return;
        }
        final String app = handleDash(parts[0]);
        final String module = handleDash(parts[1]);
        final String distinct = handleDash(parts[2]);
        final String bean = parts[3];
        String invocationId = parts[4];
        boolean cancelIdRunning = Boolean.parseBoolean(parts[5]);
        Cookie cookie = exchange.getRequestCookies().get(EjbHttpService.JSESSIONID);
        final String sessionAffinity = cookie != null ? cookie.getValue() : null;
        final InvocationIdentifier identifier;
        if (invocationId != null && sessionAffinity != null) {
            identifier = new InvocationIdentifier(invocationId, sessionAffinity);
        } else {
            exchange.setStatusCode(StatusCodes.BAD_REQUEST);
            EjbHttpClientMessages.MESSAGES.debugf("Exchange %s did not include both session id and invocation id in cancel request", exchange);
            return;
        }
        CancelHandle handle = cancellationFlags.remove(identifier);
        if (handle != null) {
            handle.cancel(cancelIdRunning);
        }
    }

    private static String handleDash(String s) {
        if (s.equals("-")) {
            return "";
        }
        return s;
    }

}
