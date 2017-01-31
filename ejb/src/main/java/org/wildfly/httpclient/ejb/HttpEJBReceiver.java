package org.wildfly.httpclient.ejb;

import io.undertow.client.ClientRequest;
import io.undertow.util.AttachmentKey;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import org.jboss.ejb.client.Affinity;
import org.jboss.ejb.client.EJBClientInvocationContext;
import org.jboss.ejb.client.EJBLocator;
import org.jboss.ejb.client.EJBReceiver;
import org.jboss.ejb.client.EJBReceiverInvocationContext;
import org.jboss.ejb.client.SessionID;
import org.jboss.ejb.client.StatefulEJBLocator;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.ejb.client.URIAffinity;
import org.jboss.marshalling.ByteOutput;
import org.jboss.marshalling.InputStreamByteInput;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.Unmarshaller;
import org.wildfly.httpclient.common.HttpConnectionPool;
import org.wildfly.httpclient.common.HttpTargetContext;
import org.wildfly.httpclient.common.WildflyHttpContext;
import org.xnio.FutureResult;

import javax.ejb.Asynchronous;
import java.io.IOException;
import java.io.ObjectInput;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

/**
 * @author Stuart Douglas
 */
class HttpEJBReceiver extends EJBReceiver {

    private final AttachmentKey<EjbContextData> EJB_CONTEXT_DATA = AttachmentKey.create(EjbContextData.class);

    @Override
    protected void processInvocation(EJBReceiverInvocationContext receiverContext) throws Exception {

        EJBClientInvocationContext clientInvocationContext = receiverContext.getClientInvocationContext();
        EJBLocator locator = clientInvocationContext.getLocator();

        Affinity affinity = locator.getAffinity();
        URI uri;
        if (affinity instanceof URIAffinity) {
            uri = affinity.getUri();
        } else {
            throw EjbHttpClientMessages.MESSAGES.invalidAffinity(affinity);
        }

        WildflyHttpContext current = WildflyHttpContext.getCurrent();
        HttpTargetContext targetContext = current.getTargetContext(uri);
        if (targetContext == null) {
            throw EjbHttpClientMessages.MESSAGES.couldNotResolveTargetForLocator(locator);
        }
        if (targetContext.getAttachment(EJB_CONTEXT_DATA) == null) {
            synchronized (this) {
                if (targetContext.getAttachment(EJB_CONTEXT_DATA) == null) {
                    targetContext.putAttachment(EJB_CONTEXT_DATA, new EjbContextData());
                }
            }
        }
        targetContext.awaitSessionId(false);
        targetContext.getConnectionPool().getConnection((connection) -> invocationConnectionReady(clientInvocationContext, receiverContext, connection, targetContext), (e) -> receiverContext.resultReady(new StaticResultProducer(e, null)), false);
    }

