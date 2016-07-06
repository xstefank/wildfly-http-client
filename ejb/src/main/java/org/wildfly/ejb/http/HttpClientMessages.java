package org.wildfly.ejb.http;

import java.io.IOException;

import org.jboss.logging.Messages;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageBundle;

@MessageBundle(projectCode = "WFHTTP")
public interface HttpClientMessages {

    HttpClientMessages MESSAGES = Messages.getBundle(HttpClientMessages.class);

    @Message(id = 1, value = "Connection in wrong state")
    IllegalStateException connectionInWrongState();

    @Message(id = 2, value = "Invalid response type %s")
    IOException invalidResponseType(String type);

    @Message(id = 3, value = "Session open timed out")
    RuntimeException sessionOpenTimedOut();

    @Message(id = 4, value = "Unexpected data in response")
    IOException unexpectedDataInResponse();
}
