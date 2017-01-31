package org.wildfly.httpclient.common;

import io.undertow.connector.ByteBufferPool;
import io.undertow.server.DefaultByteBufferPool;
import org.xnio.OptionMap;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Stuart Douglas
 */
class WildflyHttpContextBuilder {
    private InetSocketAddress defaultBindAddress;
    private long idleTimeout;
    private int maxConnections;
    private int maxStreamsPerConnection;
    private Boolean eagerlyAcquireSession;
    private final List<HttpConfigBuilder> targets = new ArrayList<>();

    WildflyHttpContext build() {
        try {
            Xnio xnio = Xnio.getInstance();
            XnioWorker worker = xnio.createWorker(OptionMap.EMPTY); //TODO
            ByteBufferPool pool = new DefaultByteBufferPool(true, 1024); //TODO
            //TODO: ssl config
            WildflyHttpContext.ConfigSection[] connections = new WildflyHttpContext.ConfigSection[this.targets.size()];

            long idleTimout = this.idleTimeout > 0 ? this.idleTimeout : 60000;
            int maxConnections = this.maxConnections > 0 ? this.maxConnections : 10;
            int maxStreamsPerConnection = this.maxStreamsPerConnection > 0? this.maxStreamsPerConnection : 10;

            for (int i = 0; i < this.targets.size(); ++i) {
                HttpConfigBuilder sb = this.targets.get(i);
                HostPool hp = new HostPool(sb.getUris());
                boolean eager = this.eagerlyAcquireSession == null ? false: this.eagerlyAcquireSession;
                if(sb.getEagerlyAcquireSession() != null && sb.getEagerlyAcquireSession()) {
                    eager = true;
                }
                WildflyHttpContext.ConfigSection connection = new WildflyHttpContext.ConfigSection(new HttpTargetContext(new HttpConnectionPool(sb.getMaxConnections() > 0 ? sb.getMaxConnections() : maxConnections, sb.getMaxStreamsPerConnection() > 0 ? sb.getMaxStreamsPerConnection() : maxStreamsPerConnection, worker, pool, null, OptionMap.EMPTY, hp, sb.getIdleTimeout() > 0 ? sb.getIdleTimeout() : idleTimout), eager), sb.getUris());
                connections[i] = connection;
            }
            return new WildflyHttpContext(connections, maxConnections, maxStreamsPerConnection, idleTimeout, eagerlyAcquireSession == null ? false : eagerlyAcquireSession, worker, pool);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    void setDefaultBindAddress(InetSocketAddress defaultBindAddress) {
        this.defaultBindAddress = defaultBindAddress;
    }

    InetSocketAddress getDefaultBindAddress() {
        return defaultBindAddress;
    }

    HttpConfigBuilder addConfig() {
        HttpConfigBuilder builder = new HttpConfigBuilder();
        targets.add(builder);
        return builder;
    }

    List<HttpConfigBuilder> getTargets() {
        return targets;
    }


    long getIdleTimeout() {
        return idleTimeout;
    }

    void setIdleTimeout(long idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    int getMaxConnections() {
        return maxConnections;
    }

    void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    int getMaxStreamsPerConnection() {
        return maxStreamsPerConnection;
    }

    void setMaxStreamsPerConnection(int maxStreamsPerConnection) {
        this.maxStreamsPerConnection = maxStreamsPerConnection;
    }

    Boolean getEagerlyAcquireSession() {
        return eagerlyAcquireSession;
    }

    void setEagerlyAcquireSession(Boolean eagerlyAcquireSession) {
        this.eagerlyAcquireSession = eagerlyAcquireSession;
    }

    class HttpConfigBuilder {
        final List<URI> uris = new ArrayList<>();
        private InetSocketAddress bindAddress;
        private long idleTimeout;
        private int maxConnections;
        private int maxStreamsPerConnection;
        private Boolean eagerlyAcquireSession;

        void addUri(URI uri) {
            this.uris.add(uri);
        }

        List<URI> getUris() {
            return uris;
        }

        void setBindAddress(InetSocketAddress bindAddress) {
            this.bindAddress = bindAddress;
        }

        InetSocketAddress getBindAddress() {
            return bindAddress;
        }

        long getIdleTimeout() {
            return idleTimeout;
        }

        void setIdleTimeout(long idleTimeout) {
            this.idleTimeout = idleTimeout;
        }

        int getMaxConnections() {
            return maxConnections;
        }

        void setMaxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
        }

        int getMaxStreamsPerConnection() {
            return maxStreamsPerConnection;
        }

        void setMaxStreamsPerConnection(int maxStreamsPerConnection) {
            this.maxStreamsPerConnection = maxStreamsPerConnection;
        }

        Boolean getEagerlyAcquireSession() {
            return eagerlyAcquireSession;
        }

        void setEagerlyAcquireSession(Boolean eagerlyAcquireSession) {
            this.eagerlyAcquireSession = eagerlyAcquireSession;
        }
    }
}
