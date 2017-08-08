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

import java.util.List;

import javax.naming.NamingException;

import org.wildfly.naming.client.NamingProvider;
import org.wildfly.naming.client.util.FastHashtable;
import org.wildfly.security.auth.client.PeerIdentity;

/**
 * @author Stuart Douglas
 */
public class HttpNamingProvider implements NamingProvider {

    private final List<Location> locationList;
    private final FastHashtable<String, Object> env;

    HttpNamingProvider(final List<Location> locationList, final FastHashtable<String, Object> env) {
        this.locationList = locationList;
        this.env = env;
    }

    public List<Location> getLocations() {
        return locationList;
    }

    public PeerIdentity getPeerIdentityForNaming(final Location location) throws NamingException {
        return null;
    }
}
