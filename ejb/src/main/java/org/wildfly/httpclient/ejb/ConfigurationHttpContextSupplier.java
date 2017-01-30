package org.wildfly.httpclient.ejb;

import org.wildfly.client.config.ConfigXMLParseException;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.function.Supplier;

/**
 * A configuration-based EJBHttpContext supplier.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ConfigurationHttpContextSupplier implements Supplier<EJBHttpContext> {
    private static final EJBHttpContext CONFIGURED_HTTP_CONTEXT;

    static {
        CONFIGURED_HTTP_CONTEXT = AccessController.doPrivileged((PrivilegedAction<EJBHttpContext>) () -> {
            try {
                return EjbHttpClientXmlParser.parseHttpContext();
            } catch (ConfigXMLParseException | IOException e) {
                EjbHttpClientMessages.MESSAGES.trace("Failed to parse EJBHttpContext XML definition", e);
            }
            return new EJBHttpContextBuilder().build();
        });
    }

    public EJBHttpContext get() {
        return CONFIGURED_HTTP_CONTEXT;
    }
}
