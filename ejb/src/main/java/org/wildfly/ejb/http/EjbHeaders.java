package org.wildfly.ejb.http;

/**
 * @author Stuart Douglas
 */
interface EjbHeaders {

    //request headers
    String INVOCATION_VERSION_ONE = "application/x-wf-ejb-invocation;version=1";
    String SESSION_CREATE_VERSION_ONE = "application/x-wf-ejb-session-create;version=1";
    String AFFINITY_VERSION_ONE = "application/x-wf-ejb-affinity;version=1";


    //response headers
    String EJB_RESPONSE_VERSION_ONE = "application/x-wf-ejb-response;version=1";
    String EJB_RESPONSE_NEW_SESSION = "application/x-wf-ejb-new-session;version=1";
    String EJB_RESPONSE_EXCEPTION_VERSION_ONE = "application/x-wf-ejb-exception;version=1";
    String EJB_RESPONSE_AFFINITY_RESULT_VERSION_ONE = "application/x-wf-ejb-affinity-result;version=1";


}
