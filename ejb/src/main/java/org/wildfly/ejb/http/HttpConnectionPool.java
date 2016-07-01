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
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A pool of HTTP connections for a given host pool.
 *
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
    private final HostPool hostPool;

    private final ConcurrentLinkedDeque<ClientConnection> connections = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<RequestHolder> pendingConnectionRequests = new ConcurrentLinkedDeque<>();
    private final AtomicInteger currentConnectionCount = new AtomicInteger();


    public HttpConnectionPool(int maxConnections, int maxRequestsPerConnection, XnioWorker worker, ByteBufferPool byteBufferPool, XnioSsl ssl, OptionMap options, HostPool hostPool) {
        this.maxConnections = maxConnections;
        this.maxRequestsPerConnection = maxRequestsPerConnection;
        this.worker = worker;
        this.byteBufferPool = byteBufferPool;
        this.ssl = ssl;
        this.options = options;
        this.hostPool = hostPool;
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
        HostPool.AddressResult holder = hostPool.getAddress();
        InetAddress address;
        try {
            address = holder.getAddress();
        } catch (UnknownHostException e) {
            next.errorListener.error(e);
            return;
        }
        try {
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
                    holder.failed(); //notify the host pool that this host has failed
                    currentConnectionCount.decrementAndGet();
                    next.errorListener.error(e);
                }
            }, new URI(holder.getURI().getScheme(), holder.getURI().getUserInfo(), address.getHostAddress(), holder.getURI().getPort(), "/", null, null), worker, ssl, byteBufferPool, options);
        } catch (URISyntaxException e) {
            next.errorListener.error(e);
        }


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
