package org.wildfly.httpclient.naming;

import static java.security.AccessController.doPrivileged;

import java.net.URI;
import javax.naming.NamingException;

import org.wildfly.naming.client.NamingProvider;
import org.wildfly.naming.client.NamingProviderFactory;
import org.wildfly.naming.client.util.FastHashtable;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.AuthenticationContextConfigurationClient;
import org.wildfly.security.auth.client.MatchRule;

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

    @Override
    public NamingProvider createProvider(FastHashtable<String, Object> env, URI... providerUris) throws NamingException {
        if(providerUris.length == 0) {
            throw HttpNamingClientMessages.MESSAGES.atLeastOneUri();
        }
        URI providerUri = providerUris[0]; //TODO: FIX THIS
        AuthenticationContext captured = AuthenticationContext.captureCurrent();
        AuthenticationConfiguration mergedConfiguration = AUTH_CONFIGURATION_CLIENT.getAuthenticationConfiguration(providerUri, captured);

        final AuthenticationContext context = AuthenticationContext.empty().with(MatchRule.ALL, mergedConfiguration);
        return new HttpNamingProvider(providerUri, context, env);
    }

    private static final AuthenticationContextConfigurationClient AUTH_CONFIGURATION_CLIENT = doPrivileged(AuthenticationContextConfigurationClient.ACTION);

}
