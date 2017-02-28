package org.wildfly.httpclient.common;

import java.security.PrivilegedExceptionAction;

import org.wildfly.security.auth.server.SecurityIdentity;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;

/**
 * @author Stuart Douglas
 */
public class ElytronIdentityHandler implements HttpHandler {
    /**
     * The current security identity
     */
    public static final AttachmentKey<SecurityIdentity> IDENTITY_KEY = AttachmentKey.create(SecurityIdentity.class);

    private final HttpHandler next;

    public ElytronIdentityHandler(HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        SecurityIdentity securityIdentity = exchange.getAttachment(IDENTITY_KEY);
        if(securityIdentity == null) {
            next.handleRequest(exchange);
        } else {
            securityIdentity.runAs((PrivilegedExceptionAction<Object>) () -> {
                next.handleRequest(exchange);
                return null;
            });
        }
    }
}
