package org.wildfly.ejb.http;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.ObjectInput;
import java.io.OutputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.ejb.client.AttachmentKey;
import org.jboss.ejb.client.EJBClientConfiguration;
import org.jboss.ejb.client.EJBClientInvocationContext;
import org.jboss.ejb.client.EJBLocator;
import org.jboss.ejb.client.EJBReceiver;
import org.jboss.ejb.client.EJBReceiverContext;
import org.jboss.ejb.client.EJBReceiverInvocationContext;
import org.jboss.ejb.client.StatefulEJBLocator;
import org.jboss.marshalling.ByteOutput;
import org.jboss.marshalling.InputStreamByteInput;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.Unmarshaller;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.XnioWorker;
import org.xnio.ssl.XnioSsl;
import org.xnio.streams.ChannelInputStream;
import org.xnio.streams.ChannelOutputStream;
import io.undertow.client.ClientCallback;
import io.undertow.client.ClientExchange;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.connector.ByteBufferPool;
import io.undertow.server.handlers.Cookie;
import io.undertow.util.Cookies;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;

/**
 * @author Stuart Douglas
 */
public class HttpEJBReceiver extends EJBReceiver {

    private static final String JSESSIONID = "JSESSIONID"; //TODO: make configurable
    private final AttachmentKey<HttpConnectionPool> poolAttachmentKey = new AttachmentKey<>();
    private final AttachmentKey<AtomicReference<String>> sessionIdAttachmentKey = new AttachmentKey<>();
    private final AttachmentKey<CountDownLatch> sessionIdReadyLatchAttachmentKey = new AttachmentKey<>();

    private final URI uri;
    private final XnioWorker worker;
    private final ByteBufferPool bufferPool;
    private final XnioSsl ssl;
    private final OptionMap options;
    private final MarshallerFactory marshallerFactory;
    private final boolean eagerlyAcquireSession;
    private final List<ModuleID> modules;

    public HttpEJBReceiver(String nodeName, URI uri, XnioWorker worker, ByteBufferPool bufferPool, XnioSsl ssl, OptionMap options, MarshallerFactory marshallerFactory, boolean eagerlyAcquireSession, ModuleID... moduleIDs) {
        super(nodeName);
        this.uri = uri;
        this.worker = worker;
        this.bufferPool = bufferPool;
        this.ssl = ssl;
        this.options = options;
        this.marshallerFactory = marshallerFactory;
        this.eagerlyAcquireSession = eagerlyAcquireSession;
        this.modules = Arrays.asList(moduleIDs);
        for (ModuleID module : moduleIDs) {
            registerModule(module.app, module.module, module.distinct);
        }
    }

    @Override
    protected void associate(EJBReceiverContext context) {
        //TODO: fix
        HttpConnectionPool pool = new HttpConnectionPool(10, 10, worker, bufferPool, ssl, options, new HostPool(Collections.singletonList(uri)), 0);
        AtomicReference<String> sessionIdReference = new AtomicReference<>();
        context.putAttachment(this.poolAttachmentKey, pool);
        context.putAttachment(this.sessionIdAttachmentKey, sessionIdReference);
        if (eagerlyAcquireSession) {
            CountDownLatch latch = new CountDownLatch(1);
            context.putAttachment(this.sessionIdReadyLatchAttachmentKey, latch);
            pool.getConnection(connection -> {
                        acquireSessionAffinity(connection, latch, sessionIdReference);
                    },
                    HttpClientMessages.MESSAGES::failedToAcquireSession, false);
        }
    }

    private void acquireSessionAffinity(HttpConnectionPool.ConnectionHandle connection, CountDownLatch latch, AtomicReference<String> sessionIdReference) {
        EjbInvocationBuilder builder = new EjbInvocationBuilder()
                .setInvocationType(EjbInvocationBuilder.InvocationType.AFFINITY);
        sendRequest(connection, builder.createRequest(uri.getPath()), null, null, (e) -> {
            latch.countDown();
            HttpClientMessages.MESSAGES.failedToAcquireSession(e);
        }, EjbHeaders.EJB_RESPONSE_AFFINITY_RESULT_VERSION_ONE, sessionIdReference, latch::countDown);
    }

