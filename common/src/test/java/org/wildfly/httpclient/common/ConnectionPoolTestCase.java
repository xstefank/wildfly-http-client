package org.wildfly.httpclient.common;

import io.undertow.client.ClientCallback;
import io.undertow.client.ClientExchange;
import io.undertow.client.ClientRequest;
import io.undertow.server.ServerConnection;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.util.FlexBase64;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.AuthenticationContextConfigurationClient;
import org.xnio.OptionMap;
import org.xnio.channels.Channels;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Stuart Douglas
 */
@RunWith(HTTPTestServer.class)
public class ConnectionPoolTestCase {

    static final int THREADS = 20;
    static final int MAX_CONNECTION_COUNT = 3;
    static final int CONNECTION_IDLE_TIMEOUT = 1000;
    static String MAX_CONNECTIONS_PATH = "/max-connections-test";
    static String IDLE_TIMEOUT_PATH = "/idle-timeout-path";

    private static final List<ServerConnection> connections = new CopyOnWriteArrayList<>();

    private static volatile long currentRequests, maxActiveRequests;

    @Test
    public void testIdleTimeout() throws Exception {
        HTTPTestServer.registerPathHandler(IDLE_TIMEOUT_PATH, (exchange -> {
            connections.add(exchange.getConnection());
        }));

        HttpConnectionPool pool = new HttpConnectionPool(1, 1, HTTPTestServer.getWorker(), HTTPTestServer.getBufferPool(), OptionMap.EMPTY, new HostPool(new URI(HTTPTestServer.getDefaultRootServerURL())), CONNECTION_IDLE_TIMEOUT);
        final AtomicReference<Throwable> failed = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(2);
        doInvocation(IDLE_TIMEOUT_PATH, pool, latch, failed);
        doInvocation(IDLE_TIMEOUT_PATH, pool, latch, failed);
        Assert.assertTrue(latch.await(10, TimeUnit.SECONDS));
        checkFailed(failed);
        Assert.assertEquals(2, connections.size());
        Assert.assertEquals(connections.get(0), connections.get(1));
        connections.clear();
        latch = new CountDownLatch(2);

        doInvocation(IDLE_TIMEOUT_PATH, pool, latch, failed);
        Thread.sleep(CONNECTION_IDLE_TIMEOUT * 2);
        doInvocation(IDLE_TIMEOUT_PATH, pool, latch, failed);
        Assert.assertTrue(latch.await(10, TimeUnit.SECONDS));
        checkFailed(failed);

        Assert.assertEquals(2, connections.size());
        Assert.assertNotEquals(connections.get(0), connections.get(1));

    }

    @Test
    public void testMaxConnections() throws Exception {
        HTTPTestServer.registerPathHandler(MAX_CONNECTIONS_PATH, new BlockingHandler(exchange -> {
            synchronized (ConnectionPoolTestCase.class) {
                currentRequests++;
                if (currentRequests > maxActiveRequests) {
                    maxActiveRequests = currentRequests;
                }
            }
            Thread.sleep(200);
            synchronized (ConnectionPoolTestCase.class) {
                currentRequests--;
            }
        }));
        HttpConnectionPool pool = new HttpConnectionPool(MAX_CONNECTION_COUNT, 1, HTTPTestServer.getWorker(), HTTPTestServer.getBufferPool(), OptionMap.EMPTY, new HostPool(new URI(HTTPTestServer.getDefaultRootServerURL())), -1);
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        List<CountDownLatch> results = new ArrayList<>();
        final AtomicReference<Throwable> failed = new AtomicReference<>();
        try {
            for (int i = 0; i < THREADS * 2; ++i) {
                final CountDownLatch latch = new CountDownLatch(1);
                results.add(latch);
                doInvocation(MAX_CONNECTIONS_PATH, pool, latch, failed);
            }

            for (CountDownLatch i : results) {
                Assert.assertTrue(i.await(10, TimeUnit.SECONDS));
            }
            checkFailed(failed);
            Assert.assertEquals(MAX_CONNECTION_COUNT, maxActiveRequests);
        } finally {
            executor.shutdownNow();
        }
    }

    private void doInvocation(String path, HttpConnectionPool pool, CountDownLatch latch, AtomicReference<Throwable> failed) {

        pool.getConnection((connectionHandle) -> {
            ClientRequest request = new ClientRequest().setMethod(Methods.GET).setPath(path);
            setupBasicAuth(request, connectionHandle.getUri());
            request.getRequestHeaders().add(Headers.HOST, HTTPTestServer.getHostAddress());
            connectionHandle.getConnection().sendRequest(request, new ClientCallback<ClientExchange>() {
                @Override
                public void completed(ClientExchange result) {
                    result.setResponseListener(new ClientCallback<ClientExchange>() {
                        @Override
                        public void completed(ClientExchange result) {
                            try {
                                Channels.drain(result.getResponseChannel(), Long.MAX_VALUE);
                                connectionHandle.done(false);
                                latch.countDown();
                            } catch (IOException e) {
                                failed.set(e);
                                latch.countDown();
                            }
                        }

                        @Override
                        public void failed(IOException e) {
                            failed.set(e);
                            latch.countDown();
                            connectionHandle.done(true);
                        }
                    });
                }

                @Override
                public void failed(IOException e) {
                    failed.set(e);
                    latch.countDown();
                    connectionHandle.done(true);
                }
            });
        }, (error) -> {
            failed.set(error);
            latch.countDown();
        }, false);
    }

    private void checkFailed(AtomicReference<Throwable> failed) {
        Throwable failure = failed.get();
        if (failure != null) {
            throw new RuntimeException(failure);
        }
    }

    private void setupBasicAuth(ClientRequest request, URI uri) {
        AuthenticationContext context = AuthenticationContext.captureCurrent();
        AuthenticationConfiguration config = new AuthenticationContextConfigurationClient().getAuthenticationConfiguration(uri, context);
        Principal principal = new AuthenticationContextConfigurationClient().getPrincipal(config);
        PasswordCallback callback = new PasswordCallback("password", false);
        try {
            new AuthenticationContextConfigurationClient().getCallbackHandler(config).handle(new Callback[]{callback});
        } catch (IOException | UnsupportedCallbackException e) {
            return;
        }
        char[] password = callback.getPassword();
        String challenge = principal.getName() + ":" + new String(password);
        request.getRequestHeaders().put(Headers.AUTHORIZATION, "basic " + FlexBase64.encodeString(challenge.getBytes(StandardCharsets.UTF_8), false));
    }
}
