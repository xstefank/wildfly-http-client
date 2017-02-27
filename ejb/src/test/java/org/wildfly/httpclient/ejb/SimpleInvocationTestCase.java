package org.wildfly.httpclient.ejb;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import javax.ejb.ApplicationException;

import org.jboss.ejb.client.EJBClient;
import org.jboss.ejb.client.StatefulEJBLocator;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.ejb.client.URIAffinity;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.httpclient.common.WildflyHttpContext;
import io.undertow.util.Headers;

/**
 * @author Stuart Douglas
 */
@RunWith(EJBTestServer.class)
public class SimpleInvocationTestCase {

    public static final String APP = "wildfly-app";
    public static final String MODULE = "wildfly-ejb-remote-server-side";
    public static final String BEAN = "EchoBean";

    @Before
    public void before() {
        EJBTestServer.registerServicesHandler("common/v1/affinity", httpServerExchange -> httpServerExchange.getResponseHeaders().put(Headers.SET_COOKIE, "JSESSIONID=" + EJBTestServer.INITIAL_SESSION_AFFINITY));
    }

    @Test
    public void testSimpleInvocationViaURLAffinity() throws Exception {
        clearSessionId();
        EJBTestServer.setHandler((invocation, affinity, out, method, handle) -> {
            if (invocation.getParameters().length == 0) {
                return "a message";
            } else {
                return invocation.getParameters()[0];
            }
        });
        final StatelessEJBLocator<EchoRemote> statelessEJBLocator = new StatelessEJBLocator<>(EchoRemote.class, APP, MODULE, "CalculatorBean", "");
        final EchoRemote proxy = EJBClient.createProxy(statelessEJBLocator);
        final String message = "Hello World!!!";
        EJBClient.setStrongAffinity(proxy, URIAffinity.forUri(new URI(EJBTestServer.getDefaultServerURL())));
        final String echo = proxy.echo(message);
        Assert.assertEquals("Unexpected echo message", message, echo);

        String m = proxy.message();
        Assert.assertEquals("a message", m);
    }

    @Test
    public void testSimpleInvocationViaDiscovery() throws Exception {
        clearSessionId();
        EJBTestServer.setHandler((invocation, affinity, out, method, handle) -> invocation.getParameters()[0]);
        final StatelessEJBLocator<EchoRemote> statelessEJBLocator = new StatelessEJBLocator<>(EchoRemote.class, APP, MODULE, "CalculatorBean", "");
        final EchoRemote proxy = EJBClient.createProxy(statelessEJBLocator);
        final String message = "Hello World!!!";
        final String echo = proxy.echo(message);
        Assert.assertEquals("Unexpected echo message", message, echo);
    }


    @Test(expected = TestException.class)
    public void testSimpleFailedInvocation() throws Exception {
        clearSessionId();
        EJBTestServer.setHandler((invocation, affinity, out, method, handle) -> {
            throw new TestException(invocation.getParameters()[0].toString());
        });
        final StatelessEJBLocator<EchoRemote> statelessEJBLocator = new StatelessEJBLocator<EchoRemote>(EchoRemote.class, APP, MODULE, BEAN, "");
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
        EJBTestServer.setHandler((invocation, affinity, out, method, handle) -> {
            out.setSessionAffinity("foo");
            return affinity;
        });
        final StatelessEJBLocator<EchoRemote> statelessEJBLocator = new StatelessEJBLocator<>(EchoRemote.class, APP, MODULE, BEAN, "");
        final EchoRemote proxy = EJBClient.createProxy(statelessEJBLocator);

        String echo = proxy.echo("");
        Assert.assertEquals("Unexpected echo message", EJBTestServer.INITIAL_SESSION_AFFINITY, echo);
        echo = proxy.echo("");
        Assert.assertEquals("Unexpected echo message", "foo", echo);

    }


    @Test
    public void testSessionOpen() throws Exception {
        clearSessionId();
        EJBTestServer.setHandler((invocation, affinity, out, method, handle) -> {
            StatefulEJBLocator<?> ejbLocator = (StatefulEJBLocator<?>) invocation.getEJBLocator();
            return new String(Base64.getDecoder().decode(ejbLocator.getSessionId().getEncodedForm()));
        });
        StatefulEJBLocator<EchoRemote> locator = EJBClient.createSession(EchoRemote.class, APP, MODULE, BEAN, "");
        EchoRemote proxy = EJBClient.createProxy(locator);
        final String message = "Hello World!!!";
        final String echo = proxy.echo(message);
        Assert.assertEquals("Unexpected echo message", "SFSB_ID", echo);

    }

    @Test
    @Ignore
    public void testSessionOpenLazyAffinity() throws Exception {
        clearSessionId();
        EJBTestServer.setHandler((invocation, affinity, out, method, handle) -> new String(Base64.getDecoder().decode(invocation.getEJBLocator().asStateful().getSessionId().getEncodedForm())) + "-" + affinity);

        StatefulEJBLocator<EchoRemote> locator = EJBClient.createSession(EchoRemote.class, APP, MODULE, BEAN, "");
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
