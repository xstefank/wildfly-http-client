package org.wildfly.httpclient.ejb;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jboss.marshalling.ByteOutput;
import org.jboss.marshalling.InputStreamByteInput;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.marshalling.river.RiverMarshallerFactory;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;
import org.wildfly.httpclient.common.HTTPTestServer;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import io.undertow.Undertow;
import io.undertow.connector.ByteBufferPool;
import io.undertow.server.DefaultByteBufferPool;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.CookieImpl;
import io.undertow.server.handlers.PathHandler;
import io.undertow.testutils.DebuggingSlicePool;
import io.undertow.testutils.DefaultServer;
import io.undertow.util.Headers;
import io.undertow.util.NetworkUtils;
import io.undertow.util.StatusCodes;

/**
 * @author Stuart Douglas
 */
public class EJBTestServer extends HTTPTestServer {

    private static final String JSESSIONID = "JSESSIONID";
    private static final MarshallerFactory marshallerFactory = new RiverMarshallerFactory();

    private static volatile TestEJBHandler handler;

    public EJBTestServer(Class<?> klass) throws InitializationError {
        super(klass);
    }

    public static TestEJBHandler getHandler() {
        return handler;
    }

    public static void setHandler(TestEJBHandler handler) {
        EJBTestServer.handler = handler;
    }

    @Override
    protected void registerPaths(PathHandler servicesHandler) {
        servicesHandler.addPrefixPath("/ejb", new PathHandler().addPrefixPath("v1", new TestEJBHTTPHandler()));

    }

