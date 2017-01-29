package org.wildfly.httpclient.ejb;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.jboss.ejb.client.EJBReceiver;
import org.jboss.ejb.client.EJBTransportProvider;

/**
 * @author Stuart Douglas
 */
public class HttpClientProvider implements EJBTransportProvider {

    public static final String HTTP = "http";
    public static final String HTTPS = "https";
    public static final Set<String> PROTOCOLS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(HTTP, HTTPS)));

    @Override
    public boolean supportsProtocol(String uriScheme) {
        return PROTOCOLS.contains(uriScheme);
    }

    @Override
    public EJBReceiver getReceiver(String uriScheme) throws IllegalArgumentException {
        return null;
    }
}
