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

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A host pool is defined as one or more hosts that are are serving
 * the same back end applications.
 * <p>
 * For hosts to be in the same host pool it must be possible to make an invocation against any host
 * interchangeably. In general the connection pool will only use a single host at a time, however if the
 * connection fails it will attempt to use other hosts in the pool.
 * <p>
 * In a lot of cases this will only contain a single host, however if there are multiple load balancer hosts
 * that service the same underlying application then this class allows for invocations to be directed to either
 * if one falls over.
 * <p>
 * This class is also DNS load balancing aware. If there are multiple IP's for a given URL the different URL's will
 * be added to the rotation.
 * <p>
 * This host pool will attempt to simply use a single address, if it is notified of failure on that address it will
 * instead select a different URI. If there are multiple addresses per URI then the next time the URI is selected
 * it will attempt to use a new address.
 *
 *
 * @author Stuart Douglas
 */
public class HostPool {

    private final URI uri;
    private volatile InetAddress[] addresses;
    private volatile int currentAddress;
    private final AtomicLong failureCount = new AtomicLong();

    public HostPool(URI uri) {
        this.uri = uri;
    }

    public AddressResult getAddress() {
        return new AddressResult(failureCount.get());
    }

    private InetAddress getAddressImpl() throws UnknownHostException {
        while (true) {
            InetAddress[] addresses = this.addresses;
            int currentAddress = this.currentAddress;
            if (addresses == null) {
                synchronized (this) {
                    if ((addresses = this.addresses) == null) {
                        addresses = InetAddress.getAllByName(uri.getHost());
                        InetAddress primary = InetAddress.getByName(uri.getHost());
                        List<InetAddress> filtered = new ArrayList<>();
                        //TODO: how to we handle addresses of different classes?
                        //at the moment we only take addresses of the same type that is returned from getByName
                        for(InetAddress a : addresses) {
                            if(primary.getClass().isAssignableFrom(a.getClass())) {
                                filtered.add(a);
                            }
                        }
                        addresses = this.addresses = filtered.toArray(new InetAddress[filtered.size()]);
                    }
                    this.currentAddress = currentAddress = new Random().nextInt(this.addresses.length);
                }
            }
            if (currentAddress >= addresses.length) {
                continue; //minor chance of a race, as the address list and current address are not invoked atomically just re-invoke
            }
            return addresses[currentAddress];
        }
    }

    public URI getUri() {
        return uri;
    }

    private void markError() {
        synchronized (this) {
            int current = currentAddress;
            current++;
            if (current == addresses.length) {
                current = 0;
            }
            this.currentAddress = current;
        }
    }
    public class AddressResult {

        private final long failCount;

        public AddressResult(long failCount) {
            this.failCount = failCount;
        }

        public InetAddress getAddress() throws UnknownHostException {
            return getAddressImpl();
        }

        public URI getURI() {
            return uri;
        }

        public void failed() {
            markError();
        }

    }
}
