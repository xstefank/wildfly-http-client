package org.wildfly.httpclient.ejb;

import org.jboss.ejb.client.AttachmentKey;
import org.jboss.ejb.client.EJBReceiver;
import org.jboss.ejb.client.EJBReceiverContext;
import org.jboss.ejb.client.EJBTransportProvider;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Stuart Douglas
 */
public class HttpClientProvider implements EJBTransportProvider {

    public static final String HTTP = "http";
    public static final String HTTPS = "https";
    public static final Set<String> PROTOCOLS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(HTTP, HTTPS)));

    private static final AttachmentKey<HttpEJBReceiver> RECEIVER = new AttachmentKey<>();

    @Override
    public void notifyRegistered(EJBReceiverContext receiverContext) {
        receiverContext.getClientContext().putAttachment(RECEIVER, new HttpEJBReceiver());
    }

    @Override
    public boolean supportsProtocol(String uriScheme) {
        return PROTOCOLS.contains(uriScheme);
    }

    @Override
    public EJBReceiver getReceiver(EJBReceiverContext ejbReceiverContext, String s) throws IllegalArgumentException {
        if(PROTOCOLS.contains(s)) {
            HttpEJBReceiver receiver = ejbReceiverContext.getClientContext().getAttachment(RECEIVER);
            if(receiver != null) {
                return receiver;
            }
        }
        throw EjbHttpClientMessages.MESSAGES.couldNotCreateHttpEjbReceiverFor(s);
    }

}
