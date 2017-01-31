package org.wildfly.httpclient.ejb;

import java.io.IOException;

import org.jboss.ejb.client.Affinity;
import org.jboss.ejb.client.EJBLocator;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

@MessageLogger(projectCode = "WFHTTPEJB")
interface EjbHttpClientMessages extends BasicLogger {

    EjbHttpClientMessages MESSAGES = Logger.getMessageLogger(EjbHttpClientMessages.class, EjbHttpClientMessages.class.getPackage().getName());

    @Message(id = 2, value = "Session open timed out")
    RuntimeException sessionOpenTimedOut();

    @Message(id = 3, value = "Unexpected data in response")
    IOException unexpectedDataInResponse();


    @Message(id = 6, value = "No URI provided for connection %s")
    @LogMessage(level = Logger.Level.ERROR)
    void uriCannotBeNull(String conn);

    @Message(id = 7, value = "Failed to parse URI %s for connection %s")
    RuntimeException failedToParseURI(String uri, String conn);

    @Message(id = 8, value = "No modules specified for connection %s")
    RuntimeException noModulesSelectedForConnection(String conn);

    @Message(id = 9, value = "Invalid app:module:distinct specification %s for connection %s")
    RuntimeException invalidModuleSpec(String mod, String conn);

    @Message(id = 11, value = "No session id in response")
    IOException noSessionIdInResponse();

    @Message(id = 14, value = "Could not resolve target for locator %s")
    IllegalStateException couldNotResolveTargetForLocator(EJBLocator locator);

    @Message(id = 15, value = "Could create HTTP EJBReceiver for protocol %s")
    IllegalArgumentException couldNotCreateHttpEjbReceiverFor(String s);

    @Message(id = 16, value = "Invalid affinity type %s, must be URIAffinity of NONE")
    IllegalArgumentException invalidAffinity(Affinity affinity);
}
