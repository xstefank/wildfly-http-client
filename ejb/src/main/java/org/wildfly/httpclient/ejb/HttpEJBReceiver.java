package org.wildfly.httpclient.ejb;

import io.undertow.client.ClientRequest;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import org.jboss.ejb.client.EJBClientInvocationContext;
import org.jboss.ejb.client.EJBLocator;
import org.jboss.ejb.client.EJBReceiver;
import org.jboss.ejb.client.EJBReceiverInvocationContext;
import org.jboss.ejb.client.StatefulEJBLocator;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.marshalling.InputStreamByteInput;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.Unmarshaller;
import org.wildfly.httpclient.common.HttpConnectionPool;

import javax.ejb.Asynchronous;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * @author Stuart Douglas
 */
class HttpEJBReceiver extends EJBReceiver {

    @Override
    protected void processInvocation(EJBReceiverInvocationContext receiverContext) throws Exception {

        EJBClientInvocationContext clientInvocationContext = receiverContext.getClientInvocationContext();
        EJBLocator locator = clientInvocationContext.getLocator();
        EJBHttpContext current = EJBHttpContext.getCurrent();
        EJBTargetContext targetContext = current.getEJBTargetContext(locator);
        if (targetContext == null) {
            throw EjbHttpClientMessages.MESSAGES.couldNotResolveTargetForLocator(locator);
        }
        targetContext.init();
        targetContext.getConnectionPool().getConnection((connection) -> invocationConnectionReady(clientInvocationContext, receiverContext, connection, targetContext), (e) -> receiverContext.resultReady(new StaticResultProducer(e, null)), false);
    }

    @Override
    protected <T> StatefulEJBLocator<T> createSession(StatelessEJBLocator<T> statelessEJBLocator) throws Exception {
        return null;
    }


    private void invocationConnectionReady(EJBClientInvocationContext clientInvocationContext, EJBReceiverInvocationContext receiverContext, HttpConnectionPool.ConnectionHandle connection, EJBTargetContext ejbTargetContext) {

        EJBLocator<?> locator = clientInvocationContext.getLocator();
        EJBInvocationBuilder builder = new EJBInvocationBuilder()
                .setInvocationType(EJBInvocationBuilder.InvocationType.METHOD_INVOCATION)
                .setMethod(clientInvocationContext.getInvokedMethod())
                .setAppName(locator.getAppName())
                .setModuleName(locator.getModuleName())
                .setDistinctName(locator.getDistinctName())
                .setView(clientInvocationContext.getViewClass().getName())
                .setSessionId(ejbTargetContext.getSessionId())
                .setBeanName(locator.getBeanName());
        if (locator instanceof StatefulEJBLocator) {
            builder.setBeanId(Base64.getEncoder().encodeToString(((StatefulEJBLocator) locator).getSessionId().getEncodedForm()));
        }
        if (clientInvocationContext.getInvokedMethod().getReturnType() == Future.class) {
            receiverContext.proceedAsynchronously();
        } else if (clientInvocationContext.getInvokedMethod().getReturnType() == void.class) {
            if (clientInvocationContext.getInvokedMethod().isAnnotationPresent(Asynchronous.class)) {
                receiverContext.proceedAsynchronously();
            } else if (ejbTargetContext.getAsyncMethodMap().contains(clientInvocationContext.getInvokedMethod())) {
                receiverContext.proceedAsynchronously();
            }
        }
        ClientRequest request = builder.createRequest(connection.getUri().getPath());
        request.getRequestHeaders().put(Headers.TRANSFER_ENCODING, "chunked");
        ejbTargetContext.sendRequest(connection, request, (marshaller -> {
                    marshalEJBRequest(marshaller, clientInvocationContext);
                }),

                ((input, response) -> {
                    if (response.getResponseCode() == StatusCodes.ACCEPTED && clientInvocationContext.getInvokedMethod().getReturnType() == void.class) {
                        ejbTargetContext.getAsyncMethodMap().add(clientInvocationContext.getInvokedMethod());
                    }
                    Exception exception = null;
                    Object returned = null;
                    try {

                        final MarshallingConfiguration marshallingConfiguration = ejbTargetContext.createMarshallingConfig();
                        final Unmarshaller unmarshaller = EJBTargetContext.MARSHALLER_FACTORY.createUnmarshaller(marshallingConfiguration);
                        unmarshaller.start(new InputStreamByteInput(input));
                        returned = unmarshaller.readObject();
                        // read the attachments
                        //TODO: do we need attachments?
                        final Map<String, Object> attachments = EJBTargetContext.readAttachments(unmarshaller);
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
                (e) -> receiverContext.resultReady(new StaticResultProducer(e instanceof Exception ? (Exception) e : new RuntimeException(e), null)), EjbHeaders.EJB_RESPONSE_VERSION_ONE, EjbHeaders.EJB_RESPONSE_EXCEPTION_VERSION_ONE, null);

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
