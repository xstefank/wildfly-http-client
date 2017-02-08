package org.wildfly.httpclient.ejb;

import java.util.concurrent.ExecutorService;

import org.jboss.ejb.server.Association;
import io.undertow.server.HttpHandler;
import io.undertow.server.RoutingHandler;
import io.undertow.util.Methods;

/**
 * @author Stuart Douglas
 */
public class EjbHttpService {

    static final String JSESSIONID = "JSESSIONID";

    private final Association association;
    private final ExecutorService executorService;

    public EjbHttpService(Association association, ExecutorService executorService) {
        this.association = association;
        this.executorService = executorService;
    }


    public HttpHandler createHttpHandler() {

        return new RoutingHandler()
                .add(Methods.POST, HttpInvocationHandler.PATH, new HttpInvocationHandler(association, executorService))
                .add(Methods.POST, HttpSessionOpenHandler.PATH, new HttpSessionOpenHandler(association, executorService));
    }

}
