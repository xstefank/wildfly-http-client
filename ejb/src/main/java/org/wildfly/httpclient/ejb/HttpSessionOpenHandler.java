package org.wildfly.httpclient.ejb;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.CookieImpl;
import io.undertow.server.session.SecureRandomSessionIdGenerator;
import io.undertow.server.session.SessionIdGenerator;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import org.jboss.ejb.client.EJBIdentifier;
import org.jboss.ejb.client.SessionID;
import org.jboss.ejb.server.Association;
import org.jboss.ejb.server.SessionOpenRequest;
import org.jboss.marshalling.InputStreamByteInput;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.Unmarshaller;
import org.wildfly.common.annotation.NotNull;
import org.wildfly.httpclient.common.ContentType;
import org.wildfly.httpclient.common.HttpServerHelper;
import org.wildfly.transaction.client.ImportResult;
import org.wildfly.transaction.client.LocalTransaction;
import org.wildfly.transaction.client.LocalTransactionContext;

import javax.ejb.NoSuchEJBException;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.xa.XAException;
import java.io.InputStream;
import java.net.SocketAddress;
import java.util.Base64;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * @author Stuart Douglas
 */
class HttpSessionOpenHandler extends RemoteHTTPHandler {


    private final Association association;
    private final ExecutorService executorService;
    private final SessionIdGenerator sessionIdGenerator = new SecureRandomSessionIdGenerator();
    private final LocalTransactionContext localTransactionContext;

    HttpSessionOpenHandler(Association association, ExecutorService executorService, LocalTransactionContext localTransactionContext) {
        super(executorService);
        this.association = association;
        this.executorService = executorService;
        this.localTransactionContext = localTransactionContext;
    }

    @Override
    protected void handleInternal(HttpServerExchange exchange) throws Exception {
        String ct = exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE);
        ContentType contentType = ContentType.parse(ct);
        if (contentType == null || contentType.getVersion() != 1 || !EjbHeaders.SESSION_OPEN.equals(contentType.getType())) {
            exchange.setStatusCode(StatusCodes.BAD_REQUEST);
            EjbHttpClientMessages.MESSAGES.debugf("Bad content type %s", ct);
            return;
        }
        String relativePath = exchange.getRelativePath();
        if(relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }
        String[] parts = relativePath.split("/");
        if(parts.length != 4) {
            exchange.setStatusCode(StatusCodes.NOT_FOUND);
            return;
        }
        final String app = handleDash(parts[0]);
        final String module = handleDash(parts[1]);
        final String distinct = handleDash(parts[2]);
        final String bean = parts[3];

        Cookie cookie = exchange.getRequestCookies().get(EjbHttpService.JSESSIONID);
        String sessionAffinity = null;
        if (cookie != null) {
            sessionAffinity = cookie.getValue();
        }


        final EJBIdentifier ejbIdentifier = new EJBIdentifier(app, module, bean, distinct);
        exchange.dispatch(executorService, () -> {
            final ReceivedTransaction txConfig;
            try {
                final MarshallingConfiguration marshallingConfiguration = new MarshallingConfiguration();
                marshallingConfiguration.setClassTable(ProtocolV1ClassTable.INSTANCE);
                marshallingConfiguration.setObjectTable(ProtocolV1ObjectTable.INSTANCE);
                marshallingConfiguration.setVersion(2);
                Unmarshaller unmarshaller = HttpServerHelper.RIVER_MARSHALLER_FACTORY.createUnmarshaller(marshallingConfiguration);

                try (InputStream inputStream = exchange.getInputStream()) {
                    unmarshaller.start(new InputStreamByteInput(inputStream));
                    txConfig = readTransaction(unmarshaller);
                    unmarshaller.finish();
                }
            } catch (Exception e) {
                HttpServerHelper.sendException(exchange, StatusCodes.INTERNAL_SERVER_ERROR, e);
                return;
            }
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

            association.receiveSessionOpenRequest(new SessionOpenRequest() {
                @Override
                public boolean hasTransaction() {
                    return txConfig != null;
                }

                @Override
                public Transaction getTransaction() throws SystemException, IllegalStateException {
                    return transaction;
                }

                @Override
                public SocketAddress getPeerAddress() {
                    return exchange.getSourceAddress();
                }

                @Override
                public SocketAddress getLocalAddress() {
                    return exchange.getDestinationAddress();
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
                public void writeWrongViewType() {
                    HttpServerHelper.sendException(exchange, StatusCodes.NOT_FOUND, EjbHttpClientMessages.MESSAGES.wrongViewType());
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


                    final MarshallingConfiguration marshallingConfiguration = new MarshallingConfiguration();
                    marshallingConfiguration.setClassTable(ProtocolV1ClassTable.INSTANCE);
                    marshallingConfiguration.setObjectTable(ProtocolV1ObjectTable.INSTANCE);
                    marshallingConfiguration.setVersion(2);

                    Cookie sessionCookie = exchange.getRequestCookies().get(EjbHttpService.JSESSIONID);
                    if (sessionCookie == null) {
                        String rootPath = exchange.getResolvedPath();
                        int ejbIndex = rootPath.lastIndexOf("/ejb");
                        if (ejbIndex > 0) {
                            rootPath = rootPath.substring(0, ejbIndex);
                        }

                        exchange.getResponseCookies().put(EjbHttpService.JSESSIONID, new CookieImpl(EjbHttpService.JSESSIONID, sessionIdGenerator.createSessionId()).setPath(rootPath));
                    }

                    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, EjbHeaders.EJB_RESPONSE_NEW_SESSION.toString());
                    exchange.getResponseHeaders().put(EjbHeaders.EJB_SESSION_ID, Base64.getEncoder().encodeToString(sessionId.getEncodedForm()));

                    exchange.setStatusCode(StatusCodes.NO_CONTENT);
                    exchange.endExchange();
                }

                @Override
                public <C> C getProviderInterface(Class<C> providerInterfaceType) {
                    return null;
                }
            });
        });
    }

    private static String handleDash(String s) {
        if (s.equals("-")) {
            return "";
        }
        return s;
    }
}
