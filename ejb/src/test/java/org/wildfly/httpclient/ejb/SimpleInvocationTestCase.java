package org.wildfly.httpclient.ejb;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import javax.ejb.ApplicationException;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.jboss.ejb.client.EJBClient;
import org.jboss.ejb.client.StatefulEJBLocator;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.ejb.client.URIAffinity;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.httpclient.common.HttpTargetContext;
import org.wildfly.httpclient.common.WildflyHttpContext;

/**
 * @author Stuart Douglas
 */
@RunWith(EJBTestServer.class)
public class SimpleInvocationTestCase {

    public static final String APP = "wildfly-app";
    public static final String MODULE = "wildfly-ejb-remote-server-side";

    @Before
    public void before() {
        EJBTestServer.registerServicesHandler("common/v1/affinity", httpServerExchange -> httpServerExchange.getResponseHeaders().put(Headers.SET_COOKIE, "JSESSIONID=" + EJBTestServer.INITIAL_SESSION_AFFINITY));
    }

    @Test
    public void testSimpleInvocationViaURLAffinity() throws Exception {
        clearSessionId();
        EJBTestServer.setHandler((invocation, out) -> invocation.getParams()[0]);
        final StatelessEJBLocator<EchoRemote> statelessEJBLocator = new StatelessEJBLocator<>(EchoRemote.class, APP, MODULE, "CalculatorBean", "");
        final EchoRemote proxy = EJBClient.createProxy(statelessEJBLocator);
        final String message = "Hello World!!!";
        EJBClient.setStrongAffinity(proxy, URIAffinity.forUri(new URI(EJBTestServer.getDefaultServerURL())));
        final String echo = proxy.echo(message);
        Assert.assertEquals("Unexpected echo message", message, echo);
    }

    @Test
    public void testSimpleInvocationViaDiscovery() throws Exception {
        clearSessionId();
        EJBTestServer.setHandler((invocation, out) -> invocation.getParams()[0]);
        final StatelessEJBLocator<EchoRemote> statelessEJBLocator = new StatelessEJBLocator<>(EchoRemote.class, APP, MODULE, "CalculatorBean", "");
        final EchoRemote proxy = EJBClient.createProxy(statelessEJBLocator);
        final String message = "Hello World!!!";
        final String echo = proxy.echo(message);
        Assert.assertEquals("Unexpected echo message", message, echo);
    }


    @Test(expected = TestException.class)
    public void testSimpleFailedInvocation() throws Exception {
        clearSessionId();
        EJBTestServer.setHandler((invocation, out) -> {
            throw new TestException(invocation.getParams()[0].toString());
        });
        final StatelessEJBLocator<EchoRemote> statelessEJBLocator = new StatelessEJBLocator<EchoRemote>(EchoRemote.class, APP, MODULE, EchoBean.class.getSimpleName(), "");
        final EchoRemote proxy = EJBClient.createProxy(statelessEJBLocator);
        final String message = "Hello World!!!";
        for (int i = 0; i < 10; ++i) {
            String echo = proxy.echo(message);
            Assert.assertEquals("Unexpected echo message", message, echo);
        }
    }

    @Test
    public void testInvocationAffinity() throws Exception {
        clearSessionId();
        EJBTestServer.setHandler((invocation, out) -> {
            out.setSessionAffinity("foo");
            return invocation.getSessionAffinity();
        });
        final StatelessEJBLocator<EchoRemote> statelessEJBLocator = new StatelessEJBLocator<>(EchoRemote.class, APP, MODULE, EchoBean.class.getSimpleName(), "");
        final EchoRemote proxy = EJBClient.createProxy(statelessEJBLocator);

        String echo = proxy.echo("");
        Assert.assertEquals("Unexpected echo message", EJBTestServer.INITIAL_SESSION_AFFINITY, echo);
        echo = proxy.echo("");
        Assert.assertEquals("Unexpected echo message", "foo", echo);

    }


    @Test
    public void testSessionOpen() throws Exception {
        clearSessionId();
        EJBTestServer.setHandler((invocation, out) -> new String(Base64.getDecoder().decode(invocation.getBeanId())));
        StatefulEJBLocator<EchoRemote> locator = EJBClient.createSession(EchoRemote.class, APP, MODULE, EchoBean.class.getSimpleName(), "");
        EchoRemote proxy = EJBClient.createProxy(locator);
        final String message = "Hello World!!!";
        final String echo = proxy.echo(message);
        Assert.assertEquals("Unexpected echo message", "SFSB_ID", echo);

    }

    @Test
    @Ignore
    public void testSessionOpenLazyAffinity() throws Exception {
        clearSessionId();
        EJBTestServer.setHandler((invocation, out) -> new String(Base64.getDecoder().decode(invocation.getBeanId())) + "-" + invocation.getSessionAffinity());

        StatefulEJBLocator<EchoRemote> locator = EJBClient.createSession(EchoRemote.class, APP, MODULE, EchoBean.class.getSimpleName(), "");
        EchoRemote proxy = EJBClient.createProxy(locator);
        final String message = "Hello World!!!";
        final String echo = proxy.echo(message);
        Assert.assertEquals("Unexpected echo message", "SFSB_ID-lazy-session-affinity", echo);
    }

    private void clearSessionId() throws URISyntaxException {
        WildflyHttpContext.getCurrent().getTargetContext(new URI(EJBTestServer.getDefaultServerURL())).clearSessionId();
    }

    @ApplicationException
    private static class TestException extends Exception {
        public TestException(String message) {
            super(message);
        }
    }
}