    @Override
    protected void disassociate(EJBReceiverContext context) {
        HttpConnectionPool pool = context.getAttachment(this.poolAttachmentKey);
        context.removeAttachment(this.poolAttachmentKey);
        context.removeAttachment(this.sessionIdAttachmentKey);
        IoUtils.safeClose(pool);
    }

    @Override
    protected void processInvocation(EJBClientInvocationContext clientInvocationContext, EJBReceiverInvocationContext receiverContext) throws Exception {
        awaitInitialAffinity(receiverContext.getEjbReceiverContext());
        HttpConnectionPool pool = receiverContext.getEjbReceiverContext().getAttachment(this.poolAttachmentKey);
        final AtomicReference<String> sessionAffinity = receiverContext.getEjbReceiverContext().getAttachment(sessionIdAttachmentKey);
        pool.getConnection((connection) -> invocationConnectionReady(clientInvocationContext, receiverContext, connection, sessionAffinity), (e) -> receiverContext.resultReady(new StaticResultProducer(e, null)), false);

    }


    private void invocationConnectionReady(EJBClientInvocationContext clientInvocationContext, EJBReceiverInvocationContext receiverContext, HttpConnectionPool.ConnectionHandle connection, AtomicReference<String> sessionAffinityCookie) {

        EJBLocator<?> locator = clientInvocationContext.getLocator();
        EjbInvocationBuilder builder = new EjbInvocationBuilder()
                .setInvocationType(EjbInvocationBuilder.InvocationType.METHOD_INVOCATION)
                .setMethod(clientInvocationContext.getInvokedMethod())
                .setAppName(locator.getAppName())
                .setModuleName(locator.getModuleName())
                .setDistinctName(locator.getDistinctName())
                .setView(clientInvocationContext.getViewClass().getName())
                .setSessionId(sessionAffinityCookie.get())
                .setBeanName(locator.getBeanName());
        if (locator instanceof StatefulEJBLocator) {
            builder.setBeanId(Base64.getEncoder().encodeToString(((StatefulEJBLocator) locator).getSessionId().getEncodedForm()));
        }
        ClientRequest request = builder.createRequest(uri.getPath());
        request.getRequestHeaders().put(Headers.TRANSFER_ENCODING, "chunked");
        sendRequest(connection, request, (marshaller -> {
                    marshalEJBRequest(marshaller, clientInvocationContext);
                }),

                ((unmarshaller, response) -> {
                    Exception exception = null;
                    Object returned = null;
                    try {
                        returned = unmarshaller.readObject();
                        // read the attachments
                        //TODO: do we need attachments?
                        final Map<String, Object> attachments = readAttachments(unmarshaller);
                        // finish unmarshalling
                        if (unmarshaller.read() != -1) {
                            exception = HttpClientMessages.MESSAGES.unexpectedDataInResponse();
                        }
                        unmarshaller.finish();
                        connection.done(false);

                        if (response.getResponseCode() >= 400) {
                            receiverContext.resultReady(new StaticResultProducer((Exception) returned, null));
                            return;
                        }
                    } catch (Exception e) {
                        exception = e;
                    }
                    final Object ret = returned;
                    final Exception ex = exception;
                    receiverContext.resultReady(new StaticResultProducer(ex, ret));
                }),
                (e) -> receiverContext.resultReady(new StaticResultProducer(e instanceof Exception ? (Exception) e : new RuntimeException(e), null)), EjbHeaders.EJB_RESPONSE_VERSION_ONE, sessionAffinityCookie, null);

    }

