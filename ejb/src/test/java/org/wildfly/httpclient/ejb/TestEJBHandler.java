package org.wildfly.httpclient.ejb;

import org.jboss.ejb.client.EJBMethodLocator;
import org.jboss.ejb.server.InvocationRequest;

/**
 * @author Stuart Douglas
 */
public interface TestEJBHandler {

    Object handle(InvocationRequest.Resolved invocation, String affinity, TestEjbOutput out, EJBMethodLocator method, EJBTestServer.TestCancelHandle handle) throws Exception;
}
