package org.wildfly.ejb.http;

import org.jboss.logging.Messages;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageBundle;

@MessageBundle(projectCode = "WFHTTP")
public interface HttpClientMessages {

    HttpClientMessages MESSAGES = Messages.getBundle(HttpClientMessages.class);

    @Message(id = 1, value = "Connection in wrong state")
    IllegalStateException connectionInWrongState();
}
