package org.wildfly.ejb.http;

/**
 * @author Stuart Douglas
 */
public interface TestEJBHandler {

    Object handle(TestEJBInvocation invocation);
}
