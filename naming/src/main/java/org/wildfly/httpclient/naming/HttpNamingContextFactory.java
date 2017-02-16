package org.wildfly.httpclient.naming;

import org.wildfly.naming.client.NamingContextFactory;
import org.wildfly.naming.client.NamingProvider;
import org.wildfly.naming.client.util.FastHashtable;

import javax.naming.Context;
import javax.naming.NamingException;

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
