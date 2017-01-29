package org.wildfly.httpclient.ejb;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.function.Supplier;

import org.wildfly.client.config.ConfigXMLParseException;

/**
 * A configuration-based HttpContext supplier.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ConfigurationHttpContextSupplier implements Supplier<HttpContext> {
    private static final HttpContext CONFIGURED_HTTP_CONTEXT;

    static {
        CONFIGURED_HTTP_CONTEXT = AccessController.doPrivileged((PrivilegedAction<HttpContext>) () -> {
            HttpContext httpContext = null;
            try {
                httpContext = EjbHttpClientXmlParser.parseHttpContext();
            } catch (ConfigXMLParseException | IOException e) {
                EjbHttpClientMessages.MESSAGES.trace("Failed to parse HttpContext XML definition", e);
            }
            httpContext = new HttpContextBuilder().build();
            return httpContext;
        });
    }

    public HttpContext get() {
        return CONFIGURED_HTTP_CONTEXT;
    }
}
