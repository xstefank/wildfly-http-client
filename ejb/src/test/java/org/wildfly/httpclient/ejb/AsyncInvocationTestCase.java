package org.wildfly.httpclient.ejb;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.ejb.ApplicationException;

import org.jboss.ejb.client.EJBClient;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.ejb.client.URIAffinity;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import io.undertow.util.Headers;

/**
 * @author Stuart Douglas
 */
@RunWith(EJBTestServer.class)
public class AsyncInvocationTestCase {

    public static final String APP = "wildfly-app";
    public static final String MODULE = "wildfly-ejb-remote-server-side";

    @Before
    public void before() {
        EJBTestServer.registerServicesHandler("common/v1/affinity", httpServerExchange -> httpServerExchange.getResponseHeaders().put(Headers.SET_COOKIE, "JSESSIONID=" + EJBTestServer.INITIAL_SESSION_AFFINITY));
    }

    @Test
    public void testSimpleAsyncInvocation() throws Exception {
        EJBTestServer.setHandler((invocation, affinity, out, method, handle) -> {
            if (method.getMethodName().equals("asyncMessage")) {
                return "a message";
            }
            return null;
        });
        final StatelessEJBLocator<EchoRemote> statelessEJBLocator = new StatelessEJBLocator<>(EchoRemote.class, APP, MODULE, "CalculatorBean", "", URIAffinity.forUri(new URI(EJBTestServer.getDefaultServerURL())));
        final EchoRemote proxy = EJBClient.createProxy(statelessEJBLocator);
        final Future<String> echo = proxy.asyncMessage();
        Assert.assertEquals("Unexpected echo message", "a message", echo.get());
    }

    @Test
    public void testSimpleAsyncException() throws Exception {
        EJBTestServer.setHandler((invocation, affinity, out, method, handle) -> {
            if (method.getMethodName().equals("asyncMessage")) {
                return "a message";
            } else if (method.getMethodName().equals("asyncException")) {
                throw new TestException("exception");
            }
            return null;
        });
        final StatelessEJBLocator<EchoRemote> statelessEJBLocator = new StatelessEJBLocator<>(EchoRemote.class, APP, MODULE, "CalculatorBean", "", URIAffinity.forUri(new URI(EJBTestServer.getDefaultServerURL())));
        final EchoRemote proxy = EJBClient.createProxy(statelessEJBLocator);
        final Future<String> echo = proxy.asyncException();
        try {
            echo.get();
            Assert.fail();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            Assert.assertTrue(cause instanceof TestException);
            Assert.assertEquals("exception", cause.getMessage());
        }
    }

    @Test
    public void testSimpleAsyncCancellation() throws Exception {
        final CompletableFuture<Boolean> resultFuture = new CompletableFuture<>();
        EJBTestServer.setHandler((invocation, affinity, out, method, handle) -> {
            if (method.getMethodName().equals("asyncMessage")) {
                return "a message";
            } else if (method.getMethodName().equals("asyncException")) {
                throw new TestException("exception");
            } else if (method.getMethodName().equals("asyncCancel")) {
                resultFuture.complete(handle.awaitResult());
            }
            return null;
        });
        final StatelessEJBLocator<EchoRemote> statelessEJBLocator = new StatelessEJBLocator<>(EchoRemote.class, APP, MODULE, "CalculatorBean", "", URIAffinity.forUri(new URI(EJBTestServer.getDefaultServerURL())));
        final EchoRemote proxy = EJBClient.createProxy(statelessEJBLocator);
        final Future<String> echo = proxy.asyncCancel();
        //TODO: the cancellation can be a bit racey
        Thread.sleep(1000);
        echo.cancel(true);
        Assert.assertEquals(true, resultFuture.get(10, TimeUnit.SECONDS));
    }

    @ApplicationException
    private static class TestException extends Exception {
        public TestException(String message) {
            super(message);
        }
    }
}
