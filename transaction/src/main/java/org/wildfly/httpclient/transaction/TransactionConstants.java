package org.wildfly.httpclient.transaction;

import org.wildfly.httpclient.common.ContentType;
import io.undertow.util.HttpString;

/**
 * @author Stuart Douglas
 */
interface TransactionConstants {

    String EXCEPTION = "application/x-wf-jbmar-exception;version=1";
    String XID_VERSION_1 = "application/x-wf-jbmar-xid;version=1";
    String XID = "application/x-wf-jbmar-xid";
    String NEW_TRANSACTION_ACCEPT = "application/x-wf-jbmar-exception;version=1,application/x-wf-jbmar-new-txn;version=1";
    String RECOVER_ACCEPT = "application/x-wf-txn-jbmar-xid-list;version=1,application/x-wf-jbmar-new-txn;version=1";

    HttpString READ_ONLY = new HttpString("x-wf-txn-read-only");
    HttpString TIMEOUT = new HttpString("x-wf-txn-timeout");
    HttpString RECOVERY_PARENT_NAME = new HttpString("x-wf-txn-parent-name");
    HttpString RECOVERY_FLAGS = new HttpString("x-wf-txn-recovery-flags");


    ContentType NEW_TRANSACTION = new ContentType("application/x-wf-jbmar-new-txn", 1);

    String TXN_V1_UT_BEGIN = "/txn/v1/ut/begin";
    String TXN_V1_UT_COMMIT = "/txn/v1/ut/commit";
    String TXN_V1_UT_ROLLBACK = "/txn/v1/ut/rollback";
    String TXN_V1_XA_COMMIT = "/txn/v1/xa/commit";
    String TXN_V1_XA_ROLLBACK = "/txn/v1/xa/rollback";
    String TXN_V1_XA_PREP = "/txn/v1/xa/prep";
    String TXN_V1_XA_FORGET = "/txn/v1/xa/forget";
    String TXN_V1_XA_BC = "/txn/v1/xa/bc";
    String TXN_V1_XA_RECOVER = "/txn/v1/xa/recover";
}
