package org.wildfly.httpclient.ejb;

import io.undertow.client.ClientCallback;
import io.undertow.client.ClientExchange;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.server.handlers.Cookie;
import io.undertow.util.Cookies;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import org.jboss.marshalling.ByteOutput;
import org.jboss.marshalling.InputStreamByteInput;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.marshalling.river.RiverMarshallerFactory;
import org.wildfly.httpclient.common.HttpConnectionPool;
import org.xnio.channels.Channels;
import org.xnio.streams.ChannelInputStream;
import org.xnio.streams.ChannelOutputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Stuart Douglas
 */
class EJBTargetContext {

    private static final String JSESSIONID = "JSESSIONID";
    static final MarshallerFactory MARSHALLER_FACTORY = new RiverMarshallerFactory();

    private final HttpConnectionPool connectionPool;
    private final Set<Method> asyncMethodMap = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final boolean eagerlyAcquireAffinity;
    private final CountDownLatch sessionAffinityLatch = new CountDownLatch(1);
    private volatile String sessionId;

    private final AtomicBoolean initialized = new AtomicBoolean();

    private long sessionIdAcquireTime;


    EJBTargetContext(HttpConnectionPool connectionPool, boolean eagerlyAcquireAffinity) {
        this.connectionPool = connectionPool;
        this.eagerlyAcquireAffinity = eagerlyAcquireAffinity;
    }

    void init() {
        if(initialized.compareAndSet(false, true)) {
            if(eagerlyAcquireAffinity) {
                acquireAffinitiy();
            }
        }
    }

    private void acquireAffinitiy() {

        connectionPool.getConnection(connection -> {
                    acquireSessionAffinity(connection, sessionAffinityLatch);
                },
                (t) -> {sessionAffinityLatch.countDown(); EjbHttpClientMessages.MESSAGES.failedToAcquireSession(t);}, false);
    }


    private void acquireSessionAffinity(HttpConnectionPool.ConnectionHandle connection, CountDownLatch latch) {
        EJBInvocationBuilder builder = new EJBInvocationBuilder()
                .setInvocationType(EJBInvocationBuilder.InvocationType.AFFINITY);
        sendRequest(connection, builder.createRequest(connection.getUri().getPath()), null, null, (e) -> {
            latch.countDown();
            EjbHttpClientMessages.MESSAGES.failedToAcquireSession(e);
        }, EjbHeaders.EJB_RESPONSE_AFFINITY_RESULT_VERSION_ONE, EjbHeaders.EJB_RESPONSE_EXCEPTION_VERSION_ONE, latch::countDown);
    }

