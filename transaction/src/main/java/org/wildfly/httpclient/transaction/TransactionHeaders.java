package org.wildfly.httpclient.transaction;

import org.wildfly.httpclient.common.ContentType;

/**
 * @author Stuart Douglas
 */
interface TransactionHeaders {

    String EXCEPTION = "application/x-wf-jbmar-exception;version=1";
    String XID = "application/x-wf-jbmar-xid;version=1";
    String NEW_TRANSACTION_ACCEPT = "application/x-wf-jbmar-exception;version=1,application/x-wf-jbmar-new-txn;version=1";

    ContentType NEW_TRANSACTION = new ContentType("application/x-wf-jbmar-new-txn", 1);
}
