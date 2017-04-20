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

import java.net.URI;
import javax.naming.NamingException;
import javax.net.ssl.SSLContext;

import org.wildfly.naming.client.NamingProvider;
import org.wildfly.naming.client.util.FastHashtable;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.PeerIdentity;

/**
 * @author Stuart Douglas
 */
public class HttpNamingProvider implements NamingProvider {

    private final URI uri;
    private final FastHashtable<String, Object> env;

    private final AuthenticationConfiguration authenticationConfiguration;
    private final SSLContext sslContext;

    public HttpNamingProvider(URI uri, FastHashtable<String, Object> env, AuthenticationConfiguration authenticationConfiguration, SSLContext sslContext) {
        this.uri = uri;
        this.env = env;
        this.authenticationConfiguration = authenticationConfiguration;
        this.sslContext = sslContext;
    }

    @Override
    public URI getProviderUri() {
        return uri;
    }

    @Override
    public AuthenticationConfiguration getAuthenticationConfiguration() {
        return authenticationConfiguration;
    }

    @Override
    public SSLContext getSSLContext() {
        return sslContext;
    }

    @Override
    public PeerIdentity getPeerIdentityForNaming() throws NamingException {
        return null;//TODO: all the auth side of things
    }
}