    void sendRequest(final HttpConnectionPool.ConnectionHandle connection, ClientRequest request, EjbMarshaller ejbMarshaller, EjbResultHandler ejbResultHandler, EjbFailureHandler failureHandler, String expectedResponse, String exceptionType, Runnable completedTask) {

        connection.getConnection().sendRequest(request, new ClientCallback<ClientExchange>() {
            @Override
            public void completed(ClientExchange result) {


                result.setResponseListener(new ClientCallback<ClientExchange>() {
                    @Override
                    public void completed(ClientExchange result) {
                        connection.getConnection().getWorker().execute(() -> {
                            //TODO: this all needs to be done properly
                            ClientResponse response = result.getResponse();
                            String type = response.getResponseHeaders().getFirst(Headers.CONTENT_TYPE);
                            //TODO: proper comparison, there may be spaces
                            if (type == null || !(type.equals(expectedResponse) || type.equals(exceptionType))) {
                                if (response.getResponseCode() >= 400) {
                                    failureHandler.handleFailure(EjbHttpClientMessages.MESSAGES.invalidResponseCode(response.getResponseCode(), response));
                                } else {
                                    failureHandler.handleFailure(EjbHttpClientMessages.MESSAGES.invalidResponseType(type));
                                }
                                //close the connection to be safe
                                connection.done(true);
                                return;
                            }
                            try {
                                //handle session affinity
                                HeaderValues cookies = response.getResponseHeaders().get(Headers.SET_COOKIE);
                                if (cookies != null) {
                                    for (String cookie : cookies) {
                                        Cookie c = Cookies.parseSetCookieHeader(cookie);
                                        if (c.getName().equals(JSESSIONID)) {
                                            setSessionId(c.getValue());
                                        }
                                    }
                                }

                                if (type.equals(exceptionType)) {
                                    final MarshallingConfiguration marshallingConfiguration = createMarshallingConfig();
                                    final Unmarshaller unmarshaller = MARSHALLER_FACTORY.createUnmarshaller(marshallingConfiguration);
                                    unmarshaller.start(new InputStreamByteInput(new BufferedInputStream(new ChannelInputStream(result.getResponseChannel()))));
                                    Exception exception = (Exception) unmarshaller.readObject();
                                    Map<String, Object> attachments = readAttachments(unmarshaller);
                                    if (unmarshaller.read() != -1) {
                                        EjbHttpClientMessages.MESSAGES.debugf("Unexpected data when reading exception from %s", response);
                                        connection.done(true);
                                    } else {
                                        connection.done(false);
                                    }
                                    failureHandler.handleFailure(exception);
                                    return;
                                } else if (response.getResponseCode() >= 400) {
                                    //unknown error

                                    failureHandler.handleFailure(EjbHttpClientMessages.MESSAGES.invalidResponseCode(response.getResponseCode(), response));
                                    //close the connection to be safe
                                    connection.done(true);

                                } else {
                                    if (ejbResultHandler != null) {
                                        if (response.getResponseCode() == StatusCodes.NO_CONTENT) {
                                            Channels.drain(result.getResponseChannel(), Long.MAX_VALUE);
                                            ejbResultHandler.handleResult(null, response);
                                        } else {
                                            ejbResultHandler.handleResult(new BufferedInputStream(new ChannelInputStream(result.getResponseChannel())), response);
                                        }
                                    }
                                    if (completedTask != null) {
                                        completedTask.run();
                                    }
                                }

                            } catch (Exception e) {
                                connection.done(true);
                                failureHandler.handleFailure(e);
                            }
                        });
                    }

                    @Override
                    public void failed(IOException e) {
                        connection.done(true);
                        failureHandler.handleFailure(e);
                    }
                });

                if (ejbMarshaller != null) {
                    //marshalling is blocking, we need to delegate, otherwise we may need to buffer arbitrarily large requests
                    connection.getConnection().getWorker().execute(() -> {
                        try (OutputStream outputStream = new BufferedOutputStream(new ChannelOutputStream(result.getRequestChannel()))) {

                            // marshall the locator and method params
                            final MarshallingConfiguration marshallingConfiguration = createMarshallingConfig();
                            final Marshaller marshaller = MARSHALLER_FACTORY.createMarshaller(marshallingConfiguration);
                            final ByteOutput byteOutput = Marshalling.createByteOutput(outputStream);
                            // start the marshaller
                            marshaller.start(byteOutput);
                            ejbMarshaller.marshall(marshaller);

                        } catch (IOException e) {
                            connection.done(true);
                            failureHandler.handleFailure(e);
                        }
                    });
                }
            }

            @Override
            public void failed(IOException e) {
                connection.done(true);
                failureHandler.handleFailure(e);
            }
        });
    }

    MarshallingConfiguration createMarshallingConfig() {
        final MarshallingConfiguration marshallingConfiguration = new MarshallingConfiguration();
        marshallingConfiguration.setClassTable(ProtocolV1ClassTable.INSTANCE);
        marshallingConfiguration.setObjectTable(ProtocolV1ObjectTable.INSTANCE);
        marshallingConfiguration.setVersion(2);
        return marshallingConfiguration;
    }

    static Map<String, Object> readAttachments(final ObjectInput input) throws IOException, ClassNotFoundException {
        final int numAttachments = input.readByte();
        if (numAttachments == 0) {
            return null;
        }
        final Map<String, Object> attachments = new HashMap<>(numAttachments);
        for (int i = 0; i < numAttachments; i++) {
            // read the key
            final String key = (String) input.readObject();
            // read the attachment value
            final Object val = input.readObject();
            attachments.put(key, val);
        }
        return attachments;
    }

    HttpConnectionPool getConnectionPool() {
        return connectionPool;
    }

    String getSessionId() {
        return sessionId;
    }

    void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    Set<Method> getAsyncMethodMap() {
        return asyncMethodMap;
    }


    interface EjbMarshaller {
        void marshall(Marshaller marshaller) throws IOException;
    }

    interface EjbResultHandler {
        void handleResult(InputStream result, ClientResponse response);
    }

    interface EjbFailureHandler {
        void handleFailure(Throwable throwable);
    }
}
