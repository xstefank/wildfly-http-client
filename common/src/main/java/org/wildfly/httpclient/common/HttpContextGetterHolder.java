package org.wildfly.httpclient.common;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.function.Supplier;

final class HttpContextGetterHolder {
    static final Supplier<WildflyHttpContext> SUPPLIER = AccessController.doPrivileged((PrivilegedAction<Supplier<WildflyHttpContext>>) WildflyHttpContext.HTTP_CONTEXT_MANAGER::getPrivilegedSupplier);
}