    @Override
    protected <T> StatefulEJBLocator<T> openSession(EJBReceiverContext context, Class<T> viewType, String appName, String moduleName, String distinctName, String beanName) throws IllegalArgumentException {
        try {
            awaitInitialAffinity(context);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        HttpConnectionPool pool = context.getAttachment(this.poolAttachmentKey);
        FutureResult<StatefulEJBLocator<T>> result = new FutureResult<>();
        final AtomicReference<String> sessionAffinity = context.getAttachment(sessionIdAttachmentKey);

        pool.getConnection((connection) -> openSessionConnectionReady(connection, result, viewType, appName, moduleName, distinctName, beanName, sessionAffinity), (e) -> {
            result.setException(new IOException(e));
        }, false);

        final EJBClientConfiguration ejbClientConfiguration = context.getClientContext().getEJBClientConfiguration();
        final long invocationTimeout = ejbClientConfiguration == null ? 0 : ejbClientConfiguration.getInvocationTimeout();
        if (invocationTimeout > 0) {
            IoFuture.Status await = result.getIoFuture().await(invocationTimeout, TimeUnit.MILLISECONDS);
            if (await != IoFuture.Status.DONE && await != IoFuture.Status.FAILED) {
                result.setCancelled();
                throw HttpClientMessages.MESSAGES.sessionOpenTimedOut();
            }
        }
        try {
            return result.getIoFuture().get();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private <T> void openSessionConnectionReady(HttpConnectionPool.ConnectionHandle connection, FutureResult<StatefulEJBLocator<T>> result, Class<T> viewType, String appName, String moduleName, String distinctName, String beanName, final AtomicReference<String> sessionAffinity) throws IllegalArgumentException {

        EjbInvocationBuilder builder = new EjbInvocationBuilder()
                .setInvocationType(EjbInvocationBuilder.InvocationType.STATEFUL_CREATE)
                .setAppName(appName)
                .setModuleName(moduleName)
                .setDistinctName(distinctName)
                .setView(viewType.getName())
                .setSessionId(sessionAffinity.get())
                .setBeanName(beanName);
        ClientRequest request = builder.createRequest(uri.getPath()); //TODO: FIX THIS
        sendRequest(connection, request, null,
                ((unmarshaller, response) -> {
                    Object returned = null;
                    try {
                        returned = unmarshaller.readObject();
                        if (unmarshaller.read() != -1) {
                            result.setException(HttpClientMessages.MESSAGES.unexpectedDataInResponse());
                        }
                        unmarshaller.finish();
                        connection.done(false);

                        if (response.getResponseCode() >= 400) {
                            result.setException(new IOException((Exception) returned));
                            return;
                        }
                    } catch (Exception e) {
                        result.setException(new IOException(e));
                    }
                    result.setResult((StatefulEJBLocator<T>) returned);
                })
                , (e) -> result.setException(new IOException(e)), EjbHeaders.EJB_RESPONSE_NEW_SESSION, sessionAffinity, null);

    }

    private void sendRequest(final HttpConnectionPool.ConnectionHandle connection, ClientRequest request, EjbMarshaller ejbMarshaller, EjbResultHandler ejbResultHandler, EjbFailureHandler failureHandler, String expectedResponse, final AtomicReference<String> sessionAffinity, Runnable completedTask) {

        connection.getConnection().sendRequest(request, new ClientCallback<ClientExchange>() {
            @Override
            public void completed(ClientExchange result) {

                final MarshallingConfiguration marshallingConfiguration = new MarshallingConfiguration();
                marshallingConfiguration.setClassTable(ProtocolV1ClassTable.INSTANCE);
                marshallingConfiguration.setObjectTable(ProtocolV1ObjectTable.INSTANCE);
                marshallingConfiguration.setVersion(2);

                result.setResponseListener(new ClientCallback<ClientExchange>() {
                    @Override
                    public void completed(ClientExchange result) {
                        worker.execute(() -> {
                            //TODO: this all needs to be done properly
                            ClientResponse response = result.getResponse();
                            String type = response.getResponseHeaders().getFirst(Headers.CONTENT_TYPE);
                            //TODO: proper comparison, there may be spaces
                            if (type == null || !(type.equals(expectedResponse) || type.equals(EjbHeaders.EJB_RESPONSE_EXCEPTION_VERSION_ONE))) {
                                failureHandler.handleFailure(HttpClientMessages.MESSAGES.invalidResponseType(type));
                                return;
                            }
                            try {
                                //handle session affinity
                                HeaderValues cookies = response.getResponseHeaders().get(Headers.SET_COOKIE);
                                if (cookies != null) {
                                    for (String cookie : cookies) {
                                        Cookie c = Cookies.parseSetCookieHeader(cookie);
                                        if (c.getName().equals(JSESSIONID)) {
                                            sessionAffinity.set(c.getValue());
                                        }
                                    }
                                }
                                if(ejbResultHandler != null) {
                                    final Unmarshaller unmarshaller = marshallerFactory.createUnmarshaller(marshallingConfiguration);
                                    unmarshaller.start(new InputStreamByteInput(new BufferedInputStream(new ChannelInputStream(result.getResponseChannel()))));
                                    ejbResultHandler.handleResult(unmarshaller, response);
                                }
                                if(completedTask != null) {
                                    completedTask.run();
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

                if(ejbMarshaller != null) {
                    //marshalling is blocking, we need to delegate, otherwise we may need to buffer arbitrarily large requests
                    connection.getConnection().getWorker().execute(() -> {
                        try (OutputStream outputStream = new BufferedOutputStream(new ChannelOutputStream(result.getRequestChannel()))) {

                            // marshall the locator and method params
                                final Marshaller marshaller = marshallerFactory.createMarshaller(marshallingConfiguration);
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

    private void marshalEJBRequest(Marshaller marshaller, EJBClientInvocationContext clientInvocationContext) throws IOException {


        Object[] methodParams = clientInvocationContext.getParameters();
        if (methodParams != null && methodParams.length > 0) {
            for (final Object methodParam : methodParams) {
                marshaller.writeObject(methodParam);
            }
        }
        // write out the attachments
        // we write out the private (a.k.a JBoss specific) attachments as well as public invocation context data
        // (a.k.a user application specific data)
        final Map<?, ?> privateAttachments = clientInvocationContext.getAttachments();
        final Map<String, Object> contextData = clientInvocationContext.getContextData();
        // no private or public data to write out
        if (contextData == null && privateAttachments.isEmpty()) {
            marshaller.writeByte(0);
        } else {
            // write the attachment count which is the sum of invocation context data + 1 (since we write
            // out the private attachments under a single key with the value being the entire attachment map)
            int totalAttachments = contextData.size();
            if (!privateAttachments.isEmpty()) {
                totalAttachments++;
            }
            PackedInteger.writePackedInteger(marshaller, totalAttachments);
            // write out public (application specific) context data
            for (Map.Entry<String, Object> invocationContextData : contextData.entrySet()) {
                marshaller.writeObject(invocationContextData.getKey());
                marshaller.writeObject(invocationContextData.getValue());
            }
            if (!privateAttachments.isEmpty()) {
                // now write out the JBoss specific attachments under a single key and the value will be the
                // entire map of JBoss specific attachments
                marshaller.writeObject(EJBClientInvocationContext.PRIVATE_ATTACHMENTS_KEY);
                marshaller.writeObject(privateAttachments);
            }
        }
        // finish marshalling
        marshaller.finish();
    }

    @Override
    protected boolean exists(String appName, String moduleName, String distinctName, String beanName) {
        //This method is not actually used
        return false;
    }

    private void awaitInitialAffinity(EJBReceiverContext context) throws IOException {

        CountDownLatch latch = context.getAttachment(this.sessionIdReadyLatchAttachmentKey);
        if(latch != null) {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.interrupted();
                throw new InterruptedIOException();
            }
        }
    }

    private static Map<String, Object> readAttachments(final ObjectInput input) throws IOException, ClassNotFoundException {
        final int numAttachments = input.readByte();
        if (numAttachments == 0) {
            return null;
        }
        final Map<String, Object> attachments = new HashMap<String, Object>(numAttachments);
        for (int i = 0; i < numAttachments; i++) {
            // read the key
            final String key = (String) input.readObject();
            // read the attachment value
            final Object val = input.readObject();
            attachments.put(key, val);
        }
        return attachments;
    }

    public static class ModuleID {
        final String app, module, distinct;

        public ModuleID(String app, String module, String distinct) {
            this.app = app;
            this.module = module;
            this.distinct = distinct;
        }

        public String getApp() {
            return app;
        }

        public String getModule() {
            return module;
        }

        public String getDistinct() {
            return distinct;
        }
    }

    private interface EjbMarshaller {
        void marshall(Marshaller marshaller) throws IOException;
    }

    private interface EjbResultHandler {
        void handleResult(Unmarshaller unmarshaller, ClientResponse response);
    }

    private interface EjbFailureHandler {
        void handleFailure(Throwable throwable);
    }

    private static class StaticResultProducer implements EJBReceiverInvocationContext.ResultProducer {
        private final Exception ex;
        private final Object ret;

        public StaticResultProducer(Exception ex, Object ret) {
            this.ex = ex;
            this.ret = ret;
        }

        @Override
        public Object getResult() throws Exception {
            if (ex != null) {
                throw ex;
            }
            return ret;
        }

        @Override
        public void discardResult() {

        }
    }
}
