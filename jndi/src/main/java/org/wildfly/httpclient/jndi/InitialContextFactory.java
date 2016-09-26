package org.wildfly.httpclient.jndi;

import java.util.Hashtable;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

/**
 * @author Stuart Douglas
 */
public class InitialContextFactory implements javax.naming.spi.InitialContextFactory {
    @Override
    public Context getInitialContext(Hashtable<?, ?> environment) throws NamingException {

        String provider = (String) environment.get(InitialContext.PROVIDER_URL);
        if (provider == null) {
            throw JndiClientMessages.MESSAGES.providerUrlMustBeProvided();
        }
        String[] urls = provider.split(";");

        return null;
    }
}
