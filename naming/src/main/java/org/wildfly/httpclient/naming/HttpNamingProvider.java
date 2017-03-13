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

import org.wildfly.naming.client.NamingProvider;
import org.wildfly.naming.client.util.FastHashtable;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.PeerIdentity;

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
