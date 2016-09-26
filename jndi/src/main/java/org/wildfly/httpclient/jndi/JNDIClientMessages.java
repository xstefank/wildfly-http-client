package org.wildfly.httpclient.jndi;

import javax.naming.InitialContext;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

@MessageLogger(projectCode = "JNDIWFHTTP")
interface JndiClientMessages extends BasicLogger {

    JndiClientMessages MESSAGES = Logger.getMessageLogger(JndiClientMessages.class, JndiClientMessages.class.getPackage().getName());

    @Message(id = 1, value = InitialContext.PROVIDER_URL + " must be specified")
    IllegalArgumentException providerUrlMustBeProvided();
}
