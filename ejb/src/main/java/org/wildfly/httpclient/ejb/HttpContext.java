package org.wildfly.httpclient.ejb;

import static java.security.AccessController.doPrivileged;

import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.wildfly.common.context.ContextManager;
import org.wildfly.common.context.Contextual;
import org.wildfly.httpclient.common.HttpConnectionPool;

/**
 * @author Stuart Douglas
 */
class HttpContext implements Contextual<HttpContext> {

    /**
     * The context manager for HTTP endpoints.
     */
    static ContextManager<HttpContext> HTTP_CONTEXT_MANAGER = doPrivileged((PrivilegedAction<ContextManager<HttpContext>>) () -> {
        final ContextManager<HttpContext> contextManager = new ContextManager<>(HttpContext.class, "jboss-remoting.endpoint");
        contextManager.setGlobalDefaultSupplierIfNotSet(ConfigurationHttpContextSupplier::new);
        return contextManager;
    });

    private static final Connection NULL_COLLECTION = new Connection(Collections.emptyList(), null);

    private final Map<ModuleIdentifier, Connection> connectionCache = new ConcurrentHashMap<>();

    private final Connection[] connections;

    HttpContext(Connection[] connections) {
        this.connections = connections;
    }

    HttpContext getCurrent() {
        return HttpContextGetterHolder.SUPPLIER.get();
    }

    @Override
    public ContextManager<HttpContext> getInstanceContextManager() {
        return HTTP_CONTEXT_MANAGER;
    }

    HttpConnectionPool getConnectionPool(final ModuleIdentifier moduleIdentifier) {
        Connection cache = connectionCache.get(moduleIdentifier);
        if (cache != null) {
            if (cache == NULL_COLLECTION) {
                return null;
            }
            return cache.getConnectionPool();
        }
        Connection defaultConnection = null;
        for (Connection connection : connections) {
            if (connection.getModules().isEmpty()) {
                defaultConnection = connection;
            }
            for (Module module : connection.getModules()) {
                if (module.matches(moduleIdentifier.getApp(), moduleIdentifier.getModule(), moduleIdentifier.getDistinct())) {
                    connectionCache.put(moduleIdentifier, connection);
                    return connection.getConnectionPool();
                }
            }
        }
        if (defaultConnection == null) {
            connectionCache.put(moduleIdentifier, NULL_COLLECTION);
            return null;
        }
        connectionCache.put(moduleIdentifier, defaultConnection);
        return defaultConnection.getConnectionPool();
    }

    static class Module {
        private final Pattern app, module, distinct;

        Module(Pattern app, Pattern module, Pattern distinct) {
            this.app = app;
            this.module = module;
            this.distinct = distinct;
        }

        public Pattern getApp() {
            return app;
        }

        public Pattern getModule() {
            return module;
        }

        public Pattern getDistinct() {
            return distinct;
        }

        public boolean isMatchAll() {
            return module == null && app == null && distinct == null;
        }

        public boolean matches(final String app, final String module, final String distinct) {
            if (app != null && this.app != null) {
                if (!this.app.matcher(app).matches()) {
                    return false;
                }
            }
            if (module != null && this.module != null) {
                if (!this.module.matcher(module).matches()) {
                    return false;
                }
            }
            if (distinct != null && this.distinct != null) {
                if (!this.distinct.matcher(distinct).matches()) {
                    return false;
                }
            }
            return true;
        }
    }

    static class Connection {
        private final List<Module> modules;
        private final HttpConnectionPool connectionPool;

        Connection(List<Module> modules, HttpConnectionPool connectionPool) {
            this.modules = new ArrayList<>(modules);
            this.connectionPool = connectionPool;
        }

        public List<Module> getModules() {
            return modules;
        }

        public HttpConnectionPool getConnectionPool() {
            return connectionPool;
        }
    }
}
