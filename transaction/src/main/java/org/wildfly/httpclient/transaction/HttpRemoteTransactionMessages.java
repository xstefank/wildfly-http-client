package org.wildfly.httpclient.transaction;

import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.xa.XAException;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Field;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

/**
 * @author Stuart Douglas
 */
@MessageLogger(projectCode = "TXNWFHTTP")
public interface HttpRemoteTransactionMessages extends BasicLogger {

    HttpRemoteTransactionMessages MESSAGES = Logger.getMessageLogger(HttpRemoteTransactionMessages.class, HttpRemoteTransactionMessages.class.getPackage().getName());

    @Message(id = 1, value = "The protocol operation was interrupted locally")
    SystemException operationInterrupted();

    @Message(id = 2, value = "Rollback-only transaction rolled back")
    RollbackException rollbackOnlyRollback();

    @Message(id = 3, value = "Invalid transaction state")
    IllegalStateException invalidTxnState();

    @Message(id = 4, value = "Transaction operation failed due to thread interruption")
    XAException interruptedXA(@Field int errorCode);
}
