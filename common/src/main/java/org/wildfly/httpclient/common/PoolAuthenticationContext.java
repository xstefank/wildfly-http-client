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

package org.wildfly.httpclient.common;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.util.Locale;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;

import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.AuthenticationContextConfigurationClient;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.util.FlexBase64;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;

/**
 * Class that holds authentication information for a connection
 *
 * @author Stuart Douglas
 */
class PoolAuthenticationContext {

    private static final AuthenticationContextConfigurationClient AUTH_CONTEXT_CLIENT;

    static {
        AUTH_CONTEXT_CLIENT = AccessController.doPrivileged((PrivilegedAction<AuthenticationContextConfigurationClient>) AuthenticationContextConfigurationClient::new);
    }

    private volatile Type current;

    boolean handleResponse(ClientResponse response) {
        if (response.getResponseCode() != StatusCodes.UNAUTHORIZED) {
            return false;
        }
        String authenticate = response.getResponseHeaders().getFirst(Headers.WWW_AUTHENTICATE);
        if (authenticate == null) {
            return false;
        }
        if (authenticate.toLowerCase(Locale.ENGLISH).startsWith("basic ")) {
            current = Type.BASIC;
            return true;
        }
        return false; //TODO: digest
    }

    boolean prepareRequest(URI uri, ClientRequest request) {
        if (current == Type.BASIC) {

            AuthenticationContext context = AuthenticationContext.captureCurrent();
            AuthenticationConfiguration config = AUTH_CONTEXT_CLIENT.getAuthenticationConfiguration(uri, context);
            Principal principal = AUTH_CONTEXT_CLIENT.getPrincipal(config);
            PasswordCallback callback = new PasswordCallback("password", false);
            try {
                AUTH_CONTEXT_CLIENT.getCallbackHandler(config).handle(new Callback[]{callback});
            } catch (IOException | UnsupportedCallbackException e) {
                return false;
            }
            char[] password = callback.getPassword();
            String challenge = principal.getName() + ":" + new String(password);
            request.getRequestHeaders().put(Headers.AUTHORIZATION, "Basic " + FlexBase64.encodeString(challenge.getBytes(StandardCharsets.UTF_8), false));
            return true;
        }
        return false;
    }

    enum Type {
        NONE,
        BASIC,
        DIGEST
    }

}
