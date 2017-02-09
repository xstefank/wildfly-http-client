package org.wildfly.httpclient.naming;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Hashtable;
import java.util.Objects;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;

import io.undertow.util.StatusCodes;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.OutputStreamByteOutput;
import org.jboss.marshalling.river.RiverMarshallerFactory;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.httpclient.common.HTTPTestServer;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.CookieImpl;
import io.undertow.util.Headers;

/**
 * @author Stuart Douglas
 */
@RunWith(HTTPTestServer.class)
public class SimpleNamingOperationTestCase {

    private final MarshallerFactory marshallerFactory = new RiverMarshallerFactory();

    @Test
    public void testJNDIlookup() throws NamingException {

        HTTPTestServer.registerServicesHandler("common/v1/affinity", exchange -> exchange.getResponseCookies().put("JSESSIONID", new CookieImpl("JSESSIONID", "foo")));
        HTTPTestServer.registerServicesHandler("naming/v1/lookup", new BlockingHandler(exchange -> {
            String name = exchange.getRelativePath();
            if (name.startsWith("/")) {
                name = name.substring(1);
            }
            if (name.equals("missing")) {
                HTTPTestServer.sendException(exchange, 500, new NameNotFoundException());
            } else if (name.equals("comp")) {
                exchange.setStatusCode(StatusCodes.NO_CONTENT);
            } else {
                String result = "JNDI:" + URLDecoder.decode(name, StandardCharsets.UTF_8.displayName());
                doMarshall(exchange, result);
            }
        }));


        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "org.wildfly.naming.client.WildFlyInitialContextFactory");
        env.put(Context.PROVIDER_URL, HTTPTestServer.getDefaultServerURL());
        InitialContext ic = new InitialContext(env);
        Object result = ic.lookup("test");
        Assert.assertEquals("JNDI:test", result);
        result = ic.lookup("comp/UserTransaction");
        Assert.assertEquals("JNDI:comp/UserTransaction", result);
        try {
            ic.lookup("missing");
            Assert.fail();
        } catch (NameNotFoundException expected) {}

    }

    private void doMarshall(HttpServerExchange exchange, String result) throws IOException {
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/x-wf-jndi-jbmar-value;version=1");
        final MarshallingConfiguration marshallingConfiguration = new MarshallingConfiguration();
        marshallingConfiguration.setVersion(2);
        Marshaller marshaller = marshallerFactory.createMarshaller(marshallingConfiguration);
        marshaller.start(new OutputStreamByteOutput(exchange.getOutputStream()));
        marshaller.writeObject(result);
        marshaller.finish();
    }
}
