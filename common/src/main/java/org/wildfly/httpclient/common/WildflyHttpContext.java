package org.wildfly.httpclient.common;

import static java.security.AccessController.doPrivileged;

import java.net.URI;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.wildfly.common.context.ContextManager;
import org.wildfly.common.context.Contextual;
import org.xnio.OptionMap;
import org.xnio.XnioWorker;
import io.undertow.connector.ByteBufferPool;

/**
 * Represents the current configured state of the HTTP contexts.
 *
 * @author Stuart Douglas
 */
public class WildflyHttpContext implements Contextual<WildflyHttpContext> {

    /**
     * The context manager for HTTP endpoints.
     */
    static ContextManager<WildflyHttpContext> HTTP_CONTEXT_MANAGER = doPrivileged((PrivilegedAction<ContextManager<WildflyHttpContext>>) () -> {
        final ContextManager<WildflyHttpContext> contextManager = new ContextManager<>(WildflyHttpContext.class, "jboss-ejb-http-client.http-context");
        contextManager.setGlobalDefaultSupplierIfNotSet(ConfigurationHttpContextSupplier::new);
        return contextManager;
    });

    /**
     * TODO: figure out some way to remove these when all the connections are closed, it has the potential to be very racey
     */
    private final Map<URI, HttpTargetContext> uriConnectionPools = new ConcurrentHashMap<>();

    private final ConfigSection[] targets;

    private final int maxConnections;
    private final int maxStreamsPerConnection;
    private final long idleTimeout;
    private final boolean eagerlyAcquireAffinity;
    private final XnioWorker worker;
    private final ByteBufferPool pool;

    WildflyHttpContext(ConfigSection[] targets, int maxConnections, int maxStreamsPerConnection, long idleTimeout, boolean eagerlyAcquireAffinity, XnioWorker worker, ByteBufferPool pool) {
        this.targets = targets;
        this.maxConnections = maxConnections;
        this.maxStreamsPerConnection = maxStreamsPerConnection;
        this.idleTimeout = idleTimeout;
        this.eagerlyAcquireAffinity = eagerlyAcquireAffinity;
        this.worker = worker;
        this.pool = pool;
    }

    public static WildflyHttpContext getCurrent() {
        return HttpContextGetterHolder.SUPPLIER.get();
    }

    @Override
    public ContextManager<WildflyHttpContext> getInstanceContextManager() {
        return HTTP_CONTEXT_MANAGER;
    }

    public HttpTargetContext getTargetContext(final URI uri) {
        return getConnectionPoolForURI(uri);
    }

    private HttpTargetContext getConnectionPoolForURI(URI uri) {
        HttpTargetContext context = uriConnectionPools.get(uri);
        if (context != null) {
            return context;
        }
        for (ConfigSection target : targets) {
            for (URI targetURI : target.getUris()) {
                if (targetURI.equals(uri)) {
                    target.getHttpTargetContext().init();
                    uriConnectionPools.put(uri, target.getHttpTargetContext());
                    return target.getHttpTargetContext();
                }
            }
        }
        synchronized (this) {
            context = uriConnectionPools.get(uri);
            if (context != null) {
                return context;
            }
            HttpConnectionPool pool = new HttpConnectionPool(maxConnections, maxStreamsPerConnection, worker, this.pool, null, OptionMap.EMPTY, new HostPool(Collections.singletonList(uri)), idleTimeout);
            uriConnectionPools.put(uri, context = new HttpTargetContext(pool, eagerlyAcquireAffinity));
            context.init();
            return context;
        }
    }

    static class ConfigSection {
        private final HttpTargetContext httpTargetContext;
        private final List<URI> uris;

        ConfigSection(HttpTargetContext httpTargetContext, List<URI> uris) {
            this.httpTargetContext = httpTargetContext;
            this.uris = new ArrayList<>(uris);
        }

        public HttpTargetContext getHttpTargetContext() {
            return httpTargetContext;
        }

        public List<URI> getUris() {
            return uris;
        }
    }


}
