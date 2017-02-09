package org.wildfly.httpclient.ejb;

import java.net.SocketAddress;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import javax.ejb.NoSuchEJBException;
import javax.transaction.SystemException;
import javax.transaction.Transaction;

import org.jboss.ejb.client.EJBIdentifier;
import org.jboss.ejb.client.SessionID;
import org.jboss.ejb.server.Association;
import org.jboss.ejb.server.SessionOpenRequest;
import org.jboss.marshalling.MarshallingConfiguration;
import org.wildfly.common.annotation.NotNull;
import org.wildfly.httpclient.common.HttpServerHelper;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.CookieImpl;
import io.undertow.server.session.SecureRandomSessionIdGenerator;
import io.undertow.server.session.SessionIdGenerator;
import io.undertow.util.Headers;
import io.undertow.util.PathTemplateMatch;
import io.undertow.util.SameThreadExecutor;
import io.undertow.util.StatusCodes;

/**
 * @author Stuart Douglas
 */
class HttpSessionOpenHandler extends RemoteHTTPHandler {


    static String PATH = "/v1/open/{app}/{module}/{distinct}/{beanName}";
    private final Association association;
    private final ExecutorService executorService;
    private final SessionIdGenerator sessionIdGenerator = new SecureRandomSessionIdGenerator();

    HttpSessionOpenHandler(Association association, ExecutorService executorService) {
        super(executorService, HttpServerHelper.RIVER_MARSHALLER_FACTORY);
        this.association = association;
        this.executorService = executorService;
    }

    @Override
    protected void handleInternal(HttpServerExchange exchange) throws Exception {
        PathTemplateMatch match = exchange.getAttachment(PathTemplateMatch.ATTACHMENT_KEY);
        Map<String, String> params = match.getParameters();
        final String app = handleDash(params.get("app"));
        final String module = handleDash(params.get("module"));
        final String distinct = handleDash(params.get("distinct"));
        final String bean = params.get("beanName");

        Cookie cookie = exchange.getRequestCookies().get(EjbHttpService.JSESSIONID);
        String sessionAffinity = null;
        if (cookie != null) {
            sessionAffinity = cookie.getValue();
        }
        final EJBIdentifier ejbIdentifier = new EJBIdentifier(app, module, bean, distinct);
        exchange.dispatch(SameThreadExecutor.INSTANCE, () -> association.receiveSessionOpenRequest(new SessionOpenRequest() {
            @Override
            public boolean hasTransaction() {
                return false;
            }

            @Override
            public Transaction getTransaction() throws SystemException, IllegalStateException {
                return null;
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
                    if(ejbIndex > 0) {
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
        }));
    }

    private static String handleDash(String s) {
        if (s.equals("-")) {
            return "";
        }
        return s;
    }
}
