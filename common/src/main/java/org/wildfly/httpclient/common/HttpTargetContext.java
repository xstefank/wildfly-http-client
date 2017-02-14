package org.wildfly.httpclient.common;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.OutputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.marshalling.ByteOutput;
import org.jboss.marshalling.InputStreamByteInput;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.marshalling.river.RiverMarshallerFactory;
import org.xnio.channels.Channels;
import org.xnio.streams.ChannelInputStream;
import org.xnio.streams.ChannelOutputStream;
import io.undertow.client.ClientCallback;
import io.undertow.client.ClientExchange;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.server.handlers.Cookie;
import io.undertow.util.AbstractAttachable;
import io.undertow.util.Cookies;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import io.undertow.util.StatusCodes;

/**
 * @author Stuart Douglas
 */
public class HttpTargetContext extends AbstractAttachable {

    private static final String EXCEPTION_TYPE = "application/x-wf-jbmar-exception";

    private static final String JSESSIONID = "JSESSIONID";
    static final MarshallerFactory MARSHALLER_FACTORY = new RiverMarshallerFactory();

    private final HttpConnectionPool connectionPool;
    private final boolean eagerlyAcquireAffinity;
    private volatile CountDownLatch sessionAffinityLatch = new CountDownLatch(1);
    private volatile String sessionId;
    private final URI uri;

    private final AtomicBoolean affinityRequestSent = new AtomicBoolean();


    HttpTargetContext(HttpConnectionPool connectionPool, boolean eagerlyAcquireAffinity, URI uri) {
        this.connectionPool = connectionPool;
        this.eagerlyAcquireAffinity = eagerlyAcquireAffinity;
        this.uri = uri;
    }

    void init() {
        if (eagerlyAcquireAffinity) {
            acquireAffinitiy();
        }
    }

    private void acquireAffinitiy() {
        if (affinityRequestSent.compareAndSet(false, true)) {
            acquireSessionAffinity(sessionAffinityLatch);
        }
    }


    private void acquireSessionAffinity(CountDownLatch latch) {
        ClientRequest clientRequest = new ClientRequest();
        clientRequest.setMethod(Methods.GET);
        clientRequest.setPath(uri.getPath() + "/common/v1/affinity");

        sendRequest(clientRequest, null, null, (e) -> {
            latch.countDown();
            HttpClientMessages.MESSAGES.failedToAcquireSession(e);
        }, null, latch::countDown);
    }

    public Unmarshaller createUnmarshaller(MarshallingConfiguration marshallingConfiguration) throws IOException {
        return MARSHALLER_FACTORY.createUnmarshaller(marshallingConfiguration);
    }

    public Marshaller createMarshaller(MarshallingConfiguration marshallingConfiguration) throws IOException {
        return MARSHALLER_FACTORY.createMarshaller(marshallingConfiguration);
    }

    public void sendRequest( ClientRequest request, HttpMarshaller httpMarshaller, HttpResultHandler httpResultHandler, HttpFailureHandler failureHandler, ContentType expectedResponse, Runnable completedTask) {
        if (sessionId != null) {
            request.getRequestHeaders().add(Headers.COOKIE, "JSESSIONID=" + sessionId);
        }
        connectionPool.getConnection(connection -> sendRequestInternal(connection, request, httpMarshaller, httpResultHandler, failureHandler, expectedResponse, completedTask, false, false), failureHandler::handleFailure, false);
    }

    public void sendRequest(ClientRequest request, HttpMarshaller httpMarshaller, HttpResultHandler httpResultHandler, HttpFailureHandler failureHandler, ContentType expectedResponse, Runnable completedTask, boolean allowNoContent) {
        if (sessionId != null) {
            request.getRequestHeaders().add(Headers.COOKIE, "JSESSIONID=" + sessionId);
        }
        connectionPool.getConnection(connection -> sendRequestInternal(connection, request, httpMarshaller, httpResultHandler, failureHandler, expectedResponse, completedTask, allowNoContent, false), failureHandler::handleFailure, false);
    }

