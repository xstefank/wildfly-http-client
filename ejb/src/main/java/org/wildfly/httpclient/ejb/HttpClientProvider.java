package org.wildfly.httpclient.ejb;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.jboss.ejb.client.ContextSelector;
import org.jboss.ejb.client.DefaultContextSelectorProvider;
import org.jboss.ejb.client.EJBClientContext;

/**
 * @author Stuart Douglas
 */
public class HttpClientProvider implements DefaultContextSelectorProvider {

    public static final String URI = ".uri";
    public static final String MODULES = ".modules";
    final String HTTP_CONNECTIONS = "http.connections";
    final String HTTP_CONNECTION = "http.connection.";

    @Override
    public ContextSelector<EJBClientContext> wrapDefaultContextSelector(ContextSelector<EJBClientContext> selector, Properties properties) {
        if(properties == null) {
            return selector;
        }
        String connections = properties.getProperty(HTTP_CONNECTIONS);
        if(connections == null || connections.trim().isEmpty()) {
            return selector;
        }
        String[] connList = connections.split(",");
        List<DefaultHTTPContextSelector.ReceiverProperties> receiverList = new ArrayList<>();
        for(String conn : connList) {
            String name = HTTP_CONNECTION + conn;
            String uri = properties.getProperty(name + URI);
            if(uri == null) {
                EJBHttpClientMessages.MESSAGES.uriCannotBeNull(conn);
            }
            String modules = properties.getProperty(name+ MODULES);
            List<HttpEJBReceiver.ModuleID> moduleIDList = new ArrayList<>();
            if(modules == null || modules.isEmpty()) {
                throw EJBHttpClientMessages.MESSAGES.noModulesSelectedForConnection(conn);
            }
            for(String mod : modules.split(",")) {
                String[] parts = mod.split(":");
                if(parts.length == 0) {
                    continue;
                } else if(parts.length == 1) {
                    moduleIDList.add(new HttpEJBReceiver.ModuleID("", parts[0], ""));
                } else if(parts.length == 2) {
                    moduleIDList.add(new HttpEJBReceiver.ModuleID(parts[0], parts[1], ""));
                } else if(parts.length == 3) {
                    moduleIDList.add(new HttpEJBReceiver.ModuleID(parts[0], parts[1], parts[2]));
                } else {
                    throw EJBHttpClientMessages.MESSAGES.invalidModuleSpec(mod, conn);
                }
            }
            DefaultHTTPContextSelector.ReceiverProperties p = null;
            try {
                p = new DefaultHTTPContextSelector.ReceiverProperties(new URI(uri), moduleIDList);
            } catch (URISyntaxException e) {
                throw EJBHttpClientMessages.MESSAGES.failedToParseURI(uri, conn);
            }
            receiverList.add(p);
        }


        return new DefaultHTTPContextSelector(selector, receiverList);
    }
}
