package org.wildfly.httpclient.ejb;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.function.Supplier;

final class HttpContextGetterHolder {
    static final Supplier<HttpContext> SUPPLIER = AccessController.doPrivileged((PrivilegedAction<Supplier<HttpContext>>) HttpContext.HTTP_CONTEXT_MANAGER::getPrivilegedSupplier);
}
