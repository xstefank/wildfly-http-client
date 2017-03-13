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
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.function.Supplier;

import org.wildfly.client.config.ConfigXMLParseException;

/**
 * A configuration-based EJBHttpContext supplier.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ConfigurationHttpContextSupplier implements Supplier<WildflyHttpContext> {
    private static final WildflyHttpContext CONFIGURED_HTTP_CONTEXT;

    static {
        CONFIGURED_HTTP_CONTEXT = AccessController.doPrivileged((PrivilegedAction<WildflyHttpContext>) () -> {
            try {
                return HttpClientXmlParser.parseHttpContext();
            } catch (ConfigXMLParseException | IOException e) {
                HttpClientMessages.MESSAGES.trace("Failed to parse EJBHttpContext XML definition", e);
            }
            return new WildflyHttpContext.Builder().build();
        });
    }

    public WildflyHttpContext get() {
        return CONFIGURED_HTTP_CONTEXT;
    }
}
