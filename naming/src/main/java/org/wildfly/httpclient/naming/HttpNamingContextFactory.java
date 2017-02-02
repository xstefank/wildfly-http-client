package org.wildfly.httpclient.naming;

import javax.naming.Context;
import javax.naming.NamingException;

import org.wildfly.naming.client.NamingContextFactory;
import org.wildfly.naming.client.NamingProvider;
import org.wildfly.naming.client.util.FastHashtable;

/**
 * @author Stuart Douglas
 */
public class HttpNamingContextFactory implements NamingContextFactory {
    @Override
    public boolean supportsUriScheme(NamingProvider namingProvider, String nameScheme) {
        return namingProvider instanceof HttpNamingProvider && nameScheme == null;
    }

    @Override
    public Context createRootContext(NamingProvider namingProvider, String nameScheme, FastHashtable<String, Object> env) throws NamingException {
        return new HttpRootContext(env, (HttpNamingProvider) namingProvider, nameScheme);
    }
}
