package org.wildfly.httpclient.common;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.jboss.marshalling.ByteOutput;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.river.RiverMarshallerFactory;

import java.io.OutputStream;

/**
 * @author Stuart Douglas
 */
public class HttpServerHelper {

    public static final MarshallerFactory RIVER_MARSHALLER_FACTORY = new RiverMarshallerFactory();

    private HttpServerHelper() {

    }

    public static void sendException(HttpServerExchange exchange, int status, Exception e) {
        try {
            exchange.setStatusCode(status);
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/x-wf-jbmar-exception;version=1");
            final MarshallingConfiguration marshallingConfiguration = new MarshallingConfiguration();
            marshallingConfiguration.setVersion(2);
            final Marshaller marshaller = RIVER_MARSHALLER_FACTORY.createMarshaller(marshallingConfiguration);
            OutputStream outputStream = exchange.getOutputStream();
            final ByteOutput byteOutput = Marshalling.createByteOutput(outputStream);
            // start the marshaller
            marshaller.start(byteOutput);
            marshaller.writeObject(e);
            marshaller.write(0);
            marshaller.finish();
            marshaller.flush();
            exchange.endExchange();
        } catch (Exception ex) {
            HttpClientMessages.MESSAGES.failedToWriteException(ex);
            exchange.endExchange();
        }
    }
}
