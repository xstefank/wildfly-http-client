package org.wildfly.httpclient.naming;

import org.wildfly.naming.client.NamingProvider;
import org.wildfly.naming.client.NamingProviderFactory;
import org.wildfly.naming.client.util.FastHashtable;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.AuthenticationContextConfigurationClient;
import org.wildfly.security.auth.client.MatchRule;

import javax.naming.NamingException;
import java.net.URI;

import static java.security.AccessController.doPrivileged;

/**
 * @author Stuart Douglas
 */
public class HttpNamingProviderFactory implements NamingProviderFactory {
    @Override
    public boolean supportsUriScheme(String providerScheme, FastHashtable<String, Object> env) {
        switch (providerScheme) {
            case "http":
            case "https":
                return true;
        }
        return false;
    }

    private static final AuthenticationContextConfigurationClient AUTH_CONFIGURATION_CLIENT = doPrivileged(AuthenticationContextConfigurationClient.ACTION);

    public NamingProvider createProvider(final URI providerUri, final FastHashtable<String, Object> env) throws NamingException {

        AuthenticationContext captured = AuthenticationContext.captureCurrent();
        AuthenticationConfiguration mergedConfiguration = AUTH_CONFIGURATION_CLIENT.getAuthenticationConfiguration(providerUri, captured);

        final AuthenticationContext context = AuthenticationContext.empty().with(MatchRule.ALL, mergedConfiguration);
        return new HttpNamingProvider(providerUri, context, env);

    }
}
