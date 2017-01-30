package org.wildfly.httpclient.ejb;

import io.undertow.client.ClientRequest;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import org.jboss.ejb.client.TransactionID;
import org.jboss.ejb.client.UserTransactionID;

import java.util.Base64;

/**
 * @author Stuart Douglas
 */
class TransactionInvocationBuilder {
    private static final String ACCEPT = "application/x-wf-txn-result;version=1,application/x-wf-txn-exception;version=1";

    private TransactionID transactionID;
    private Type type;
    private boolean onePhaseCommit;
    private String sessionId;

    public TransactionID getTransactionID() {
        return transactionID;
    }

    public TransactionInvocationBuilder setTransactionID(TransactionID transactionID) {
        this.transactionID = transactionID;
        return this;
    }

    public Type getType() {
        return type;
    }

    public TransactionInvocationBuilder setType(Type type) {
        this.type = type;
        return this;
    }

    public boolean isOnePhaseCommit() {
        return onePhaseCommit;
    }

    public TransactionInvocationBuilder setOnePhaseCommit(boolean onePhaseCommit) {
        this.onePhaseCommit = onePhaseCommit;
        return this;
    }

    public String getSessionId() {
        return sessionId;
    }

    public TransactionInvocationBuilder setSessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    ClientRequest build(String mountPoint) {
        ClientRequest clientRequest = new ClientRequest();
        clientRequest.setMethod(Methods.POST);
        if (sessionId != null) {
            clientRequest.getRequestHeaders().put(Headers.COOKIE, "JSESSIONID=" + sessionId); //TODO: fix this
        }

        if (transactionID instanceof UserTransactionID) {
            clientRequest.setPath(mountPoint + "/txn/ut/" + Base64.getEncoder().encodeToString(transactionID.getEncodedForm()));
        } else {
            clientRequest.setPath(mountPoint + "/txn/xa/" + Base64.getEncoder().encodeToString(transactionID.getEncodedForm()));
        }
        clientRequest.getRequestHeaders().put(Headers.ACCEPT, ACCEPT);
        if (type == Type.COMMIT) {
            clientRequest.getRequestHeaders().put(Headers.CONTENT_TYPE, EjbHeaders.TXN_COMMIT_VERSION_ONE);
            if(onePhaseCommit) {
                clientRequest.setPath(clientRequest.getPath() + "?opc");
            }
        } else if (type == Type.ROLLBACK) {
            clientRequest.getRequestHeaders().put(Headers.CONTENT_TYPE, EjbHeaders.TXN_ROLLBACK_VERSION_ONE);
        } else if (type == Type.PREPARE) {
            clientRequest.getRequestHeaders().put(Headers.CONTENT_TYPE, EjbHeaders.TXN_PREPARE_VERSION_ONE);
        } else if (type == Type.FORGET) {
            clientRequest.getRequestHeaders().put(Headers.CONTENT_TYPE, EjbHeaders.TXN_FORGET_VERSION_ONE);
        } else if (type == Type.BEFORE_COMPLETION) {
            clientRequest.getRequestHeaders().put(Headers.CONTENT_TYPE, EjbHeaders.TXN_BEFORE_COMPLETION_VERSION_ONE);
        }

        return clientRequest;
    }

    enum Type {
        COMMIT,
        ROLLBACK,
        PREPARE,
        FORGET,
        BEFORE_COMPLETION
    }

}
