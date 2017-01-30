package org.wildfly.httpclient.ejb;

import io.undertow.connector.ByteBufferPool;
import org.jboss.ejb.client.Affinity;
import org.jboss.ejb.client.EJBClientContext;
import org.jboss.ejb.client.EJBLocator;
import org.jboss.ejb.client.URIAffinity;
import org.wildfly.common.context.ContextManager;
import org.wildfly.common.context.Contextual;
import org.wildfly.discovery.AttributeValue;
import org.wildfly.httpclient.common.HostPool;
import org.wildfly.httpclient.common.HttpConnectionPool;
import org.xnio.OptionMap;
import org.xnio.XnioWorker;

import java.net.URI;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.security.AccessController.doPrivileged;

/**
 * Represents the current configured state of the HTTP contexts.
 *
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

    private static final EJBTarget NULL_TARGET = new EJBTarget(Collections.emptyList(), null, Collections.emptyList());

    private final Map<ModuleIdentifier, EJBTarget> connectionCache = new ConcurrentHashMap<>();

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
        if(affinity == Affinity.NONE) {
            return getConnectionPoolNoAffinity(ejbIdentifier);
        } else if(affinity instanceof URIAffinity) {
            return getConnectionPoolForURI(affinity.getUri());
        } else {
            throw EjbHttpClientMessages.MESSAGES.invalidAffinity(affinity);
        }
    }

    private EJBTargetContext getConnectionPoolForURI(URI uri) {
        EJBTargetContext context = uriConnectionPools.get(uri);
        if(context != null) {
            return context;
        }
        for(EJBTarget target: targets) {
            for(URI targetURI : target.getUris()) {
                if(targetURI.equals(uri)) {
                    uriConnectionPools.put(uri, target.getEjbTargetContext());
                    return target.getEjbTargetContext();
                }
            }
        }
        synchronized (this) {
            context = uriConnectionPools.get(uri);
            if(context != null) {
                return context;
            }
            HttpConnectionPool pool = new HttpConnectionPool(maxConnections, maxStreamsPerConnection, worker, this.pool, null, OptionMap.EMPTY, new HostPool(Collections.singletonList(uri)), idleTimeout);
            uriConnectionPools.put(uri, context = new EJBTargetContext(pool, eagerlyAcquireAffinity));
            return context;
        }
    }

    private EJBTargetContext getConnectionPoolNoAffinity(EJBLocator<?> ejbIdentifier) {
        ModuleIdentifier moduleIdentifier = new ModuleIdentifier(ejbIdentifier.getAppName(), ejbIdentifier.getModuleName(), ejbIdentifier.getDistinctName());
        EJBTarget cache = connectionCache.get(moduleIdentifier);
        if (cache != null) {
            if (cache == NULL_TARGET) {
                return null;
            }
            return cache.getEjbTargetContext();
        }
        EJBTarget defaultConnection = null;
        for (EJBTarget connection : targets) {
            if (connection.getModules().isEmpty()) {
                defaultConnection = connection;
            }
            for (Module module : connection.getModules()) {
                if (module.matches(moduleIdentifier.getApp(), moduleIdentifier.getModule(), moduleIdentifier.getDistinct())) {
                    connectionCache.put(moduleIdentifier, connection);
                    return connection.getEjbTargetContext();
                }
            }
        }
        if (defaultConnection == null) {
            connectionCache.put(moduleIdentifier, NULL_TARGET);
            return null;
        }
        connectionCache.put(moduleIdentifier, defaultConnection);
        return defaultConnection.getEjbTargetContext();
    }

    public List<DiscoveryResult> getDiscoveryAttributes() {
        List<DiscoveryResult> ret = new ArrayList<>();
        for(EJBTarget target : targets) {
            List<AttributeValue> withDistinct = new ArrayList<>();
            List<AttributeValue> noDistinct = new ArrayList<>();
            for(Module module : target.getModules()) {
                final String appName = module.getApp();
                final String moduleName = module.getModule();
                final String distinctName = module.getDistinct();
                if (distinctName != null && ! distinctName.isEmpty()) {
                    if (appName.isEmpty()) {
                        withDistinct.add(AttributeValue.fromString('"' + moduleName + '/' + distinctName + '"'));
                    } else {
                        withDistinct.add(AttributeValue.fromString('"' + appName + '/' + moduleName + '/' + distinctName + '"'));
                    }
                } else {
                    if (appName.isEmpty()) {
                        noDistinct.add(AttributeValue.fromString('"' + moduleName + '"'));
                    } else {
                        noDistinct.add(AttributeValue.fromString( '"' + appName + '/' + moduleName + '"'));
                    }
                }
            }
            Map<String, Collection<AttributeValue>> attrs = new HashMap<>();
            attrs.put(EJBClientContext.FILTER_ATTR_EJB_MODULE_DISTINCT, withDistinct);
            attrs.put(EJBClientContext.FILTER_ATTR_EJB_MODULE, noDistinct);
            ret.add(new DiscoveryResult(target.getUris(), attrs));
        }

        return ret;
    }

    static class Module {
        private final String app, module, distinct;

        Module(String app, String module, String distinct) {
            this.app = app;
            this.module = module;
            this.distinct = distinct;
        }

        public String getApp() {
            return app;
        }

        public String getModule() {
            return module;
        }

        public String getDistinct() {
            return distinct;
        }

        public boolean matches(final String app, final String module, final String distinct) {
            //TODO: should we be so lenient about null values? discovery does not seem to support this use case
            if (app != null && this.app != null) {
                if (!this.app.equals(app)) {
                    return false;
                }
            }
            if (module != null && this.module != null) {
                if (!this.module.equals(module)) {
                    return false;
                }
            }
            if (distinct != null && this.distinct != null) {
                if (!this.distinct.equals(distinct)) {
                    return false;
                }
            }
            return true;
        }
    }

    static class EJBTarget {
        private final List<Module> modules;
        private final EJBTargetContext ejbTargetContext;
        private final List<URI> uris;

        EJBTarget(List<Module> modules, EJBTargetContext ejbTargetContext, List<URI> uris) {
            this.modules = new ArrayList<>(modules);
            this.ejbTargetContext = ejbTargetContext;
            this.uris = new ArrayList<>(uris);
        }

        public List<Module> getModules() {
            return modules;
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
