package org.wildfly.ejb.http;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.OutputStream;
import java.net.URI;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jboss.ejb.client.AttachmentKey;
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
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.XnioWorker;
import org.xnio.ssl.XnioSsl;
import org.xnio.streams.ChannelInputStream;
import org.xnio.streams.ChannelOutputStream;
import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientExchange;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.connector.ByteBufferPool;
import io.undertow.util.Headers;

/**
 * @author Stuart Douglas
 */
public class HttpEJBReceiver extends EJBReceiver {

    private final AttachmentKey<HttpConnectionPool> pool = new AttachmentKey<>();
    private final URI uri;
    private final XnioWorker worker;
    private final ByteBufferPool bufferPool;
    private final XnioSsl ssl;
    private final OptionMap options;
    private final MarshallerFactory marshallerFactory;


    public HttpEJBReceiver(String nodeName, URI uri, XnioWorker worker, ByteBufferPool bufferPool, XnioSsl ssl, OptionMap options, MarshallerFactory marshallerFactory, ModuleID... moduleIDs) {
        super(nodeName);
        this.uri = uri;
        this.worker = worker;
        this.bufferPool = bufferPool;
        this.ssl = ssl;
        this.options = options;
        this.marshallerFactory = marshallerFactory;
        for (ModuleID module : moduleIDs) {
            registerModule(module.app, module.module, module.distinct);
        }
    }

    @Override
    protected void associate(EJBReceiverContext context) {
        //TODO: fix
        HttpConnectionPool pool = new HttpConnectionPool(10, 10, worker, bufferPool, ssl, options, new HostPool(Collections.singletonList(uri)));
        context.getClientContext().putAttachment(this.pool, pool);
    }

    @Override
    protected void disassociate(EJBReceiverContext context) {
        HttpConnectionPool pool = context.getClientContext().getAttachment(this.pool);
        IoUtils.safeClose(pool);
    }

    @Override
    protected void processInvocation(EJBClientInvocationContext clientInvocationContext, EJBReceiverInvocationContext receiverContext) throws Exception {
        HttpConnectionPool pool = clientInvocationContext.getClientContext().getAttachment(this.pool);
        pool.getConnection((connection) -> connectionReady(clientInvocationContext, receiverContext, connection), (e) -> invocationFailed(receiverContext, e), false);

    }

    private void invocationFailed(EJBReceiverInvocationContext receiverContext, final Exception e) {
        receiverContext.resultReady(new EJBReceiverInvocationContext.ResultProducer() {
            @Override
            public Object getResult() throws Exception {
                throw e;
            }

            @Override
            public void discardResult() {

            }
        });
    }

    private void connectionReady(EJBClientInvocationContext clientInvocationContext, EJBReceiverInvocationContext receiverContext, ClientConnection connection) {

        EJBLocator<?> locator = clientInvocationContext.getLocator();
        EjbInvocationBuilder builder = new EjbInvocationBuilder()
                .setInvocationType(EjbInvocationBuilder.InvocationType.METHOD_INVOCATION)
                .setMethod(clientInvocationContext.getInvokedMethod())
                .setAppName(locator.getAppName())
                .setModuleName(locator.getModuleName())
                .setDistinctName(locator.getDistinctName())
                .setView(clientInvocationContext.getViewClass().getName())
                .setBeanName(locator.getBeanName());
        if (locator instanceof StatefulEJBLocator) {
            builder.setBeanId(Base64.getEncoder().encodeToString(((StatefulEJBLocator) locator).getSessionId().getEncodedForm()));
        }
        ClientRequest request = builder.createRequest("/wildfly-services"); //TODO: FIX THIS
        request.getRequestHeaders().put(Headers.TRANSFER_ENCODING, "chunked");
        connection.sendRequest(request, new ClientCallback<ClientExchange>() {
            @Override
            public void completed(ClientExchange result) {
                //marshalling is blocking, we need to delegate, otherwise we may need to buffer arbitrarily large requests
                connection.getWorker().execute(() -> {
                    OutputStream outputStream = null;
                    try {
                        // marshall the locator and method params
                        final MarshallingConfiguration marshallingConfiguration = new MarshallingConfiguration();
                        marshallingConfiguration.setClassTable(ProtocolV1ClassTable.INSTANCE);
                        marshallingConfiguration.setObjectTable(ProtocolV1ObjectTable.INSTANCE);
                        marshallingConfiguration.setVersion(2);
                        final Marshaller marshaller = marshallerFactory.createMarshaller(marshallingConfiguration);
                        outputStream = new BufferedOutputStream(new ChannelOutputStream(result.getRequestChannel()));
                        final ByteOutput byteOutput = Marshalling.createByteOutput(outputStream);
                        // start the marshaller
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

                        result.setResponseListener(new ClientCallback<ClientExchange>() {
                            @Override
                            public void completed(ClientExchange result) {
                                worker.execute(() -> {

                                    //TODO: this all needs to be done properly
                                    ClientResponse response = result.getResponse();
                                    String type = response.getResponseHeaders().getFirst(Headers.CONTENT_TYPE);
                                    //TODO: proper comparison, there may be spaces
                                    if (type == null || !(type.equals(EjbHeaders.EJB_RESPONSE_VERSION_ONE) || type.equals(EjbHeaders.EJB_EXCEPTION_VERSION_ONE))) {
                                        invocationFailed(receiverContext, new IOException("invalid response type " + type));
                                        return;
                                    }
                                    try {
                                        final Unmarshaller unmarshaller = marshallerFactory.createUnmarshaller(marshallingConfiguration);
                                        unmarshaller.start(new InputStreamByteInput(new BufferedInputStream(new ChannelInputStream(result.getResponseChannel()))));
                                        Object returned = unmarshaller.readObject();
                                        // read the attachments
                                        //TODO: do we need attachments?
                                        final Map<String, Object> attachments = readAttachments(unmarshaller);

                                        // finish unmarshalling
                                        unmarshaller.finish();

                                        if (response.getResponseCode() >= 400) {


                                            invocationFailed(receiverContext, (Exception)returned);
                                            return;
                                        }
                                        receiverContext.resultReady(new EJBReceiverInvocationContext.ResultProducer() {
                                            @Override
                                            public Object getResult() throws Exception {
                                                return returned;
                                            }

                                            @Override
                                            public void discardResult() {

                                            }
                                        });

                                    } catch (Exception e) {
                                        invocationFailed(receiverContext, e);
                                    }
                                });

                            }

                            @Override
                            public void failed(IOException e) {
                                invocationFailed(receiverContext, e);
                            }
                        });
                    } catch (IOException e) {
                        invocationFailed(receiverContext, e);
                    } finally {
                        IoUtils.safeClose(outputStream);
                    }
                });
            }

            @Override
            public void failed(IOException e) {
                invocationFailed(receiverContext, e);
            }
        });

    }

    @Override
    protected <T> StatefulEJBLocator<T> openSession(EJBReceiverContext context, Class<T> viewType, String appName, String moduleName, String distinctName, String beanName) throws IllegalArgumentException {


        return null;
    }

    @Override
    protected boolean exists(String appName, String moduleName, String distinctName, String beanName) {
        //This method is not actually used
        return false;
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
}
