package org.wildfly.httpclient.ejb;

import io.undertow.util.HttpString;

/**
 * @author Stuart Douglas
 */
interface EjbHeaders {

    //request headers
    String INVOCATION_VERSION_ONE = "application/x-wf-ejb-invocation;version=1";
    String SESSION_OPEN_VERSION_ONE = "application/x-wf-ejb-session-open;version=1";
    String AFFINITY_VERSION_ONE = "application/x-wf-ejb-affinity;version=1";


    //response headers
    String EJB_RESPONSE_VERSION_ONE = "application/x-wf-ejb-response;version=1";
    String EJB_RESPONSE_NEW_SESSION = "application/x-wf-ejb-new-session;version=1";
    String EJB_RESPONSE_EXCEPTION_VERSION_ONE = "application/x-wf-ejb-exception;version=1";
    String EJB_RESPONSE_AFFINITY_RESULT_VERSION_ONE = "application/x-wf-ejb-affinity-result;version=1";

    String TXN_RESULT_VERSION_ONE = "application/x-wf-txn-result;version=1";
    String TXN_XIDS_VERSION_ONE = "application/x-wf-txn-xids;version=1";
    String TXN_EXCEPTION_VERSION_ONE = "application/x-wf-txn-exception;version=1";

    String TXN_COMMIT_VERSION_ONE = "application/x-wf-txn-commit;version=1";
    String TXN_ROLLBACK_VERSION_ONE = "application/x-wf-txn-rollback;version=1";
    String TXN_PREPARE_VERSION_ONE = "application/x-wf-txn-prepare;version=1";
    String TXN_FORGET_VERSION_ONE = "application/x-wf-txn-forget;version=1";
    String TXN_BEFORE_COMPLETION_VERSION_ONE = "application/x-wf-txn-before-completion;version=1";
    String TXN_RECOVERY_VERSION_ONE = "application/x-wf-txn-recovery;version=1";

    HttpString EJB_SESSION_ID = new HttpString("X-wf-ejb-session-id");
    HttpString READ_ONLY = new HttpString("X-wf-txn-xa-read-only");
    HttpString PARENT_NODE_NAME = new HttpString("X-wf-txn-parent-node-name");
    HttpString RECOVERY_FLAGS = new HttpString("X-wf-txn-recovery-flags");

}