    @Override
    protected <T> StatefulEJBLocator<T> createSession(StatelessEJBLocator<T> locator) throws Exception {

        Affinity affinity = locator.getAffinity();
        URI uri;
        if (affinity instanceof URIAffinity) {
            uri = affinity.getUri();
        } else {
            throw EjbHttpClientMessages.MESSAGES.invalidAffinity(affinity);
        }
        WildflyHttpContext current = WildflyHttpContext.getCurrent();
        HttpTargetContext targetContext = current.getTargetContext(uri);
        if (targetContext == null) {
            throw EjbHttpClientMessages.MESSAGES.couldNotResolveTargetForLocator(locator);
        }
        if (targetContext.getAttachment(EJB_CONTEXT_DATA) == null) {
            synchronized (this) {
                if (targetContext.getAttachment(EJB_CONTEXT_DATA) == null) {
                    targetContext.putAttachment(EJB_CONTEXT_DATA, new EjbContextData());
                }
            }
        }
        targetContext.awaitSessionId(true);
        FutureResult<StatefulEJBLocator<T>> result = new FutureResult<>();
        targetContext.getConnectionPool().getConnection((connection) -> openSessionConnectionReady(connection, result, locator, targetContext), (e) -> result.setException(new IOException(e)), false);
        try {
            return result.getIoFuture().get();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private <T> void openSessionConnectionReady(HttpConnectionPool.ConnectionHandle connection, FutureResult<StatefulEJBLocator<T>> result, StatelessEJBLocator<T> locator, HttpTargetContext targetContext) throws IllegalArgumentException {

        EJBInvocationBuilder builder = new EJBInvocationBuilder()
                .setInvocationType(EJBInvocationBuilder.InvocationType.STATEFUL_CREATE)
                .setAppName(locator.getAppName())
                .setModuleName(locator.getModuleName())
                .setDistinctName(locator.getDistinctName())
                .setView(locator.getViewType().getName())
                .setBeanName(locator.getBeanName());
        ClientRequest request = builder.createRequest(connection.getUri().getPath());
        targetContext.sendRequest(connection, request, null,
                ((unmarshaller, response) -> {
                    String sessionId = response.getResponseHeaders().getFirst(EjbHeaders.EJB_SESSION_ID);
                    if (sessionId == null) {
                        result.setException(EjbHttpClientMessages.MESSAGES.noSessionIdInResponse());
                        connection.done(true);
                    } else {
                        SessionID sessionID = SessionID.createSessionID(Base64.getDecoder().decode(sessionId));
                        result.setResult(new StatefulEJBLocator<T>(locator, sessionID));
                        connection.done(false);
                    }
                })
                , (e) -> result.setException(new IOException(e)), EjbHeaders.EJB_RESPONSE_NEW_SESSION, null);

    }

    private void invocationConnectionReady(EJBClientInvocationContext clientInvocationContext, EJBReceiverInvocationContext receiverContext, HttpConnectionPool.ConnectionHandle connection, HttpTargetContext targetContext) {
        EjbContextData ejbData = targetContext.getAttachment(EJB_CONTEXT_DATA);
        EJBLocator<?> locator = clientInvocationContext.getLocator();
        EJBInvocationBuilder builder = new EJBInvocationBuilder()
                .setInvocationType(EJBInvocationBuilder.InvocationType.METHOD_INVOCATION)
                .setMethod(clientInvocationContext.getInvokedMethod())
                .setAppName(locator.getAppName())
                .setModuleName(locator.getModuleName())
                .setDistinctName(locator.getDistinctName())
                .setView(clientInvocationContext.getViewClass().getName())
                .setBeanName(locator.getBeanName());
        if (locator instanceof StatefulEJBLocator) {
            builder.setBeanId(Base64.getEncoder().encodeToString(((StatefulEJBLocator) locator).getSessionId().getEncodedForm()));
        }
        if (clientInvocationContext.getInvokedMethod().getReturnType() == Future.class) {
            receiverContext.proceedAsynchronously();
        } else if (clientInvocationContext.getInvokedMethod().getReturnType() == void.class) {
            if (clientInvocationContext.getInvokedMethod().isAnnotationPresent(Asynchronous.class)) {
                receiverContext.proceedAsynchronously();
            } else if (ejbData.asyncMethods.contains(clientInvocationContext.getInvokedMethod())) {
                receiverContext.proceedAsynchronously();
            }
        }
        ClientRequest request = builder.createRequest(connection.getUri().getPath());
        request.getRequestHeaders().put(Headers.TRANSFER_ENCODING, "chunked");
        targetContext.sendRequest(connection, request, (marshaller -> {
                    marshalEJBRequest(marshaller, clientInvocationContext, targetContext);
                }),

                ((input, response) -> {
                    if (response.getResponseCode() == StatusCodes.ACCEPTED && clientInvocationContext.getInvokedMethod().getReturnType() == void.class) {
                        ejbData.asyncMethods.add(clientInvocationContext.getInvokedMethod());
                    }
                    Exception exception = null;
                    Object returned = null;
                    try {

                        final MarshallingConfiguration marshallingConfiguration = createMarshallingConfig();
                        final Unmarshaller unmarshaller = targetContext.createUnmarshaller(marshallingConfiguration);
                        unmarshaller.start(new InputStreamByteInput(input));
                        returned = unmarshaller.readObject();
                        // read the attachments
                        //TODO: do we need attachments?
                        final Map<String, Object> attachments = readAttachments(unmarshaller);
                        // finish unmarshalling
                        if (unmarshaller.read() != -1) {
                            exception = EjbHttpClientMessages.MESSAGES.unexpectedDataInResponse();
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
                (e) -> receiverContext.resultReady(new StaticResultProducer(e instanceof Exception ? (Exception) e : new RuntimeException(e), null)), EjbHeaders.EJB_RESPONSE_VERSION_ONE, null);

    }

    private MarshallingConfiguration createMarshallingConfig() {
        final MarshallingConfiguration marshallingConfiguration = new MarshallingConfiguration();
        marshallingConfiguration.setClassTable(ProtocolV1ClassTable.INSTANCE);
        marshallingConfiguration.setObjectTable(ProtocolV1ObjectTable.INSTANCE);
        marshallingConfiguration.setVersion(2);
        return marshallingConfiguration;
    }

    private void marshalEJBRequest(ByteOutput byteOutput, EJBClientInvocationContext clientInvocationContext, HttpTargetContext targetContext) throws IOException {

        MarshallingConfiguration config = createMarshallingConfig();
        Marshaller marshaller = targetContext.createMarshaller(config);
        marshaller.start(byteOutput);


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

    private static class EjbContextData {
        final Set<Method> asyncMethods = Collections.newSetFromMap(new ConcurrentHashMap());

    }
}
