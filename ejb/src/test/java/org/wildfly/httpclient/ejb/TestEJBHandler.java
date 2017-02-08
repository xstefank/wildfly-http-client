package org.wildfly.httpclient.ejb;

import org.jboss.ejb.server.InvocationRequest;

/**
 * @author Stuart Douglas
 */
public interface TestEJBHandler {

    Object handle(InvocationRequest.Resolved invocation, String affinity, TestEjbOutput out) throws Exception;
}
