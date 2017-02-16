/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 2110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.wildfly.httpclient.ejb;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import org.jboss.marshalling.Unmarshaller;
import org.wildfly.transaction.client.SimpleXid;

import javax.transaction.xa.Xid;
import java.io.IOException;
import java.util.concurrent.ExecutorService;

/**
 * Undertow HTTP handler that is responsible for initial parsing of EJB over HTTP messages
 *
 * @author Stuart Douglas
 */
public abstract class RemoteHTTPHandler implements HttpHandler {

    private final ExecutorService executorService;

    private static final AttachmentKey<ExecutorService> EXECUTOR = AttachmentKey.create(ExecutorService.class);

    public RemoteHTTPHandler(ExecutorService executorService) {
        this.executorService = executorService;
    }

    @Override
    public final void handleRequest(HttpServerExchange exchange) throws Exception {
        if (exchange.isInIoThread()) {
            if (executorService == null) {
                exchange.dispatch(this);
            } else {
                exchange.putAttachment(EXECUTOR, executorService);
                exchange.dispatch(executorService, this);
            }
            return;
        } else if (executorService != null && exchange.getAttachment(EXECUTOR) == null) {
            exchange.putAttachment(EXECUTOR, executorService);
            exchange.dispatch(executorService, this);
            return;
        }
        exchange.startBlocking();
        handleInternal(exchange);
    }

    protected abstract void handleInternal(HttpServerExchange exchange) throws Exception;

    protected ReceivedTransaction readTransaction(Unmarshaller unmarshaller) throws IOException {
        int type = unmarshaller.readByte();
        if (type == 0) {
            return null;
        } else if (type == 1 || type == 2) {
            int formatId = unmarshaller.readInt();
            int len = unmarshaller.readInt();
            byte[] globalId = new byte[len];
            unmarshaller.readFully(globalId);
            len = unmarshaller.readInt();
            byte[] branchId = new byte[len];
            unmarshaller.readFully(branchId);
            SimpleXid simpleXid = new SimpleXid(formatId, globalId, branchId);
            if (type == 2) {
                return new ReceivedTransaction(simpleXid, unmarshaller.readInt(), true);
            }
            return new ReceivedTransaction(simpleXid, 0, false);
        } else {
            throw EjbHttpClientMessages.MESSAGES.invalidTransactionType(type);
        }
    }

    static class ReceivedTransaction {
        final Xid xid;
        final int remainingTime;
        final boolean outflowed;

        ReceivedTransaction(Xid xid, int remainingTime, boolean outflowed) {
            this.xid = xid;
            this.remainingTime = remainingTime;
            this.outflowed = outflowed;
        }

        public Xid getXid() {
            return xid;
        }

        public int getRemainingTime() {
            return remainingTime;
        }

        public boolean isOutflowed() {
            return outflowed;
        }
    }
}
