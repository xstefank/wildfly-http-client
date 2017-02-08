package org.wildfly.httpclient.common;


import java.net.URI;
import java.net.URISyntaxException;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.security.auth.client.AuthenticationContext;
import io.undertow.server.handlers.CookieImpl;

/**
 * @author Stuart Douglas
 */
@RunWith(HTTPTestServer.class)
public class AcquireAffinityTestCase {

    @Test
    public void testAcquireAffinity() throws URISyntaxException {
        HTTPTestServer.registerServicesHandler("common/v1/affinity", exchange -> exchange.getResponseCookies().put("JSESSIONID", new CookieImpl("JSESSIONID", "foo")));

        AuthenticationContext cc = AuthenticationContext.captureCurrent();
        HttpTargetContext context = WildflyHttpContext.getCurrent().getTargetContext(new URI(HTTPTestServer.getDefaultServerURL()));
        Assert.assertEquals("foo", context.awaitSessionId(false));

    }
}
