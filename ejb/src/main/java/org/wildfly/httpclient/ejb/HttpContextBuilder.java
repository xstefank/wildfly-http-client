package org.wildfly.httpclient.ejb;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.wildfly.httpclient.common.HostPool;
import org.wildfly.httpclient.common.HttpConnectionPool;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.xnio.OptionMap;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import io.undertow.connector.ByteBufferPool;
import io.undertow.server.DefaultByteBufferPool;

/**
 * @author Stuart Douglas
 */
class HttpContextBuilder {
    private InetSocketAddress defaultBindAddress;
    private final List<ConnectionBuilder> connections = new ArrayList<>();

    public HttpContext build() {
        try {
            Xnio xnio = Xnio.getInstance();
            XnioWorker worker = xnio.createWorker(OptionMap.EMPTY); //TODO
            ByteBufferPool pool = new DefaultByteBufferPool(true, 1024); //TODO
            HttpContext.Connection[] connections = new HttpContext.Connection[this.connections.size()];
            for (int i = 0; i < this.connections.size(); ++i) {
                ConnectionBuilder sb = this.connections.get(i);

                HostPool hp = new HostPool(sb.getUris());

                HttpContext.Connection connection = new HttpContext.Connection(sb.modules, new HttpConnectionPool(sb.getMaxConnections(), sb.getMaxStreamsPerConnection(), worker, pool, null, OptionMap.EMPTY, hp, sb.getIdleTimeout()));
                connections[i] = connection;
            }
            return new HttpContext(connections);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void setDefaultBindAddress(InetSocketAddress defaultBindAddress) {
        this.defaultBindAddress = defaultBindAddress;
    }

    public InetSocketAddress getDefaultBindAddress() {
        return defaultBindAddress;
    }

    public ConnectionBuilder addConnection() {
        ConnectionBuilder connectionBuilder = new ConnectionBuilder();
        connections.add(connectionBuilder);
        return connectionBuilder;
    }

    public List<ConnectionBuilder> getConnections() {
        return connections;
    }

    class ConnectionBuilder {
        final List<URI> uris = new ArrayList<>();
        private AuthenticationContext authenticationContext;
        private InetSocketAddress bindAddress;
        private final List<HttpContext.Module> modules = new ArrayList<>();
        private long idleTimeout;
        private int maxConnections;
        private int maxStreamsPerConnection;

        public void addUri(URI uri) {
            this.uris.add(uri);
        }

        public List<URI> getUris() {
            return uris;
        }

        public List<HttpContext.Module> getModules() {
            return modules;
        }

        public void setAuthenticationContext(AuthenticationContext authenticationContext) {
            this.authenticationContext = authenticationContext;
        }

        public AuthenticationContext getAuthenticationContext() {
            return authenticationContext;
        }

        public void setBindAddress(InetSocketAddress bindAddress) {
            this.bindAddress = bindAddress;
        }

        public InetSocketAddress getBindAddress() {
            return bindAddress;
        }

        public void addModule(String app, String module, String distinct) {
            modules.add(new HttpContext.Module(Pattern.compile(app), Pattern.compile(module), Pattern.compile(distinct)));
        }

        public long getIdleTimeout() {
            return idleTimeout;
        }

        public void setIdleTimeout(long idleTimeout) {
            this.idleTimeout = idleTimeout;
        }

        public int getMaxConnections() {
            return maxConnections;
        }

        public void setMaxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
        }

        public int getMaxStreamsPerConnection() {
            return maxStreamsPerConnection;
        }

        public void setMaxStreamsPerConnection(int maxStreamsPerConnection) {
            this.maxStreamsPerConnection = maxStreamsPerConnection;
        }
    }
}
