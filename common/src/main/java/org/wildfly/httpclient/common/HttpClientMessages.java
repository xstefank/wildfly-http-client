package org.wildfly.httpclient.common;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

@MessageLogger(projectCode = "WFHTTP")
interface HttpClientMessages extends BasicLogger {

    HttpClientMessages MESSAGES = Logger.getMessageLogger(HttpClientMessages.class, HttpClientMessages.class.getPackage().getName());

    @Message(id = 1, value = "Connection in wrong state")
    IllegalStateException connectionInWrongState();
}
