package org.wildfly.httpclient.ejb;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import javax.ejb.EJBHome;
import javax.ejb.NoSuchEJBException;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.xa.XAException;

import org.jboss.ejb.client.Affinity;
import org.jboss.ejb.client.EJBHomeLocator;
import org.jboss.ejb.client.EJBIdentifier;
import org.jboss.ejb.client.EJBLocator;
import org.jboss.ejb.client.EJBMethodLocator;
import org.jboss.ejb.client.SessionID;
import org.jboss.ejb.client.StatefulEJBLocator;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.ejb.server.Association;
import org.jboss.ejb.server.InvocationRequest;
import org.jboss.marshalling.ByteOutput;
import org.jboss.marshalling.InputStreamByteInput;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.Unmarshaller;
import org.wildfly.common.annotation.NotNull;
import org.wildfly.httpclient.common.ContentType;
import org.wildfly.httpclient.common.HttpServerHelper;
import org.wildfly.transaction.client.ImportResult;
import org.wildfly.transaction.client.LocalTransaction;
import org.wildfly.transaction.client.LocalTransactionContext;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.util.Headers;
import io.undertow.util.PathTemplateMatch;
import io.undertow.util.StatusCodes;

/**
 * @author Stuart Douglas
 */
class HttpInvocationHandler extends RemoteHTTPHandler {


    static String PATH = "/v1/invoke/{app}/{module}/{distinct}/{beanName}/{sfsbSessionId}/{viewClass}/{methodName}/*";
    private final Association association;
    private final ExecutorService executorService;
    private final LocalTransactionContext localTransactionContext;

    HttpInvocationHandler(Association association, ExecutorService executorService, LocalTransactionContext localTransactionContext) {
        super(executorService);
        this.association = association;
        this.executorService = executorService;
        this.localTransactionContext = localTransactionContext;
    }

    @Override
    protected void handleInternal(HttpServerExchange exchange) throws Exception {
        String ct = exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE);
        ContentType contentType = ContentType.parse(ct);
        if (contentType == null || contentType.getVersion() != 1 || !EjbHeaders.INVOCATION.equals(contentType.getType())) {
            exchange.setStatusCode(StatusCodes.BAD_REQUEST);
            EjbHttpClientMessages.MESSAGES.debugf("Bad content type %s", ct);
            return;
        }

        PathTemplateMatch match = exchange.getAttachment(PathTemplateMatch.ATTACHMENT_KEY);
        Map<String, String> params = match.getParameters();
        final String app = handleDash(params.get("app"));
        final String module = handleDash(params.get("module"));
        final String distinct = handleDash(params.get("distinct"));
        final String bean = params.get("beanName");


