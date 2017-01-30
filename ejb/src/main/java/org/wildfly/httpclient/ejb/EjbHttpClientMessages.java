package org.wildfly.httpclient.ejb;

import io.undertow.client.ClientResponse;
import org.jboss.ejb.client.Affinity;
import org.jboss.ejb.client.EJBLocator;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;
import org.wildfly.client.config.ConfigXMLParseException;

import java.io.IOException;

@MessageLogger(projectCode = "WFHTTPEJB")
interface EjbHttpClientMessages extends BasicLogger {

    EjbHttpClientMessages MESSAGES = Logger.getMessageLogger(EjbHttpClientMessages.class, EjbHttpClientMessages.class.getPackage().getName());

    @Message(id = 1, value = "Invalid response type %s")
    IOException invalidResponseType(String type);

    @Message(id = 2, value = "Session open timed out")
    RuntimeException sessionOpenTimedOut();

    @Message(id = 3, value = "Unexpected data in response")
    IOException unexpectedDataInResponse();

    @Message(id = 4, value = "Failed to acquire session")
    @LogMessage(level = Logger.Level.ERROR)
    void failedToAcquireSession(@Cause Throwable t);

    @Message(id = 6, value = "No URI provided for connection %s")
    @LogMessage(level = Logger.Level.ERROR)
    void uriCannotBeNull(String conn);

    @Message(id = 7, value = "Failed to parse URI %s for connection %s")
    RuntimeException failedToParseURI(String uri, String conn);

    @Message(id = 8, value = "No modules specified for connection %s")
    RuntimeException noModulesSelectedForConnection(String conn);

    @Message(id = 9, value = "Invalid app:module:distinct specification %s for connection %s")
    RuntimeException invalidModuleSpec(String mod, String conn);

    @Message(id = 10, value = "Invalid response code %s (full response %s)")
    IOException invalidResponseCode(int responseCode, ClientResponse response);

    @Message(id = 11, value = "No session id in response")
    IOException noSessionIdInResponse();

    @Message(id = 12, value = "Port value %s out of range")
    ConfigXMLParseException portValueOutOfRange(int port);

    @Message(id = 13, value = "Invalid affinity type %s, must be URIAffinity of NONE")
    IllegalArgumentException invalidAffinity(Affinity affinity);

    @Message(id = 14, value = "Could not resolve target for locator %s")
    IllegalStateException couldNotResolveTargetForLocator(EJBLocator locator);

    @Message(id = 15, value = "Could create HTTP EJBReceiver for protocol %s")
    IllegalArgumentException couldNotCreateHttpEjbReceiverFor(String s);
}
