/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.httpclient.common;

import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.river.RiverMarshallerFactory;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.connector.ByteBufferPool;
import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.handlers.AuthenticationCallHandler;
import io.undertow.security.handlers.AuthenticationConstraintHandler;
import io.undertow.security.handlers.AuthenticationMechanismsHandler;
import io.undertow.security.handlers.SecurityInitialHandler;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.security.impl.BasicAuthenticationMechanism;
import io.undertow.server.DefaultByteBufferPool;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.util.NetworkUtils;

/**
 * @author Stuart Douglas
 */
public class HTTPTestServer extends BlockJUnit4ClassRunner {

    protected static final MarshallerFactory marshallerFactory = new RiverMarshallerFactory();

    public static final int BUFFER_SIZE = Integer.getInteger("test.bufferSize", 8192 * 3);
    private static final PathHandler PATH_HANDLER = new PathHandler();
    private static final PathHandler SERVICES_HANDLER = new PathHandler();
    public static final String SFSB_ID = "SFSB_ID";
    public static final String WILDFLY_SERVICES = "/wildfly-services";
    public static final String INITIAL_SESSION_AFFINITY = "initial-session-affinity";
    public static final String LAZY_SESSION_AFFINITY = "lazy-session-affinity";
    private static boolean first = true;
    private static Undertow undertow;

    private static XnioWorker worker;

    private static final DefaultByteBufferPool pool = new DefaultByteBufferPool(true, BUFFER_SIZE, 1000, 10, 100);

    private static final Set<String> registeredPaths = new HashSet<>();
    private static final Set<String> registeredServices = new HashSet<>();

    /**
     * @return The base URL that can be used to make connections to this server
     */
    public static String getDefaultServerURL() {
        return getDefaultRootServerURL() + WILDFLY_SERVICES;
    }

    public static String getDefaultRootServerURL() {
        return "http://" + NetworkUtils.formatPossibleIpv6Address(getHostAddress()) + ":" + getHostPort();
    }

    public HTTPTestServer(Class<?> klass) throws InitializationError {
        super(klass);
    }

    public static ByteBufferPool getBufferPool() {
        return pool;
    }

    @Override
    public Description getDescription() {
        return super.getDescription();
    }

    @Override
    public void run(final RunNotifier notifier) {
        runInternal(notifier);
        notifier.addListener(new RunListener() {
            @Override
            public void testFinished(Description description) throws Exception {
                for (String reg : registeredPaths) {
                    PATH_HANDLER.removePrefixPath(reg);
                }
                registeredPaths.clear();
                for (String reg : registeredServices) {
                    SERVICES_HANDLER.removePrefixPath(reg);
                }
                registeredServices.clear();
            }
        });
        super.run(notifier);
    }

    public static void registerPathHandler(String path, HttpHandler handler) {
        PATH_HANDLER.addPrefixPath(path, handler);
        registeredPaths.add(path);
    }

    public static void registerServicesHandler(String path, HttpHandler handler) {
        SERVICES_HANDLER.addPrefixPath(path, handler);
        registeredServices.add(path);
    }
    public static XnioWorker getWorker() {
        return worker;
    }

    private void runInternal(final RunNotifier notifier) {
        try {
            if (first) {
                first = false;
                Xnio xnio = Xnio.getInstance("nio");
                PATH_HANDLER.addPrefixPath("/wildfly-services", SERVICES_HANDLER);
                worker = xnio.createWorker(OptionMap.create(Options.WORKER_TASK_CORE_THREADS, 20, Options.WORKER_IO_THREADS, 10));
                registerPaths(SERVICES_HANDLER);
                undertow = Undertow.builder()
                        .addHttpListener(getHostPort(), getHostAddress())
                        .setServerOption(UndertowOptions.REQUIRE_HOST_HTTP11, true)
                        .setHandler(new SecurityInitialHandler(AuthenticationMode.PRO_ACTIVE, new IdentityManager() {
                            @Override
                            public Account verify(Account account) {
                                return null;
                            }

                            @Override
                            public Account verify(String id, Credential credential) {
                                if(credential instanceof PasswordCredential) {
                                    if(id.equals("administrator") && Arrays.equals(((PasswordCredential) credential).getPassword(), "password1!".toCharArray())) {
                                        return new Account() {
                                            @Override
                                            public Principal getPrincipal() {
                                                return () -> "administrator";
                                            }

                                            @Override
                                            public Set<String> getRoles() {
                                                return Collections.emptySet();
                                            }
                                        };
                                    }
                                }
                                return null;

                            }

                            @Override
                            public Account verify(Credential credential) {
                                return null;
                            }
                        }, new AuthenticationConstraintHandler(new AuthenticationMechanismsHandler(new AuthenticationCallHandler(PATH_HANDLER), Collections.singletonList(new BasicAuthenticationMechanism("test"))))))
                        .build();
                undertow.start();
                notifier.addListener(new RunListener() {
                    @Override
                    public void testRunFinished(final Result result) throws Exception {
                        undertow.stop();
                    }
                });
            }
        } catch (Exception e) {
            throw new RuntimeException();
        }
    }

    protected void registerPaths(PathHandler servicesHandler) {

    }

    public static String getHostAddress() {
        return System.getProperty("server.address", "localhost");
    }

    public static int getHostPort() {
        return Integer.getInteger("server.port", 7788);
    }

    private static String handleDash(String s) {
        if (s.equals("-")) {
            return "";
        }
        return s;
    }
}
