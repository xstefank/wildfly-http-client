package org.wildfly.httpclient.ejb.invocation;

import java.net.URI;
import java.util.Base64;
import javax.ejb.ApplicationException;

import org.jboss.ejb.client.ContextSelector;
import org.jboss.ejb.client.EJBClient;
import org.jboss.ejb.client.EJBClientContext;
import org.jboss.ejb.client.StatefulEJBLocator;
import org.jboss.ejb.client.StatelessEJBLocator;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.river.RiverMarshallerFactory;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.httpclient.ejb.HttpEJBReceiver;
import org.wildfly.httpclient.ejb.EJBTestServer;
import org.wildfly.httpclient.ejb.common.EchoBean;
import org.wildfly.httpclient.ejb.common.EchoRemote;
import org.xnio.OptionMap;

/**
 * @author Stuart Douglas
 */
@RunWith(EJBTestServer.class)
public class SimpleInvocationTestCase {

    public static final String APP = "my-app";
    public static final String MODULE = "my-module";

    @Test
    public void testSimpleInvocation() throws Exception {
        EJBTestServer.setHandler((invocation, out) -> invocation.getParams()[0]);
        final StatelessEJBLocator<EchoRemote> statelessEJBLocator = new StatelessEJBLocator<EchoRemote>(EchoRemote.class, APP, MODULE, EchoBean.class.getSimpleName(), "");
        final EchoRemote proxy = EJBClient.createProxy(statelessEJBLocator);
        final String message = "Hello World!!!";
        final EJBClientContext ejbClientContext = EJBClientContext.create();
        MarshallerFactory factory = new RiverMarshallerFactory();
        ejbClientContext.registerEJBReceiver(new HttpEJBReceiver("node", new URI(EJBTestServer.getDefaultServerURL()), EJBTestServer.getWorker(), EJBTestServer.getBufferPool(), null, OptionMap.EMPTY, factory, true, new HttpEJBReceiver.ModuleID(APP, MODULE, null)));
        final ContextSelector<EJBClientContext> oldClientContextSelector = EJBClientContext.setConstantContext(ejbClientContext);
        try {
            final String echo = proxy.echo(message);
            Assert.assertEquals("Unexpected echo message", message, echo);
        } finally {
            EJBClientContext.setSelector(oldClientContextSelector);
        }
    }


    @Test
    public void testInvocationAffinity() throws Exception {
        EJBTestServer.setHandler((invocation, out) -> {
            out.setSessionAffinity("foo");
            return invocation.getSessionAffinity();
        });
        final StatelessEJBLocator<EchoRemote> statelessEJBLocator = new StatelessEJBLocator<EchoRemote>(EchoRemote.class, APP, MODULE, EchoBean.class.getSimpleName(), "");
        final EchoRemote proxy = EJBClient.createProxy(statelessEJBLocator);
        final EJBClientContext ejbClientContext = EJBClientContext.create();
        MarshallerFactory factory = new RiverMarshallerFactory();
        ejbClientContext.registerEJBReceiver(new HttpEJBReceiver("node", new URI(EJBTestServer.getDefaultServerURL()), EJBTestServer.getWorker(), EJBTestServer.getBufferPool(), null, OptionMap.EMPTY, factory, true, new HttpEJBReceiver.ModuleID(APP, MODULE, null)));
        final ContextSelector<EJBClientContext> oldClientContextSelector = EJBClientContext.setConstantContext(ejbClientContext);
        try {
            String echo = proxy.echo("");
            Assert.assertEquals("Unexpected echo message", EJBTestServer.INITIAL_SESSION_AFFINITY, echo);
            echo = proxy.echo("");
            Assert.assertEquals("Unexpected echo message", "foo", echo);
        } finally {
            EJBClientContext.setSelector(oldClientContextSelector);
        }
    }


