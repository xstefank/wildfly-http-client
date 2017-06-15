/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.httpclient.naming;

import static java.security.AccessController.doPrivileged;

import java.net.URI;
import java.security.GeneralSecurityException;
import javax.naming.NamingException;
import javax.net.ssl.SSLContext;

import org.wildfly.naming.client.NamingProvider;
import org.wildfly.naming.client.NamingProviderFactory;
import org.wildfly.naming.client._private.Messages;
import org.wildfly.naming.client.util.FastHashtable;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.AuthenticationContextConfigurationClient;

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
        if (providerUris.length == 0) {
            throw HttpNamingClientMessages.MESSAGES.atLeastOneUri();
        }
        URI providerUri = providerUris[0]; //TODO: FIX THIS
        AuthenticationContext captured = AuthenticationContext.captureCurrent();
        final AuthenticationContextConfigurationClient client = AUTH_CONFIGURATION_CLIENT;
        AuthenticationConfiguration authenticationConfiguration = client.getAuthenticationConfiguration(providerUri, captured, -1, "jndi", "jboss");
        final SSLContext sslContext;
        try {
            sslContext = client.getSSLContext(providerUri, captured, "jndi", "jboss");
        } catch (GeneralSecurityException e) {
            throw Messages.log.failedToConfigureSslContext(e);
        }
        return new HttpNamingProvider(providerUri, env, authenticationConfiguration, sslContext);
    }

    private static final AuthenticationContextConfigurationClient AUTH_CONFIGURATION_CLIENT = doPrivileged(AuthenticationContextConfigurationClient.ACTION);

}