    private static final class TestEJBHTTPHandler implements HttpHandler {

        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            if (exchange.isInIoThread()) {
                exchange.dispatch(this);
                return;
            }
            exchange.startBlocking();
            System.out.println(exchange.getRelativePath());
            String relativePath = exchange.getRelativePath();
            if (relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }
            String[] parts = relativePath.split("/");
            String content = exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE);
            switch (content) {
                case EjbHeaders.INVOCATION_VERSION_ONE:
                    handleInvocation(parts, exchange);
                    break;
                case EjbHeaders.SESSION_OPEN_VERSION_ONE:
                    handleSessionCreate(parts, exchange);
                    break;
                case EjbHeaders.AFFINITY_VERSION_ONE:
                    handleAffinity(parts, exchange);
                    break;
                default:
                    sendException(exchange, 400, new RuntimeException("Unknown content type " + content));
                    return;
            }

        }

        private void handleAffinity(String[] parts, HttpServerExchange exchange) {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, EjbHeaders.EJB_RESPONSE_AFFINITY_RESULT_VERSION_ONE);
            exchange.getResponseCookies().put("JSESSIONID", new CookieImpl("JSESSIONID", INITIAL_SESSION_AFFINITY).setPath(WILDFLY_SERVICES));
        }

        private void handleSessionCreate(String[] parts, HttpServerExchange exchange) throws Exception {

            if (parts.length < 4) {
                sendException(exchange, 400, new RuntimeException("not enough URL segments " + exchange.getRelativePath()));
                return;
            }

            String app = handleDash(parts[0]);
            String module = handleDash(parts[1]);
            String distict = handleDash(parts[2]);
            String bean = parts[3];

            final MarshallingConfiguration marshallingConfiguration = new MarshallingConfiguration();
            marshallingConfiguration.setClassTable(ProtocolV1ClassTable.INSTANCE);
            marshallingConfiguration.setObjectTable(ProtocolV1ObjectTable.INSTANCE);
            marshallingConfiguration.setVersion(2);

            Cookie sessionCookie = exchange.getRequestCookies().get(JSESSIONID);
            if(sessionCookie == null) {
                exchange.getResponseCookies().put(JSESSIONID, new CookieImpl(JSESSIONID, LAZY_SESSION_AFFINITY).setPath(WILDFLY_SERVICES));
            }

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, EjbHeaders.EJB_RESPONSE_NEW_SESSION);
            exchange.getResponseHeaders().put(EjbHeaders.EJB_SESSION_ID, Base64.getEncoder().encodeToString(SFSB_ID.getBytes(StandardCharsets.US_ASCII)));

            exchange.setStatusCode(StatusCodes.NO_CONTENT);

        }

        private void handleInvocation(String[] parts, HttpServerExchange exchange) throws Exception {

            if (parts.length < 7) {
                sendException(exchange, 400, new RuntimeException("not enough URL segments " + exchange.getRelativePath()));
                return;
            }

            String app = handleDash(parts[0]);
            String module = handleDash(parts[1]);
            String distict = handleDash(parts[2]);
            String bean = parts[3];
            String sessionID = parts[4];
            Class<?> view = Class.forName(parts[5]);
            String method = parts[6];
            Class[] paramTypes = new Class[parts.length - 7];
            for (int i = 7; i < parts.length; ++i) {
                paramTypes[i - 7] = Class.forName(parts[i]);
            }
            Object[] params = new Object[paramTypes.length];

            final MarshallingConfiguration marshallingConfiguration = new MarshallingConfiguration();
            marshallingConfiguration.setClassTable(ProtocolV1ClassTable.INSTANCE);
            marshallingConfiguration.setObjectTable(ProtocolV1ObjectTable.INSTANCE);
            marshallingConfiguration.setVersion(2);
            Unmarshaller unmarshaller = marshallerFactory.createUnmarshaller(marshallingConfiguration);

            unmarshaller.start(new InputStreamByteInput(exchange.getInputStream()));
            for (int i = 0; i < paramTypes.length; ++i) {
                params[i] = unmarshaller.readObject();
            }
            final Map<?, ?> privateAttachments;
            final Map<String, Object> contextData;
            int attachementCount = PackedInteger.readPackedInteger(unmarshaller);
            if (attachementCount > 0) {
                contextData = new HashMap<>();
                for (int i = 0; i < attachementCount - 1; ++i) {
                    String key = (String) unmarshaller.readObject();
                    Object value = unmarshaller.readObject();
                    contextData.put(key, value);
                }
                privateAttachments = (Map<?, ?>) unmarshaller.readObject();
            } else {
                contextData = Collections.emptyMap();
                privateAttachments = Collections.emptyMap();
            }

            unmarshaller.finish();
            Cookie cookie = exchange.getRequestCookies().get(JSESSIONID);
            String sessionAffinity = null;
            if (cookie != null) {
                sessionAffinity = cookie.getValue();
            }


            TestEJBInvocation invocation = new TestEJBInvocation(app, module, distict, bean, sessionID, sessionAffinity, view, method, paramTypes, params, privateAttachments, contextData);

            try {
                TestEjbOutput output = new TestEjbOutput();
                Object result = handler.handle(invocation, output);
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, EjbHeaders.EJB_RESPONSE_VERSION_ONE.toString());
                if (output.getSessionAffinity() != null) {
                    exchange.getResponseCookies().put("JSESSIONID", new CookieImpl("JSESSIONID", output.getSessionAffinity()).setPath(WILDFLY_SERVICES));
                }
                final Marshaller marshaller = marshallerFactory.createMarshaller(marshallingConfiguration);
                OutputStream outputStream = exchange.getOutputStream();
                final ByteOutput byteOutput = Marshalling.createByteOutput(outputStream);
                // start the marshaller
                marshaller.start(byteOutput);
                marshaller.writeObject(result);
                marshaller.write(0);
                marshaller.finish();
                marshaller.flush();

            } catch (Exception e) {
                sendException(exchange, 500, e);
            }

        }
    }

    private static void sendException(HttpServerExchange exchange, int status, Exception e) throws IOException {
        exchange.setStatusCode(status);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, EjbHeaders.RESPONSE_EXCEPTION_VERSION_ONE.toString());

        final MarshallingConfiguration marshallingConfiguration = new MarshallingConfiguration();
        marshallingConfiguration.setVersion(2);
        final Marshaller marshaller = marshallerFactory.createMarshaller(marshallingConfiguration);
        OutputStream outputStream = exchange.getOutputStream();
        final ByteOutput byteOutput = Marshalling.createByteOutput(outputStream);
        // start the marshaller
        marshaller.start(byteOutput);
        marshaller.writeObject(e);
        marshaller.write(0);
        marshaller.finish();
        marshaller.flush();
    }


    private static String handleDash(String s) {
        if(s.equals("-")) {
            return "";
        }
        return s;
    }
}
