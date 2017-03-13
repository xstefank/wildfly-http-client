/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
