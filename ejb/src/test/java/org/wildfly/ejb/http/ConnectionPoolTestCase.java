package org.wildfly.ejb.http;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.OptionMap;
import org.xnio.channels.Channels;
import io.undertow.client.ClientCallback;
import io.undertow.client.ClientExchange;
import io.undertow.client.ClientRequest;
import io.undertow.server.ServerConnection;
import io.undertow.util.Methods;

/**
 * @author Stuart Douglas
 */
@RunWith(TestServer.class)
public class ConnectionPoolTestCase {

    public static final int THREADS = 10;
    public static final int MAX_CONNECTION_COUNT = 3;
    public static final int CONNECTION_IDLE_TIMEOUT = 1000;
    public static String MAX_CONNECTIONS_PATH = "/max-connections-test";
    public static String IDLE_TIMEOUT_PATH = "/idle-timeout-path";

    private static final List<ServerConnection> connections = new CopyOnWriteArrayList<>();

    private static volatile long currentRequests, maxActiveRequests;

    @BeforeClass
    public static void before() {
        TestServer.PATH_HANDLER.addPrefixPath(MAX_CONNECTIONS_PATH, (exchange -> {
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
        TestServer.PATH_HANDLER.addPrefixPath(IDLE_TIMEOUT_PATH, (exchange -> {
            connections.add(exchange.getConnection());
        }));
    }

    @AfterClass
    public static void after() {
        TestServer.PATH_HANDLER.removePrefixPath(MAX_CONNECTIONS_PATH);
        TestServer.PATH_HANDLER.removePrefixPath(IDLE_TIMEOUT_PATH);
    }


    @Test
    public void testIdleTimeout() throws Exception {
        HttpConnectionPool pool = new HttpConnectionPool(1, 1, TestServer.getWorker(), TestServer.getBufferPool(), null, OptionMap.EMPTY, new HostPool(Collections.singletonList(new URI(TestServer.getDefaultRootServerURL()))), CONNECTION_IDLE_TIMEOUT);
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
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

    private void doInvocation(String path, HttpConnectionPool pool, CountDownLatch latch, AtomicReference<Throwable> failed) {

        pool.getConnection((connectionHandle) -> {
            ClientRequest request = new ClientRequest().setMethod(Methods.GET).setPath(path);
            System.out.println(connectionHandle.getConnection() + " " + System.currentTimeMillis());
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

    @Test
    public void testMaxConnections() throws Exception {
        HttpConnectionPool pool = new HttpConnectionPool(MAX_CONNECTION_COUNT, 1, TestServer.getWorker(), TestServer.getBufferPool(), null, OptionMap.EMPTY, new HostPool(Collections.singletonList(new URI(TestServer.getDefaultRootServerURL()))), -1);
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        List<CountDownLatch> results = new ArrayList<>();
        final AtomicReference<Throwable> failed = new AtomicReference<>();
        try {
            for (int i = 0; i < THREADS * 2; ++i) {
                final CountDownLatch latch = new CountDownLatch(1);
                results.add(latch);
                executor.execute(() -> {

                    pool.getConnection((connectionHandle) -> {
                        ClientRequest request = new ClientRequest().setMethod(Methods.GET).setPath(MAX_CONNECTIONS_PATH);
                        System.out.println(connectionHandle.getConnection() + " " + System.currentTimeMillis());
                        connectionHandle.getConnection().sendRequest(request, new ClientCallback<ClientExchange>() {
                            @Override
                            public void completed(ClientExchange result) {
                                result.setResponseListener(new ClientCallback<ClientExchange>() {
                                    @Override
                                    public void completed(ClientExchange result) {
                                        try {
                                            Channels.drain(result.getResponseChannel(), Long.MAX_VALUE);
                                            latch.countDown();
                                            connectionHandle.done(false);
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
                });
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

    private void checkFailed(AtomicReference<Throwable> failed) {
        Throwable failure = failed.get();
        if (failure != null) {
            throw new RuntimeException(failure);
        }
    }
}
