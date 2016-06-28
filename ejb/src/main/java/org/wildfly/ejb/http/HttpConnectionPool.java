package org.wildfly.ejb.http;

import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.UndertowClient;
import io.undertow.connector.ByteBufferPool;
import org.xnio.ChannelListener;
import org.xnio.OptionMap;
import org.xnio.XnioWorker;
import org.xnio.ssl.XnioSsl;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A pool of HTTP connections.
 *
 * TODO: this currently sucks
 *
 * @author Stuart Douglas
 */
class HttpConnectionPool implements Closeable {

    private final int maxConnections;
    private final int maxRequestsPerConnection;
    private final XnioWorker worker;
    private final ByteBufferPool byteBufferPool;
    private final XnioSsl ssl;
    private final OptionMap options;
    private final URI uri;

    private final ConcurrentLinkedDeque<ClientConnection> connections = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<RequestHolder> pendingConnectionRequests = new ConcurrentLinkedDeque<>();
    private final AtomicInteger currentConnectionCount = new AtomicInteger();


    public HttpConnectionPool(int maxConnections, int maxRequestsPerConnection, XnioWorker worker, ByteBufferPool byteBufferPool, XnioSsl ssl, OptionMap options, URI uri) {
        this.maxConnections = maxConnections;
        this.maxRequestsPerConnection = maxRequestsPerConnection;
        this.worker = worker;
        this.byteBufferPool = byteBufferPool;
        this.ssl = ssl;
        this.options = options;
        this.uri = uri;
    }

    public void getConnection(ConnectionListener connectionListener, ErrorListener errorListener, boolean ignoreConnectionLimits) throws IOException {
        pendingConnectionRequests.add(new RequestHolder(connectionListener, errorListener, ignoreConnectionLimits));
        runPending();
    }

    public void returnConnection(ClientConnection connection) {
        currentConnectionCount.decrementAndGet();
        if(connection.isOpen()) {
            connections.add(connection);
        }
        runPending();
    }

    private void runPending() {
        int count;
        do {
            count = currentConnectionCount.get();
            if(count == maxConnections) {
                return;
            }
        } while (!currentConnectionCount.compareAndSet(count, count + 1));
        RequestHolder next = pendingConnectionRequests.poll();
        if(next == null) {
            currentConnectionCount.decrementAndGet();
            return;
        }
        ClientConnection existingConnection = connections.poll();
        if(existingConnection != null) {
            next.connectionListener.done(existingConnection);
            return;
        }
        UndertowClient.getInstance().connect(new ClientCallback<ClientConnection>() {
            @Override
            public void completed(ClientConnection result) {
                result.getCloseSetter().set(new ChannelListener<ClientConnection>() {
                    @Override
                    public void handleEvent(ClientConnection channel) {
                        connections.remove(channel);
                    }
                });
                next.connectionListener.done(result);
            }

            @Override
            public void failed(IOException e) {
                currentConnectionCount.decrementAndGet();
                next.errorListener.error(e);
            }
        }, uri, worker, ssl, byteBufferPool, options);


    }

    @Override
    public void close() throws IOException {
        //TODO
    }

    public interface ConnectionListener {

        void done(ClientConnection connection);

    }

    public interface ErrorListener {

        void error(Exception e);

    }

    private static class RequestHolder {
        final ConnectionListener connectionListener;
        final ErrorListener errorListener;
        final boolean ignoreConnectionLimits;

        private RequestHolder(ConnectionListener connectionListener, ErrorListener errorListener, boolean ignoreConnectionLimits) {
            this.connectionListener = connectionListener;
            this.errorListener = errorListener;
            this.ignoreConnectionLimits = ignoreConnectionLimits;
        }
    }

}
