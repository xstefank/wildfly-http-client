package org.wildfly.httpclient.common;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.function.Supplier;

import org.wildfly.client.config.ConfigXMLParseException;

/**
 * A configuration-based EJBHttpContext supplier.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ConfigurationHttpContextSupplier implements Supplier<WildflyHttpContext> {
    private static final WildflyHttpContext CONFIGURED_HTTP_CONTEXT;

    static {
        CONFIGURED_HTTP_CONTEXT = AccessController.doPrivileged((PrivilegedAction<WildflyHttpContext>) () -> {
            try {
                return HttpClientXmlParser.parseHttpContext();
            } catch (ConfigXMLParseException | IOException e) {
                HttpClientMessages.MESSAGES.trace("Failed to parse EJBHttpContext XML definition", e);
            }
            return new WildflyHttpContext.Builder().build();
        });
    }

    public WildflyHttpContext get() {
        return CONFIGURED_HTTP_CONTEXT;
    }
}
