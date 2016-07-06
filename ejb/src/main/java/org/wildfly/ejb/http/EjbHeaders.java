package org.wildfly.ejb.http;

/**
 * @author Stuart Douglas
 */
public interface EjbHeaders {

    //request headers
    String INVOCATION_VERSION_ONE = "application/x-wf-ejb-invocation;version=1";
    String SESSION_CREATE_VERSION_ONE = "application/x-wf-ejb-session-create;version=1";


    //response headers
    String EJB_RESPONSE_VERSION_ONE = "application/x-wf-ejb-response;version=1";
    String EJB_RESPONSE_NEW_SESSION = "application/x-wf-ejb-new-session;version=1";
    String EJB_EXCEPTION_VERSION_ONE = "application/x-wf-ejb-exception;version=1";

}
