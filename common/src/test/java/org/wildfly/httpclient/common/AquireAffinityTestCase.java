package org.wildfly.httpclient.common;


import java.net.URI;
import java.net.URISyntaxException;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import io.undertow.server.handlers.CookieImpl;

/**
 * @author Stuart Douglas
 */
@RunWith(HTTPTestServer.class)
public class AquireAffinityTestCase {

    @Test
    public void testAquireAffinity() throws URISyntaxException {
        HTTPTestServer.registerServicesHandler("common/v1/affinity", exchange -> exchange.getResponseCookies().put("JSESSIONID", new CookieImpl("JSESSIONID", "foo")));

        HttpTargetContext context = WildflyHttpContext.getCurrent().getTargetContext(new URI(HTTPTestServer.getDefaultServerURL()));
        Assert.assertEquals("foo", context.awaitSessionId());

    }
}
