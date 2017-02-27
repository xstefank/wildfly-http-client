package org.wildfly.httpclient.ejb;

import org.wildfly.httpclient.common.ContentType;
import io.undertow.util.HttpString;

/**
 * @author Stuart Douglas
 */
interface EjbHeaders {
    //request headers
    String INVOCATION_VERSION_ONE = "application/x-wf-ejb-jbmar-invocation;version=1";
    String SESSION_OPEN_VERSION_ONE = "application/x-wf-jbmar-sess-open;version=1";
    String SESSION_OPEN = "application/x-wf-jbmar-sess-open";
    String INVOCATION = "application/x-wf-ejb-jbmar-invocation";

    //response headers
    ContentType EJB_RESPONSE_VERSION_ONE = new ContentType("application/x-wf-ejb-jbmar-response", 1);
    ContentType EJB_RESPONSE_NEW_SESSION = new ContentType("application/x-wf-ejb-jbmar-new-session", 1);

    HttpString EJB_SESSION_ID = new HttpString("x-wf-ejb-jbmar-session-id");
    HttpString INVOCATION_ID = new HttpString("X-wf-invocation-id");
}
