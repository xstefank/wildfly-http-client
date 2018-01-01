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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;
import org.wildfly.security.WildFlyElytronProvider;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.SslClientAuthMode;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.connector.ByteBufferPool;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.handlers.AuthenticationCallHandler;
import io.undertow.security.handlers.AuthenticationConstraintHandler;
import io.undertow.security.handlers.AuthenticationMechanismsHandler;
import io.undertow.security.handlers.SecurityInitialHandler;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.DigestCredential;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.security.idm.X509CertificateCredential;
import io.undertow.security.impl.BasicAuthenticationMechanism;
import io.undertow.security.impl.ClientCertAuthenticationMechanism;
import io.undertow.security.impl.DigestAuthenticationMechanism;
import io.undertow.server.DefaultByteBufferPool;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.util.HexConverter;
import io.undertow.util.NetworkUtils;

/**
 * @author Stuart Douglas
 */
public class HTTPTestServer extends BlockJUnit4ClassRunner {

    public static final int BUFFER_SIZE = Integer.getInteger("test.bufferSize", 8192 * 3);
    private static final PathHandler PATH_HANDLER = new PathHandler();
    private static final PathHandler SERVICES_HANDLER = new PathHandler();
    public static final String WILDFLY_SERVICES = "/wildfly-services";
    public static final String INITIAL_SESSION_AFFINITY = "initial-session-affinity";

    private static final String SERVER_KEY_STORE = "server.keystore";
    private static final String SERVER_TRUST_STORE = "server.truststore";
    public static final String CLIENT_KEY_STORE = "client.keystore";
    public static final String CLIENT_TRUST_STORE = "client.truststore";
    public static final char[] STORE_PASSWORD = "password".toCharArray();

    private static boolean first = true;
    private static Undertow undertow;

    private static XnioWorker worker;

    private static final DefaultByteBufferPool pool = new DefaultByteBufferPool(true, BUFFER_SIZE, 1000, 10, 100);

    private static final Set<String> registeredPaths = new HashSet<>();
    private static final Set<String> registeredServices = new HashSet<>();

    static {
        Security.addProvider(new WildFlyElytronProvider());
    }

    /**
     * @return The base URL that can be used to make connections to this server
     */
    public static String getDefaultServerURL() {
        return getDefaultRootServerURL() + WILDFLY_SERVICES;
    }

    public static String getDefaultRootServerURL() {
        return "http://" + NetworkUtils.formatPossibleIpv6Address(getHostAddress()) + ":" + getHostPort();
    }

    public static String getDefaultSSLRootServerURL() {
        return "https://" + NetworkUtils.formatPossibleIpv6Address(getHostAddress()) + ":" + getSSLHostPort();
    }

    /**
     * @return The base URL that can be used to make connections to this server
     */
    public static String getDefaultSSLServerURL() {
        return getDefaultSSLRootServerURL() + WILDFLY_SERVICES;
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
                        .addHttpsListener(getSSLHostPort(), getHostAddress(), createServerSslContext())
                        .setServerOption(UndertowOptions.REQUIRE_HOST_HTTP11, true)
                        .setServerOption(UndertowOptions.NO_REQUEST_TIMEOUT, 1000)
                        .setSocketOption(Options.SSL_CLIENT_AUTH_MODE, SslClientAuthMode.REQUIRED)
                        .setHandler(new SecurityInitialHandler(AuthenticationMode.PRO_ACTIVE, new IdentityManager() {
                            @Override
                            public Account verify(Account account) {
                                return null;
                            }

                            @Override
                            public Account verify(String id, Credential credential) {
                                if (credential instanceof PasswordCredential) {
                                    if (id.equals("administrator") && Arrays.equals(((PasswordCredential) credential).getPassword(), "password1!".toCharArray())) {
                                        return new TestAccount();
                                    }
                                } else if (credential instanceof DigestCredential) {
                                    DigestCredential digCred = (DigestCredential) credential;
                                    MessageDigest digest = null;
                                    try {
                                        digest = digCred.getAlgorithm().getMessageDigest();

                                        digest.update(id.getBytes(StandardCharsets.UTF_8));
                                        digest.update((byte) ':');
                                        digest.update(digCred.getRealm().getBytes(StandardCharsets.UTF_8));
                                        digest.update((byte) ':');
                                        char[] expectedPassword = "password1!".toCharArray();
                                        digest.update(new String(expectedPassword).getBytes(StandardCharsets.UTF_8));

                                        if(digCred.verifyHA1(HexConverter.convertToHexBytes(digest.digest()))) {
                                            return new TestAccount();
                                        }
                                    } catch (NoSuchAlgorithmException e) {
                                        throw new IllegalStateException("Unsupported Algorithm", e);
                                    } finally {
                                        digest.reset();
                                    }
                                }
                                return null;

                            }

                            @Override
                            public Account verify(Credential credential) {
                                X509CertificateCredential cred = (X509CertificateCredential) credential;
                                return new Account() {
                                    @Override
                                    public Principal getPrincipal() {
                                        return cred.getCertificate().getSubjectX500Principal();
                                    }

                                    @Override
                                    public Set<String> getRoles() {
                                        return Collections.emptySet();
                                    }
                                };
                            }
                        }, new AuthenticationConstraintHandler(new AuthenticationMechanismsHandler(new AuthenticationCallHandler(PATH_HANDLER), Arrays.asList(new AuthenticationMechanism[]{new BasicAuthenticationMechanism("myRealm", "BASIC", true), new DigestAuthenticationMechanism("test", "localhost", "DIGEST"), new ClientCertAuthenticationMechanism(true)})))))
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
            throw new RuntimeException(e);
        }
    }

    private SSLContext createServerSslContext() {
        return createSSLContext(loadKeyStore(SERVER_KEY_STORE), loadKeyStore(SERVER_TRUST_STORE));
    }

    private static SSLContext createSSLContext(final KeyStore keyStore, final KeyStore trustStore) {
        KeyManager[] keyManagers;
        try {
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, STORE_PASSWORD);
            keyManagers = keyManagerFactory.getKeyManagers();
        } catch (NoSuchAlgorithmException | UnrecoverableKeyException | KeyStoreException e) {
            throw new RuntimeException("Unable to initialise KeyManager[]", e);
        }

        TrustManager[] trustManagers = null;
        try {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);
            trustManagers = trustManagerFactory.getTrustManagers();
        } catch (NoSuchAlgorithmException | KeyStoreException e) {
            throw new RuntimeException("Unable to initialise TrustManager[]", e);
        }

        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, trustManagers, null);
            return sslContext;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException("Unable to create and initialise the SSLContext", e);
        }
    }

    public static KeyStore loadKeyStore(final String name) {
        final InputStream stream = HTTPTestServer.class.getClassLoader().getResourceAsStream(name);
        if (stream == null) {
            throw new RuntimeException("Could not load keystore");
        }
        try {
            KeyStore loadedKeystore = KeyStore.getInstance("JKS");
            loadedKeystore.load(stream, STORE_PASSWORD);

            return loadedKeystore;
        } catch (KeyStoreException | NoSuchAlgorithmException | IOException | CertificateException e) {
            throw new RuntimeException(String.format("Unable to load KeyStore %s", name), e);
        } finally {
            IoUtils.safeClose(stream);
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

    public static int getSSLHostPort() {
        return getHostPort() + 1;
    }

    private static class TestAccount implements Account {
        @Override
        public Principal getPrincipal() {
            return () -> "administrator";
        }

        @Override
        public Set<String> getRoles() {
            return Collections.emptySet();
        }
    }
}
