package org.wildfly.httpclient.naming;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.util.Methods;

import javax.naming.Context;

/**
 * @author Stuart Douglas
 */
public class HttpRemoteNamingService {

    private final Context localContext;

    public HttpRemoteNamingService(Context localContext) {
        this.localContext = localContext;
    }


    public HttpHandler createHandler() {
        RoutingHandler routingHandler = new RoutingHandler();
        routingHandler.add(Methods.POST, LookupHandler.PATH, new LookupHandler(localContext));

        return routingHandler;
    }


    private static final class LookupHandler implements HttpHandler {

        public static final String PATH = "/v1/lookup/{name}";
        private final Context localContext;

        private LookupHandler(Context localContext) {
            this.localContext = localContext;
        }

        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {

        }
    }
}
