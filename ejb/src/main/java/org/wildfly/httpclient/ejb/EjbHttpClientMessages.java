package org.wildfly.httpclient.ejb;

import org.jboss.ejb.client.Affinity;
import org.jboss.ejb.client.EJBLocator;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

import java.io.IOException;

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
}