        String sessionID = handleDash(params.get("sfsbSessionId"));
        String viewName = params.get("viewClass");
        String method = params.get("methodName");
        String[] parameterTypeNames = params.get("*").split("/");
        Cookie cookie = exchange.getRequestCookies().get(EjbHttpService.JSESSIONID);
        final String sessionAffinity = cookie != null ? cookie.getValue() : null;
        final EJBIdentifier ejbIdentifier = new EJBIdentifier(app, module, bean, distinct);
        exchange.dispatch(executorService, () -> association.receiveInvocationRequest(new InvocationRequest() {

            @Override
            public SocketAddress getPeerAddress() {
                return exchange.getSourceAddress();
            }

            @Override
            public SocketAddress getLocalAddress() {
                return exchange.getDestinationAddress();
            }

            @Override
            public Resolved getRequestContent(ClassLoader classLoader) throws IOException, ClassNotFoundException {

                Object[] methodParams = new Object[parameterTypeNames.length];
                final Class<?> view = Class.forName(viewName, false, classLoader);
                final MarshallingConfiguration marshallingConfiguration = new MarshallingConfiguration();
                marshallingConfiguration.setClassTable(ProtocolV1ClassTable.INSTANCE);
                marshallingConfiguration.setObjectTable(ProtocolV1ObjectTable.INSTANCE);
                marshallingConfiguration.setVersion(2);
                Unmarshaller unmarshaller = HttpServerHelper.RIVER_MARSHALLER_FACTORY.createUnmarshaller(marshallingConfiguration);

                try (InputStream inputStream = exchange.getInputStream()) {
                    unmarshaller.start(new InputStreamByteInput(inputStream));
                    ReceivedTransaction txConfig = readTransaction(unmarshaller);


                    final Transaction transaction;
                    if (txConfig == null || localTransactionContext == null) { //the TX context may be null in unit tests
                        transaction = null;
                    } else {
                        try {
                            ImportResult<LocalTransaction> result = localTransactionContext.findOrImportTransaction(txConfig.getXid(), txConfig.getRemainingTime());
                            transaction = result.getTransaction();
                        } catch (XAException e) {
                            throw new IllegalStateException(e); //TODO: what to do here?
                        }
                    }
                    for (int i = 0; i < parameterTypeNames.length; ++i) {
                        methodParams[i] = unmarshaller.readObject();
                    }
                    final Map<String, Object> privateAttachments;
                    final Map<String, Object> contextData;
                    int attachementCount = PackedInteger.readPackedInteger(unmarshaller);
                    if (attachementCount > 0) {
                        contextData = new HashMap<>();
                        for (int i = 0; i < attachementCount - 1; ++i) {
                            String key = (String) unmarshaller.readObject();
                            Object value = unmarshaller.readObject();
                            contextData.put(key, value);
                        }
                        privateAttachments = (Map<String, Object>) unmarshaller.readObject();
                    } else {
                        contextData = Collections.emptyMap();
                        privateAttachments = Collections.emptyMap();
                    }

                    unmarshaller.finish();

                    EJBLocator<?> locator;
                    if (EJBHome.class.isAssignableFrom(view)) {
                        locator = new EJBHomeLocator(view, app, module, bean, distinct, Affinity.LOCAL); //TODO: what is the correct affinity?
                    } else if (!sessionID.isEmpty()) {
                        locator = new StatefulEJBLocator<>(view, app, module, bean, distinct,
                                SessionID.createSessionID(sessionID.getBytes(StandardCharsets.UTF_8)), Affinity.LOCAL);
                    } else {
                        locator = new StatelessEJBLocator<>(view, app, module, bean, distinct, Affinity.LOCAL);
                    }

                    return new ResolvedInvocation(privateAttachments, methodParams, locator, exchange, marshallingConfiguration, sessionAffinity, transaction);
                } catch (Exception e) {
                    HttpServerHelper.sendException(exchange, StatusCodes.INTERNAL_SERVER_ERROR, e);
                    return null;
                }
            }

            @Override
            public EJBMethodLocator getMethodLocator() {
                return new EJBMethodLocator(method, parameterTypeNames);
            }

            @Override
            public void writeNoSuchMethod() {
                HttpServerHelper.sendException(exchange, StatusCodes.NOT_FOUND, EjbHttpClientMessages.MESSAGES.noSuchMethod());
            }

            @Override
            public void writeSessionNotActive() {
                HttpServerHelper.sendException(exchange, StatusCodes.INTERNAL_SERVER_ERROR, EjbHttpClientMessages.MESSAGES.sessionNotActive());
            }

            @Override
            public void writeWrongViewType() {
                HttpServerHelper.sendException(exchange, StatusCodes.NOT_FOUND, EjbHttpClientMessages.MESSAGES.wrongViewType());
            }

            @Override
            public Executor getRequestExecutor() {
                return executorService;
            }

            @Override
            public String getProtocol() {
                return exchange.getProtocol().toString();
            }

            @Override
            public boolean isBlockingCaller() {
                return false;
            }

            @Override
            public EJBIdentifier getEJBIdentifier() {
                return ejbIdentifier;
            }

            @Override
            public void writeException(@NotNull Exception exception) {
                HttpServerHelper.sendException(exchange, StatusCodes.INTERNAL_SERVER_ERROR, exception);
            }

            @Override
            public void writeNoSuchEJB() {
                HttpServerHelper.sendException(exchange, StatusCodes.NOT_FOUND, new NoSuchEJBException());
            }

            @Override
            public void writeCancelResponse() {
                throw new RuntimeException("nyi");
            }

            @Override
            public void writeNotStateful() {
                HttpServerHelper.sendException(exchange, StatusCodes.INTERNAL_SERVER_ERROR, EjbHttpClientMessages.MESSAGES.notStateful());
            }

            @Override
            public void convertToStateful(@NotNull SessionID sessionId) throws IllegalArgumentException, IllegalStateException {
                throw new RuntimeException("nyi");
            }
        }));
    }

    private static String handleDash(String s) {
        if (s.equals("-")) {
            return "";
        }
        return s;
    }

    static class ResolvedInvocation implements InvocationRequest.Resolved {
        private final Map<String, Object> privateAttachments;
        private final Object[] methodParams;
        private final EJBLocator<?> locator;
        private final HttpServerExchange exchange;
        private final MarshallingConfiguration marshallingConfiguration;
        private final String sessionAffinity;
        private final Transaction transaction;

        public ResolvedInvocation(Map<String, Object> privateAttachments, Object[] methodParams, EJBLocator<?> locator, HttpServerExchange exchange, MarshallingConfiguration marshallingConfiguration, String sessionAffinity, Transaction transaction) {
            this.privateAttachments = privateAttachments;
            this.methodParams = methodParams;
            this.locator = locator;
            this.exchange = exchange;
            this.marshallingConfiguration = marshallingConfiguration;
            this.sessionAffinity = sessionAffinity;
            this.transaction = transaction;
        }

        @Override
        public Map<String, Object> getAttachments() {
            return privateAttachments;
        }

        @Override
        public Object[] getParameters() {
            return methodParams;
        }

        @Override
        public EJBLocator<?> getEJBLocator() {
            return locator;
        }

        @Override
        public boolean hasTransaction() {
            return transaction != null;
        }

        @Override
        public Transaction getTransaction() throws SystemException, IllegalStateException {
            return transaction;
        }

        String getSessionAffinity() {
            return sessionAffinity;
        }

        HttpServerExchange getExchange() {
            return exchange;
        }

        @Override
        public void writeInvocationResult(Object result) {

            try {
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, EjbHeaders.EJB_RESPONSE_VERSION_ONE.toString());
//                                    if (output.getSessionAffinity() != null) {
//                                        exchange.getResponseCookies().put("JSESSIONID", new CookieImpl("JSESSIONID", output.getSessionAffinity()).setPath(WILDFLY_SERVICES));
//                                    }
                final Marshaller marshaller = HttpServerHelper.RIVER_MARSHALLER_FACTORY.createMarshaller(marshallingConfiguration);
                OutputStream outputStream = exchange.getOutputStream();
                final ByteOutput byteOutput = Marshalling.createByteOutput(outputStream);
                // start the marshaller
                marshaller.start(byteOutput);
                marshaller.writeObject(result);
                marshaller.write(0);
                marshaller.finish();
                marshaller.flush();
                exchange.endExchange();
            } catch (Exception e) {
                HttpServerHelper.sendException(exchange, 500, e);
            }
        }
    }
}
