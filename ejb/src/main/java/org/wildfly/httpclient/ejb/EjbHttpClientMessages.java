package org.wildfly.httpclient.ejb;

import java.io.IOException;
import javax.ejb.EJBException;

import org.jboss.ejb.client.Affinity;
import org.jboss.ejb.client.EJBLocator;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

@MessageLogger(projectCode = "WFHTTPEJB")
interface EjbHttpClientMessages extends BasicLogger {

    EjbHttpClientMessages MESSAGES = Logger.getMessageLogger(EjbHttpClientMessages.class, EjbHttpClientMessages.class.getPackage().getName());

    @Message(id = 1, value = "Unexpected data in response")
    IOException unexpectedDataInResponse();

    @Message(id = 2, value = "No session id in response")
    IOException noSessionIdInResponse();

    @Message(id = 3, value = "Could not resolve target for locator %s")
    IllegalStateException couldNotResolveTargetForLocator(EJBLocator locator);

    @Message(id = 4, value = "Could create HTTP EJBReceiver for protocol %s")
    IllegalArgumentException couldNotCreateHttpEjbReceiverFor(String s);

    @Message(id = 5, value = "Invalid affinity type %s, must be URIAffinity of NONE")
    IllegalArgumentException invalidAffinity(Affinity affinity);

    @Message(id = 6, value = "EJB not stateful")
    IllegalArgumentException notStateful();

    @Message(id = 7, value = "Session was not active")
    IllegalArgumentException sessionNotActive();

    @Message(id = 8, value = "Session was not active")
    IllegalArgumentException noSuchMethod();

    @Message(id = 9, value = "Wrong view type")
    EJBException wrongViewType();

    @Message(id = 10, value = "Cannot enlist in transaction")
    IllegalStateException cannotEnlistTx();

    @Message(id = 11, value = "Invalid transaction type %s")
    IOException invalidTransactionType(int type);
}