    public void sendRequest(final HttpConnectionPool.ConnectionHandle connection, ClientRequest request, HttpMarshaller httpMarshaller, HttpResultHandler httpResultHandler, HttpFailureHandler failureHandler, ContentType expectedResponse, Runnable completedTask) {
        if (sessionId != null) {
            request.getRequestHeaders().add(Headers.COOKIE, "JSESSIONID=" + sessionId);
        }
        sendRequestInternal(connection, request, httpMarshaller, httpResultHandler, failureHandler, expectedResponse, completedTask, false, false);
    }

    public void sendRequest(final HttpConnectionPool.ConnectionHandle connection, ClientRequest request, HttpMarshaller httpMarshaller, HttpResultHandler httpResultHandler, HttpFailureHandler failureHandler, ContentType expectedResponse, Runnable completedTask, boolean allowNoContent) {
        if (sessionId != null) {
            request.getRequestHeaders().add(Headers.COOKIE, "JSESSIONID=" + sessionId);
        }
        sendRequestInternal(connection, request, httpMarshaller, httpResultHandler, failureHandler, expectedResponse, completedTask, allowNoContent, false);
    }

    public void sendRequestInternal(final HttpConnectionPool.ConnectionHandle connection, ClientRequest request, HttpMarshaller httpMarshaller, HttpResultHandler httpResultHandler, HttpFailureHandler failureHandler, ContentType expectedResponse, Runnable completedTask, boolean allowNoContent, boolean retry) {

        final boolean authAdded = retry || connection.getAuthenticationContext().prepareRequest(connection.getUri(), request);
        request.getRequestHeaders().put(Headers.HOST, connection.getUri().getHost());
        if (request.getRequestHeaders().contains(Headers.CONTENT_TYPE)) {
            request.getRequestHeaders().put(Headers.TRANSFER_ENCODING, Headers.CHUNKED.toString());
        }
        connection.getConnection().sendRequest(request, new ClientCallback<ClientExchange>() {
            @Override
            public void completed(ClientExchange result) {
                result.setResponseListener(new ClientCallback<ClientExchange>() {
                    @Override
                    public void completed(ClientExchange result) {
                        connection.getConnection().getWorker().execute(() -> {
                            ClientResponse response = result.getResponse();
                            if (!authAdded) {
                                if (connection.getAuthenticationContext().handleResponse(response)) {
                                    try {
                                        Channels.drain(result.getResponseChannel(), Long.MAX_VALUE);
                                    } catch (IOException e) {
                                        failureHandler.handleFailure(e);
                                    }
                                    if (connection.getAuthenticationContext().prepareRequest(connection.getUri(), request)) {
                                        //retry the invocation
                                        sendRequestInternal(connection, request, httpMarshaller, httpResultHandler, failureHandler, expectedResponse, completedTask, allowNoContent, true);
                                        return;
                                    }
                                }
                            }

                            ContentType type = ContentType.parse(response.getResponseHeaders().getFirst(Headers.CONTENT_TYPE));
                            final boolean ok;
                            final boolean isException;
                            if (type == null) {
                                ok = expectedResponse == null || (allowNoContent && response.getResponseCode() == StatusCodes.NO_CONTENT);
                                isException = false;
                            } else {
                                if (type.getType().equals(EXCEPTION_TYPE)) {
                                    ok = true;
                                    isException = true;
                                } else if (expectedResponse == null) {
                                    ok = false;
                                    isException = false;
                                } else {
                                    ok = expectedResponse.getType().equals(type.getType()) && expectedResponse.getVersion() >= type.getVersion();
                                    isException = false;
                                }
                            }

                            if (!ok) {
                                if (response.getResponseCode() >= 400) {
                                    failureHandler.handleFailure(HttpClientMessages.MESSAGES.invalidResponseCode(response.getResponseCode(), response));
                                } else {
                                    failureHandler.handleFailure(HttpClientMessages.MESSAGES.invalidResponseType(type));
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

                                if (isException) {
                                    final MarshallingConfiguration marshallingConfiguration = createExceptionMarshallingConfig();
                                    final Unmarshaller unmarshaller = MARSHALLER_FACTORY.createUnmarshaller(marshallingConfiguration);
                                    try (ChannelInputStream inputStream = new ChannelInputStream(result.getResponseChannel())) {
                                        unmarshaller.start(new InputStreamByteInput(new BufferedInputStream(inputStream)));
                                        Exception exception = (Exception) unmarshaller.readObject();
                                        Map<String, Object> attachments = readAttachments(unmarshaller);
                                        if (unmarshaller.read() != -1) {
                                            HttpClientMessages.MESSAGES.debugf("Unexpected data when reading exception from %s", response);
                                            connection.done(true);
                                        } else {
                                            connection.done(false);
                                        }
                                        failureHandler.handleFailure(exception);
                                    }
                                } else if (response.getResponseCode() >= 400) {
                                    //unknown error
                                    failureHandler.handleFailure(HttpClientMessages.MESSAGES.invalidResponseCode(response.getResponseCode(), response));
                                    //close the connection to be safe
                                    connection.done(true);

                                } else {
                                    if (httpResultHandler != null) {
                                        if (response.getResponseCode() == StatusCodes.NO_CONTENT) {
                                            Channels.drain(result.getResponseChannel(), Long.MAX_VALUE);
                                            httpResultHandler.handleResult(null, response);
                                        } else {
                                            ChannelInputStream inputStream = new ChannelInputStream(result.getResponseChannel());
                                            httpResultHandler.handleResult(new BufferedInputStream(inputStream), response);
                                        }
                                    }
                                    Channels.drain(result.getResponseChannel(), Long.MAX_VALUE);
                                    if (completedTask != null) {
                                        completedTask.run();
                                    }
                                    connection.done(false);
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

                if (httpMarshaller != null) {
                    //marshalling is blocking, we need to delegate, otherwise we may need to buffer arbitrarily large requests
                    connection.getConnection().getWorker().execute(() -> {
                        try (OutputStream outputStream = new BufferedOutputStream(new ChannelOutputStream(result.getRequestChannel()))) {

                            // marshall the locator and method params
                            final ByteOutput byteOutput = Marshalling.createByteOutput(outputStream);
                            // start the marshaller
                            httpMarshaller.marshall(byteOutput);

                        } catch (Exception e) {
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

    /**
     * Exceptions don't use an object/class table, as they are common across protocols
     *
     * @return
     */
    MarshallingConfiguration createExceptionMarshallingConfig() {
        final MarshallingConfiguration marshallingConfiguration = new MarshallingConfiguration();
        marshallingConfiguration.setVersion(2);
        return marshallingConfiguration;
    }

    private static Map<String, Object> readAttachments(final ObjectInput input) throws IOException, ClassNotFoundException {
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

    public HttpConnectionPool getConnectionPool() {
        return connectionPool;
    }

    public String getSessionId() {
        return sessionId;
    }

    void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public URI getUri() {
        return uri;
    }

    public void clearSessionId() {
        synchronized (this) {
            CountDownLatch old = sessionAffinityLatch;
            sessionAffinityLatch = new CountDownLatch(1);
            old.countDown();
            this.affinityRequestSent.set(false);
            this.sessionId = null;
        }
    }

    public String awaitSessionId(boolean required) {
        if (required) {
            acquireAffinitiy();
        }
        if (affinityRequestSent.get()) {
            try {
                sessionAffinityLatch.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        return sessionId;
    }

    public interface HttpMarshaller {
        void marshall(ByteOutput output) throws Exception;
    }

    public interface HttpResultHandler {
        void handleResult(InputStream result, ClientResponse response);
    }

    public interface HttpFailureHandler {
        void handleFailure(Throwable throwable);
    }
}
