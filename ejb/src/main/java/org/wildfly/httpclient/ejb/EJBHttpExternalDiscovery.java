package org.wildfly.httpclient.ejb;

import org.wildfly.discovery.FilterSpec;
import org.wildfly.discovery.ServiceType;
import org.wildfly.discovery.ServiceURL;
import org.wildfly.discovery.spi.DiscoveryProvider;
import org.wildfly.discovery.spi.DiscoveryRequest;
import org.wildfly.discovery.spi.DiscoveryResult;
import org.wildfly.discovery.spi.ExternalDiscoveryConfigurator;
import org.wildfly.discovery.spi.RegistryProvider;

import java.net.URI;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * @author Stuart Douglas
 */
public class EJBHttpExternalDiscovery implements ExternalDiscoveryConfigurator {


    /**
     * The discovery attribute name which contains the application and module name of the located EJB.
     */
    public static final String FILTER_ATTR_EJB_MODULE = "ejb-module";
    /**
     * The discovery attribute name which contains the application and module name with the distinct name of the located EJB.
     */
    public static final String FILTER_ATTR_EJB_MODULE_DISTINCT = "ejb-module-distinct";

    @Override
    public void configure(Consumer<DiscoveryProvider> discoveryProviderConsumer, Consumer<RegistryProvider> registryProviderConsumer) {
        discoveryProviderConsumer.accept(new DiscoveryProvider() {
            @Override
            public DiscoveryRequest discover(ServiceType serviceType, FilterSpec filterSpec, DiscoveryResult result) {
                if(Objects.equals(serviceType.getAbstractType(), "ejb") &&
                        Objects.equals(serviceType.getAbstractTypeAuthority(), "jboss")) {
                    //result.
                    //filterSpec.matchesMulti()
                    EJBHttpContext context = EJBHttpContext.getCurrent();
                    if(context == null) {
                        return null;
                    }
                    for(EJBHttpContext.DiscoveryResult service : context.getDiscoveryAttributes()) {
                        if(filterSpec.matchesMulti(service.getAttributes())) {
                            for(URI uri : service.getUri()) {
                                result.addMatch(new ServiceURL.Builder().setUri(uri).setAbstractType("ejb").setAbstractTypeAuthority("jboss").create());
                            }
                        }
                    }
                    result.complete();
                }

                return null;
            }
        });
    }
}