    @Test(expected = TestException.class)
    public void testSimpleFailedInvocation() throws Exception {
        EJBTestServer.setHandler((invocation, out) -> {
            throw new TestException(invocation.getParams()[0].toString());
        });
        final StatelessEJBLocator<EchoRemote> statelessEJBLocator = new StatelessEJBLocator<EchoRemote>(EchoRemote.class, APP, MODULE, EchoBean.class.getSimpleName(), "");
        final EchoRemote proxy = EJBClient.createProxy(statelessEJBLocator);
        final String message = "Hello World!!!";
        final EJBClientContext ejbClientContext = EJBClientContext.create();
        MarshallerFactory factory = new RiverMarshallerFactory();
        ejbClientContext.registerEJBReceiver(new HttpEJBReceiver("node", new URI(EJBTestServer.getDefaultServerURL()), EJBTestServer.getWorker(), EJBTestServer.getBufferPool(), null, OptionMap.EMPTY, factory, true, new HttpEJBReceiver.ModuleID(APP, MODULE, null)));
        final ContextSelector<EJBClientContext> oldClientContextSelector = EJBClientContext.setConstantContext(ejbClientContext);
        try {
            for (int i = 0; i < 10; ++i) {
                String echo = proxy.echo(message);
                Assert.assertEquals("Unexpected echo message", message, echo);
            }
        } finally {
            EJBClientContext.setSelector(oldClientContextSelector);
        }
    }

    @Test
    public void testSessionOpen() throws Exception {
        EJBTestServer.setHandler((invocation, out) -> new String(Base64.getDecoder().decode(invocation.getBeanId())));
        final EJBClientContext ejbClientContext = EJBClientContext.create();
        MarshallerFactory factory = new RiverMarshallerFactory();
        ejbClientContext.registerEJBReceiver(new HttpEJBReceiver("node", new URI(EJBTestServer.getDefaultServerURL()), EJBTestServer.getWorker(), EJBTestServer.getBufferPool(), null, OptionMap.EMPTY, factory, true, new HttpEJBReceiver.ModuleID(APP, MODULE, null)));
        final ContextSelector<EJBClientContext> oldClientContextSelector = EJBClientContext.setConstantContext(ejbClientContext);
        try {
            StatefulEJBLocator<EchoRemote> locator = EJBClient.<EchoRemote>createSession(EchoRemote.class, APP, MODULE, EchoBean.class.getSimpleName(), "");
            EchoRemote proxy = EJBClient.createProxy(locator);
            final String message = "Hello World!!!";
            final String echo = proxy.echo(message);
            Assert.assertEquals("Unexpected echo message", "SFSB_ID", echo);
        } finally {
            EJBClientContext.setSelector(oldClientContextSelector);
        }
    }

    @Test
    public void testSessionOpenLazyAffinity() throws Exception {
        EJBTestServer.setHandler((invocation, out) -> new String(Base64.getDecoder().decode(invocation.getBeanId())) + "-" + invocation.getSessionAffinity());
        final EJBClientContext ejbClientContext = EJBClientContext.create();
        MarshallerFactory factory = new RiverMarshallerFactory();
        ejbClientContext.registerEJBReceiver(new HttpEJBReceiver("node", new URI(EJBTestServer.getDefaultServerURL()), EJBTestServer.getWorker(), EJBTestServer.getBufferPool(), null, OptionMap.EMPTY, factory, false, new HttpEJBReceiver.ModuleID(APP, MODULE, null)));
        final ContextSelector<EJBClientContext> oldClientContextSelector = EJBClientContext.setConstantContext(ejbClientContext);
        try {
            StatefulEJBLocator<EchoRemote> locator = EJBClient.<EchoRemote>createSession(EchoRemote.class, APP, MODULE, EchoBean.class.getSimpleName(), "");
            EchoRemote proxy = EJBClient.createProxy(locator);
            final String message = "Hello World!!!";
            final String echo = proxy.echo(message);
            Assert.assertEquals("Unexpected echo message", "SFSB_ID-lazy-session-affinity", echo);
        } finally {
            EJBClientContext.setSelector(oldClientContextSelector);
        }
    }
    @ApplicationException
    private static class TestException extends Exception {
        public TestException(String message) {
            super(message);
        }
    }
}
