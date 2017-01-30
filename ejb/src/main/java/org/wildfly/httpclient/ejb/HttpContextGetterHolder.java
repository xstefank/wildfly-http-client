package org.wildfly.httpclient.ejb;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.function.Supplier;

final class HttpContextGetterHolder {
    static final Supplier<EJBHttpContext> SUPPLIER = AccessController.doPrivileged((PrivilegedAction<Supplier<EJBHttpContext>>) EJBHttpContext.HTTP_CONTEXT_MANAGER::getPrivilegedSupplier);
}
