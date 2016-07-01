package org.wildfly.ejb.http;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private final List<URI> uris;
    private final Map<URI, Holder> ipAddresses;
    private volatile int currentUri;
    private final AtomicLong failureCount = new AtomicLong();

    public HostPool(List<URI> uris) {
        this.uris = new ArrayList<>(uris);
        Map<URI, Holder> map = new HashMap<>();
        for (URI uri : uris) {
            map.put(uri, new Holder(uri));
        }
        ipAddresses = Collections.unmodifiableMap(map);
    }

    public AddressResult getAddress() {
        URI uri = uris.get(currentUri);
        Holder holder = ipAddresses.get(uri);
        try {
            holder.getAddress();
        } catch (UnknownHostException e) {
            //if a initial lookup fails we try all URI's
            synchronized (this) {
                int current = this.currentUri;
                do {
                    reportFailure(new AddressResult(holder, failureCount.get()));
                    uri = uris.get(currentUri);
                    holder = ipAddresses.get(uri);
                    try {
                        holder.getAddress();
                        break;
                    } catch (UnknownHostException ignore) {

                    }
                } while (current != this.currentUri);
            }
        }
        return new AddressResult(holder, failureCount.get());
    }

    private synchronized void reportFailure(AddressResult addressResult) {
        int oldCurrent = this.currentUri;
        if(failureCount.get() != addressResult.failCount) {
            //failure has already been accounted for
            return;
        }
        if (oldCurrent + 1 == uris.size()) {
            this.currentUri = 0;
        } else {
            this.currentUri = oldCurrent + 1;
        }
        failureCount.incrementAndGet();
        addressResult.holder.markError();
    }


    private static final class Holder {
        private final URI uri;
        private volatile InetAddress[] addresses;
        private volatile int currentAddress;

        private Holder(URI uri) {
            this.uri = uri;
        }

        InetAddress getAddress() throws UnknownHostException {
            while (true) {
                InetAddress[] addresses = this.addresses;
                int currentAddress = this.currentAddress;
                if (addresses == null) {
                    synchronized (this) {
                        if ((addresses = this.addresses) == null) {
                            addresses = InetAddress.getAllByName(uri.getHost());
                            InetAddress primary = InetAddress.getByName(uri.getHost());
                            List<InetAddress> filered = new ArrayList<>();
                            //TODO: how to we handle addresses of different classes?
                            //at the moment we only take addresses of the same type that is returned from getByName
                            for(InetAddress a : addresses) {
                                if(primary.getClass().isAssignableFrom(a.getClass())) {
                                    filered.add(a);
                                }
                            }
                            addresses = this.addresses = filered.toArray(new InetAddress[filered.size()]);
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

        void markError() {
            synchronized (this) {
                int current = this.currentAddress;
                current++;
                if (current == addresses.length) {
                    current = 0;
                }
                this.currentAddress = current;
            }
        }
    }

    public class AddressResult {

        private final Holder holder;
        private final long failCount;

        public AddressResult(Holder holder, long failCount) {
            this.holder = holder;
            this.failCount = failCount;
        }

        public InetAddress getAddress() throws UnknownHostException {
            return holder.getAddress();
        }

        public URI getURI() {
            return holder.uri;
        }

        public void failed() {
            reportFailure(this);
        }

    }
}
