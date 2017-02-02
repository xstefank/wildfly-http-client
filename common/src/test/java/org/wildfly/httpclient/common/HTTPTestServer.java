package org.wildfly.httpclient.common;

import java.util.HashSet;
import java.util.Set;

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
import io.undertow.server.DefaultByteBufferPool;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.util.NetworkUtils;

/**
 * @author Stuart Douglas
 */
public class HTTPTestServer extends BlockJUnit4ClassRunner {


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
                worker = xnio.createWorker(OptionMap.create(Options.WORKER_TASK_CORE_THREADS, 20, Options.WORKER_IO_THREADS, 10));
                registerPaths(SERVICES_HANDLER);
                undertow = Undertow.builder()
                        .addHttpListener(getHostPort(), getHostAddress())
                        .setServerOption(UndertowOptions.REQUIRE_HOST_HTTP11, true)
                        .setHandler(PATH_HANDLER.addPrefixPath("/wildfly-services", SERVICES_HANDLER))
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
