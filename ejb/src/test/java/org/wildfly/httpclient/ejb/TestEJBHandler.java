package org.wildfly.httpclient.ejb;

/**
 * @author Stuart Douglas
 */
public interface TestEJBHandler {

    Object handle(TestEJBInvocation invocation, TestEjbOutput out) throws Exception;
}
