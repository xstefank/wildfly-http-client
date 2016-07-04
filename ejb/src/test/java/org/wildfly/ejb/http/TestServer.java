package org.wildfly.ejb.http;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import io.undertow.Undertow;
import io.undertow.connector.ByteBufferPool;
import io.undertow.server.DefaultByteBufferPool;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.testutils.DebuggingSlicePool;
import io.undertow.testutils.DefaultServer;
import io.undertow.util.Headers;
import io.undertow.util.NetworkUtils;

/**
 * @author Stuart Douglas
 */
public class TestServer extends BlockJUnit4ClassRunner {

    public static final int BUFFER_SIZE = Integer.getInteger("test.bufferSize", 8192 * 3);
    public static final PathHandler PATH_HANDLER = new PathHandler();
    private static boolean first = true;
    private static Undertow undertow;

    private static XnioWorker worker;
    private static final MarshallerFactory marshallerFactory = new RiverMarshallerFactory();

    private static final DebuggingSlicePool pool = new DebuggingSlicePool(new DefaultByteBufferPool(true, BUFFER_SIZE, 1000, 10, 100));

    private static volatile TestEJBHandler handler;

    public static TestEJBHandler getHandler() {
        return handler;
    }

    public static void setHandler(TestEJBHandler handler) {
        TestServer.handler = handler;
    }

    /**
     * @return The base URL that can be used to make connections to this server
     */
    public static String getDefaultServerURL() {
        return getDefaultRootServerURL() + "/wildfly-services";
    }

    public static String getDefaultRootServerURL() {
        return "http://" + NetworkUtils.formatPossibleIpv6Address(getHostAddress()) + ":" + getHostPort();
    }

    public static InetSocketAddress getDefaultServerAddress() {
        return new InetSocketAddress(DefaultServer.getHostAddress("default"), DefaultServer.getHostPort("default"));
    }

    public TestServer(Class<?> klass) throws InitializationError {
        super(klass);
    }

    public static ByteBufferPool getBufferPool() {
        return pool;
    }

    @Override
    public Description getDescription() {
        return super.getDescription();
    }

    @Override
    public void run(final RunNotifier notifier) {
        runInternal(notifier);
        super.run(notifier);
    }

    public static XnioWorker getWorker() {
        return worker;
    }

    private static void runInternal(final RunNotifier notifier) {
        try {
            if (first) {
                first = false;
                Xnio xnio = Xnio.getInstance("nio");
                worker = xnio.createWorker(OptionMap.create(Options.WORKER_TASK_CORE_THREADS, 20, Options.WORKER_IO_THREADS, 10));
                undertow = Undertow.builder()
                        .addHttpListener(getHostPort(), getHostAddress())
                        .setHandler(PATH_HANDLER.addPrefixPath("/wildfly-services", new TestEjbHandler()))
                        .build();
                undertow.start();
                notifier.addListener(new RunListener() {
                    @Override
                    public void testRunFinished(final Result result) throws Exception {
                        undertow.stop();
                    }
                });
            }
        } catch (Exception e) {
            throw new RuntimeException();
        }
    }


    public static String getHostAddress() {
        return System.getProperty("server.address", "localhost");
    }

    public static int getHostPort() {
        return Integer.getInteger("server.port", 7788);
    }

    private static final class TestEjbHandler implements HttpHandler {

        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            if(exchange.isInIoThread()) {
                exchange.dispatch(this);
                return;
            }
            exchange.startBlocking();
            System.out.println(exchange.getRelativePath());
            String relativePath = exchange.getRelativePath();
            if(relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }
            String[] parts = relativePath.split("/");
            if(parts.length < 7) {
                sendException(exchange, 400, new RuntimeException("not enough URL segments " + relativePath));
                return;
            }

            String app = parts[0];
            String module = parts[1];
            String distict = parts[2];
            String bean = parts[3];
            String sessionID = parts[4];
            Class<?> view = Class.forName(parts[5]);
            String method = parts[6];
            Class[] paramTypes = new Class[parts.length - 7];
            for(int i = 7; i < parts.length; ++i) {
                paramTypes[i-7] = Class.forName(parts[i]);
            }
            Object[] params = new Object[paramTypes.length];


            final MarshallingConfiguration marshallingConfiguration = new MarshallingConfiguration();
            marshallingConfiguration.setClassTable(ProtocolV1ClassTable.INSTANCE);
            marshallingConfiguration.setObjectTable(ProtocolV1ObjectTable.INSTANCE);
            marshallingConfiguration.setVersion(2);
            Unmarshaller unmarshaller = marshallerFactory.createUnmarshaller(marshallingConfiguration);

            unmarshaller.start(new InputStreamByteInput(exchange.getInputStream()));
            for(int i = 0; i < paramTypes.length; ++ i) {
                params[i] = unmarshaller.readObject();
            }
            final Map<?, ?> privateAttachments;
            final Map<String, Object> contextData;
            int attachementCount = PackedInteger.readPackedInteger(unmarshaller);
            if(attachementCount > 0) {
                contextData = new HashMap<>();
                for(int i = 0; i < attachementCount - 1; ++i) {
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


            TestEJBInvocation invocation = new TestEJBInvocation(app, module, distict, bean, sessionID, view, method, paramTypes, params, privateAttachments, contextData);

            try {
                Object result = handler.handle(invocation);
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, EjbHeaders.EJB_RESPONSE_VERSION_ONE);

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
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, EjbHeaders.EJB_EXCEPTION_VERSION_ONE);

        final MarshallingConfiguration marshallingConfiguration = new MarshallingConfiguration();
        marshallingConfiguration.setClassTable(ProtocolV1ClassTable.INSTANCE);
        marshallingConfiguration.setObjectTable(ProtocolV1ObjectTable.INSTANCE);
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


}
