package org.wildfly.httpclient.ejb;

import static java.security.AccessController.doPrivileged;

import java.net.URI;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.ejb.client.Affinity;
import org.jboss.ejb.client.EJBLocator;
import org.jboss.ejb.client.URIAffinity;
import org.wildfly.common.context.ContextManager;
import org.wildfly.common.context.Contextual;
import org.wildfly.discovery.AttributeValue;
import org.wildfly.httpclient.common.HostPool;
import org.wildfly.httpclient.common.HttpConnectionPool;
import org.xnio.OptionMap;
import org.xnio.XnioWorker;
import io.undertow.connector.ByteBufferPool;

/**
 * Represents the current configured state of the HTTP contexts.
 *
 * @author Stuart Douglas
 */
class EJBHttpContext implements Contextual<EJBHttpContext> {

    /**
     * The context manager for HTTP endpoints.
     */
    static ContextManager<EJBHttpContext> HTTP_CONTEXT_MANAGER = doPrivileged((PrivilegedAction<ContextManager<EJBHttpContext>>) () -> {
        final ContextManager<EJBHttpContext> contextManager = new ContextManager<>(EJBHttpContext.class, "jboss-ejb-http-client.http-context");
        contextManager.setGlobalDefaultSupplierIfNotSet(ConfigurationHttpContextSupplier::new);
        return contextManager;
    });

    /**
     * TODO: figure out some way to remove these when all the connections are closed, it has the potential to be very racey
     */
    private final Map<URI, EJBTargetContext> uriConnectionPools = new ConcurrentHashMap<>();

    private final EJBTarget[] targets;

    private final int maxConnections;
    private final int maxStreamsPerConnection;
    private final long idleTimeout;
    private final boolean eagerlyAcquireAffinity;
    private final XnioWorker worker;
    private final ByteBufferPool pool;

    EJBHttpContext(EJBTarget[] targets, int maxConnections, int maxStreamsPerConnection, long idleTimeout, boolean eagerlyAcquireAffinity, XnioWorker worker, ByteBufferPool pool) {
        this.targets = targets;
        this.maxConnections = maxConnections;
        this.maxStreamsPerConnection = maxStreamsPerConnection;
        this.idleTimeout = idleTimeout;
        this.eagerlyAcquireAffinity = eagerlyAcquireAffinity;
        this.worker = worker;
        this.pool = pool;
    }

    static EJBHttpContext getCurrent() {
        return HttpContextGetterHolder.SUPPLIER.get();
    }

    @Override
    public ContextManager<EJBHttpContext> getInstanceContextManager() {
        return HTTP_CONTEXT_MANAGER;
    }

    EJBTargetContext getEJBTargetContext(final EJBLocator<?> ejbIdentifier) {
        Affinity affinity = ejbIdentifier.getAffinity();
        if (affinity instanceof URIAffinity) {
            return getConnectionPoolForURI(affinity.getUri());
        } else {
            throw EjbHttpClientMessages.MESSAGES.invalidAffinity(affinity);
        }
    }

    private EJBTargetContext getConnectionPoolForURI(URI uri) {
        EJBTargetContext context = uriConnectionPools.get(uri);
        if (context != null) {
            return context;
        }
        for (EJBTarget target : targets) {
            for (URI targetURI : target.getUris()) {
                if (targetURI.equals(uri)) {
                    uriConnectionPools.put(uri, target.getEjbTargetContext());
                    return target.getEjbTargetContext();
                }
            }
        }
        synchronized (this) {
            context = uriConnectionPools.get(uri);
            if (context != null) {
                return context;
            }
            HttpConnectionPool pool = new HttpConnectionPool(maxConnections, maxStreamsPerConnection, worker, this.pool, null, OptionMap.EMPTY, new HostPool(Collections.singletonList(uri)), idleTimeout);
            uriConnectionPools.put(uri, context = new EJBTargetContext(pool, eagerlyAcquireAffinity));
            return context;
        }
    }

    static class EJBTarget {
        private final EJBTargetContext ejbTargetContext;
        private final List<URI> uris;

        EJBTarget(EJBTargetContext ejbTargetContext, List<URI> uris) {
            this.ejbTargetContext = ejbTargetContext;
            this.uris = new ArrayList<>(uris);
        }

        public EJBTargetContext getEjbTargetContext() {
            return ejbTargetContext;
        }

        public List<URI> getUris() {
            return uris;
        }
    }

    class DiscoveryResult {
        private final List<URI> uri;
        private final Map<String, Collection<AttributeValue>> attributes;

        DiscoveryResult(List<URI> uri, Map<String, Collection<AttributeValue>> attributes) {
            this.uri = uri;
            this.attributes = attributes;
        }

        public List<URI> getUri() {
            return uri;
        }

        public Map<String, Collection<AttributeValue>> getAttributes() {
            return attributes;
        }
    }
}
