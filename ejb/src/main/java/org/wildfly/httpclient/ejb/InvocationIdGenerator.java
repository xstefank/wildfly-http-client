package org.wildfly.httpclient.ejb;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Stuart Douglas
 */
class InvocationIdGenerator {

    private static final AtomicLong count = new AtomicLong();

    public long generateInvocationId() {
        return count.incrementAndGet();
    }

}
