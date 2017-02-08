package org.wildfly.httpclient.common;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.security.AccessController;
import java.security.GeneralSecurityException;
import java.security.PrivilegedAction;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLContext;

import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.AuthenticationContextConfigurationClient;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.XnioExecutor;
import org.xnio.XnioWorker;
import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.UndertowClient;
import io.undertow.connector.ByteBufferPool;
import io.undertow.protocols.ssl.UndertowXnioSsl;

/**
 * A pool of HTTP connections for a given host pool.
 *
 *
 * This pool is designed to give an
 *
 * @author Stuart Douglas
 */
public class HttpConnectionPool implements Closeable {

    private static final AuthenticationContextConfigurationClient AUTH_CONTEXT_CLIENT;

    static {
        AUTH_CONTEXT_CLIENT = AccessController.doPrivileged(new PrivilegedAction<AuthenticationContextConfigurationClient>() {
            @Override
            public AuthenticationContextConfigurationClient run() {
                return new AuthenticationContextConfigurationClient();
            }
        });
    }


    private final int maxConnections;
    private final int maxStreamsPerConnection;
    private final XnioWorker worker;
    private final ByteBufferPool byteBufferPool;
    private final OptionMap options;
    private final HostPool hostPool;
    private final long connectionIdleTimeout;

    private final Map<Object, ConcurrentLinkedDeque<ClientConnectionHolder>> connections = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<RequestHolder> pendingConnectionRequests = new ConcurrentLinkedDeque<>();
    private final AtomicInteger activeInvocationCount = new AtomicInteger();
    private final Map<SSLContext, UndertowXnioSsl> sslInstances = new ConcurrentHashMap<>();

    private final Object NULL_SSL_CONTEXT = new Object();

    public HttpConnectionPool(int maxConnections, int maxStreamsPerConnection, XnioWorker worker, ByteBufferPool byteBufferPool,  OptionMap options, HostPool hostPool, long connectionIdleTimeout) {
        this.maxConnections = maxConnections;
        this.maxStreamsPerConnection = maxStreamsPerConnection;
        this.worker = worker;
        this.byteBufferPool = byteBufferPool;
        this.options = options;
        this.hostPool = hostPool;
        this.connectionIdleTimeout = connectionIdleTimeout;
    }

    public void getConnection(ConnectionListener connectionListener, ErrorListener errorListener, boolean ignoreConnectionLimits) {
        AuthenticationContext context = AuthenticationContext.captureCurrent();
        pendingConnectionRequests.add(new RequestHolder(connectionListener, errorListener, ignoreConnectionLimits, context));
        runPending();
    }

    public void returnConnection(ClientConnectionHolder connection) {
        activeInvocationCount.decrementAndGet();
        if (connection.getConnection().isOpen()) {
            connections.get(connection.sslContext == null ? NULL_SSL_CONTEXT : connection.sslContext).add(connection);
        }
        runPending();
    }

    private void runPending() {

        int count;
        do {
            count = activeInvocationCount.get();
            if (count == maxConnections) {
                return;
            }
        } while (!activeInvocationCount.compareAndSet(count, count + 1));
        RequestHolder next = pendingConnectionRequests.poll();
        if (next == null) {
            activeInvocationCount.decrementAndGet();
            return;
        }
        SSLContext sslContext = null;
        UndertowXnioSsl ssl = null;
        if(hostPool.getUri().getScheme().equals("https")) {
            try {
                sslContext = AUTH_CONTEXT_CLIENT.getSSLContext(hostPool.getUri(), next.context);
            } catch (GeneralSecurityException e) {
                next.errorListener.error(e);
                return;
            }
            if(sslContext != null) {
                ssl = sslInstances.get(sslContext);
                if(ssl == null) {
                    sslInstances.put(sslContext, ssl = new UndertowXnioSsl(worker.getXnio(),OptionMap.EMPTY,sslContext));
                }
            }
        }
        Object key = sslContext == null ? NULL_SSL_CONTEXT : sslContext;
        ConcurrentLinkedDeque<ClientConnectionHolder> queue = connections.get(key);
        if(queue == null) {
            connections.putIfAbsent(key, new ConcurrentLinkedDeque<>());
            queue = connections.get(key);
        }
        for (; ; ) {
            ClientConnectionHolder existingConnection = queue.poll();
            if (existingConnection == null) {
                break;
            }
            if (existingConnection.tryAcquire()) {
                next.connectionListener.done(existingConnection);
                return;
            }
        }

        HostPool.AddressResult hostPoolAddress = hostPool.getAddress();
        InetAddress address;
        try {
            address = hostPoolAddress.getAddress();
        } catch (UnknownHostException e) {
            next.errorListener.error(e);
            return;
        }

        try {

            final SSLContext context = sslContext;
            UndertowClient.getInstance().connect(new ClientCallback<ClientConnection>() {
                @Override
                public void completed(ClientConnection result) {
                    result.getCloseSetter().set((ChannelListener<ClientConnection>) connections::remove);
                    ClientConnectionHolder clientConnectionHolder = new ClientConnectionHolder(result, hostPoolAddress.getURI(), context);
                    clientConnectionHolder.tryAcquire(); //aways suceeds
                    next.connectionListener.done(clientConnectionHolder);
                }

                @Override
                public void failed(IOException e) {
                    hostPoolAddress.failed(); //notify the host pool that this host has failed
                    activeInvocationCount.decrementAndGet();
                    next.errorListener.error(e);
                }
            }, new URI(hostPoolAddress.getURI().getScheme(), hostPoolAddress.getURI().getUserInfo(), address.getHostAddress(), hostPoolAddress.getURI().getPort(), "/", null, null), worker, ssl, byteBufferPool, options);
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

        ConnectionAuthenticationContext getAuthenticationContext();
    }


    private static class RequestHolder {
        final ConnectionListener connectionListener;
        final ErrorListener errorListener;
        final boolean ignoreConnectionLimits;
        final AuthenticationContext context;

        private RequestHolder(ConnectionListener connectionListener, ErrorListener errorListener, boolean ignoreConnectionLimits, AuthenticationContext context) {
            this.connectionListener = connectionListener;
            this.errorListener = errorListener;
            this.ignoreConnectionLimits = ignoreConnectionLimits;
            this.context = context;
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
        private final SSLContext sslContext;
        private final ConnectionAuthenticationContext connectionAuthenticationContext = new ConnectionAuthenticationContext();

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
                    activeInvocationCount.decrementAndGet();
                    runPending(); //needed to avoid a very unlikely race
                }
            }
        };

        private ClientConnectionHolder(ClientConnection connection, URI uri, SSLContext sslContext) {
            this.connection = connection;
            this.uri = uri;
            this.sslContext = sslContext;
        }

        boolean tryClose() {
            if (state.compareAndSet(0, 2)) {
                IoUtils.safeClose(connection);
                return true;
            }
            return false;
        }

        boolean tryAcquire() {
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

        @Override
        public ConnectionAuthenticationContext getAuthenticationContext() {
            return connectionAuthenticationContext;
        }
    }

}
