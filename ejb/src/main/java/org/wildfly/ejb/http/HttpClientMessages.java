package org.wildfly.ejb.http;

import java.io.IOException;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

@MessageLogger(projectCode = "WFHTTP")
interface HttpClientMessages extends BasicLogger {

    HttpClientMessages MESSAGES = Logger.getMessageLogger(HttpClientMessages.class, HttpClientMessages.class.getPackage().getName());

    @Message(id = 1, value = "Connection in wrong state")
    IllegalStateException connectionInWrongState();

    @Message(id = 2, value = "Invalid response type %s")
    IOException invalidResponseType(String type);

    @Message(id = 3, value = "Session open timed out")
    RuntimeException sessionOpenTimedOut();

    @Message(id = 4, value = "Unexpected data in response")
    IOException unexpectedDataInResponse();

    @Message(id = 5, value = "Failed to acquire session")
    @LogMessage(level = Logger.Level.ERROR)
    void failedToAcquireSession(@Cause Throwable t);
}
