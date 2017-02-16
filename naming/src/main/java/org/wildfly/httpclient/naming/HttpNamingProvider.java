package org.wildfly.httpclient.naming;

import org.wildfly.naming.client.NamingProvider;
import org.wildfly.naming.client.util.FastHashtable;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.PeerIdentity;

import javax.naming.NamingException;
import java.net.URI;

/**
 * @author Stuart Douglas
 */
public class HttpNamingProvider implements NamingProvider {

    private final URI uri;
    private final AuthenticationContext context;
    private final FastHashtable<String, Object> env;

    public HttpNamingProvider(URI uri, AuthenticationContext context, FastHashtable<String, Object> env) {
        this.uri = uri;
        this.context = context;
        this.env = env;
    }

    @Override
    public URI getProviderUri() {
        return uri;
    }

    @Override
    public PeerIdentity getPeerIdentityForNaming() throws NamingException {
        return null;//TODO: all the auth side of things
    }
}
