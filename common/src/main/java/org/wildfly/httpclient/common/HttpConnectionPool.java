package org.wildfly.httpclient.common;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.XnioExecutor;
import org.xnio.XnioWorker;
import org.xnio.ssl.XnioSsl;
import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.UndertowClient;
import io.undertow.connector.ByteBufferPool;

/**
 * A pool of HTTP connections for a given host pool.
 *
 * @author Stuart Douglas
 */
public class HttpConnectionPool implements Closeable {

    private final int maxConnections;
    private final int maxStreamsPerConnection;
    private final XnioWorker worker;
    private final ByteBufferPool byteBufferPool;
    private final XnioSsl ssl;
    private final OptionMap options;
    private final HostPool hostPool;
    private final long connectionIdleTimeout;

    private final ConcurrentLinkedDeque<ClientConnectionHolder> connections = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<RequestHolder> pendingConnectionRequests = new ConcurrentLinkedDeque<>();
    private final AtomicInteger currentConnectionCount = new AtomicInteger();


    public HttpConnectionPool(int maxConnections, int maxStreamsPerConnection, XnioWorker worker, ByteBufferPool byteBufferPool, XnioSsl ssl, OptionMap options, HostPool hostPool, long connectionIdleTimeout) {
        this.maxConnections = maxConnections;
        this.maxStreamsPerConnection = maxStreamsPerConnection;
        this.worker = worker;
        this.byteBufferPool = byteBufferPool;
        this.ssl = ssl;
        this.options = options;
        this.hostPool = hostPool;
        this.connectionIdleTimeout = connectionIdleTimeout;
    }

    public void getConnection(ConnectionListener connectionListener, ErrorListener errorListener, boolean ignoreConnectionLimits) {
        pendingConnectionRequests.add(new RequestHolder(connectionListener, errorListener, ignoreConnectionLimits));
        runPending();
    }

    public void returnConnection(ClientConnectionHolder connection) {
        currentConnectionCount.decrementAndGet();
        if (connection.getConnection().isOpen()) {
            connections.add(connection);
        }
        runPending();
    }

    private void runPending() {
        int count;
        do {
            count = currentConnectionCount.get();
            if (count == maxConnections) {
                return;
            }
        } while (!currentConnectionCount.compareAndSet(count, count + 1));
        RequestHolder next = pendingConnectionRequests.poll();
        if (next == null) {
            currentConnectionCount.decrementAndGet();
            return;
        }
        for (; ; ) {
            ClientConnectionHolder existingConnection = connections.poll();
            if (existingConnection == null) {
                break;
            }
            if (existingConnection.tryAquire()) {
                next.connectionListener.done(existingConnection);
                return;
            }
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
                    ClientConnectionHolder clientConnectionHolder = new ClientConnectionHolder(result, holder.getURI());
                    clientConnectionHolder.tryAquire(); //aways suceeds
                    next.connectionListener.done(clientConnectionHolder);
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

        void done(ConnectionHandle connection);

    }

    public interface ErrorListener {

        void error(Exception e);

    }

    public interface ConnectionHandle {
        ClientConnection getConnection();

        void done(boolean close);

        URI getUri();
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

    private class ClientConnectionHolder implements ConnectionHandle {

        //0 = idle
        //1 = in use
        //2 - closed
        private volatile AtomicInteger state = new AtomicInteger();
        private final ClientConnection connection;
        private final URI uri;
        private volatile XnioExecutor.Key timeoutKey;
        private long timeout;

        private final Runnable timeoutTask = new Runnable() {
            @Override
            public void run() {
                timeoutKey = null;
                if (state.get() == 2) {
                    return;
                }
                long time = System.currentTimeMillis();
                if (timeout > time) {
                    timeoutKey = connection.getIoThread().executeAfter(this, timeout - time, TimeUnit.MILLISECONDS);
                    return;
                }
                if (tryClose()) {
                    currentConnectionCount.decrementAndGet();
                    runPending(); //needed to avoid a very unlikely race
                }
            }
        };

        private ClientConnectionHolder(ClientConnection connection, URI uri) {
            this.connection = connection;
            this.uri = uri;
        }

        boolean tryClose() {
            if (state.compareAndSet(0, 2)) {
                IoUtils.safeClose(connection);
                return true;
            }
            return false;
        }

        boolean tryAquire() {
            return state.compareAndSet(0, 1);
        }

        @Override
        public ClientConnection getConnection() {
            return connection;
        }

        @Override
        public void done(boolean close) {
            if (close) {
                IoUtils.safeClose(connection);
            }
            timeout = System.currentTimeMillis() + connectionIdleTimeout;

            if (!state.compareAndSet(1, 0)) {
                throw HttpClientMessages.MESSAGES.connectionInWrongState();
            }

            if (timeoutKey == null && connectionIdleTimeout > 0 && !close) {
                timeoutKey = connection.getIoThread().executeAfter(timeoutTask, connectionIdleTimeout, TimeUnit.MILLISECONDS);
            }
            returnConnection(this);
        }

        @Override
        public URI getUri() {
            return uri;
        }
    }

}
