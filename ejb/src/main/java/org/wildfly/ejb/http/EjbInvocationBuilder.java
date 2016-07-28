package org.wildfly.ejb.http;

import io.undertow.client.ClientRequest;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;

import java.lang.reflect.Method;

/**
 * Builder for invocations against a specific EJB, such as invocation and session open
 *
 * @author Stuart Douglas
 */
class EjbInvocationBuilder {

    private static final HttpString INVOCATION_ID = new HttpString("X-wf-invocation-id");
    public static final String ACCEPT = "application/x-wf-ejb-response;version=1,application/x-wf-ejb-exception;version=1";

    private String appName;
    private String moduleName;
    private String distinctName;
    private String beanName;
    private String beanId;
    private String view;
    private String sessionId;
    private Method method;
    private InvocationType invocationType;
    private Long invocationId;

    public String getAppName() {
        return appName;
    }

    public EjbInvocationBuilder setAppName(String appName) {
        this.appName = appName;
        return this;
    }

    public String getModuleName() {
        return moduleName;
    }

    public EjbInvocationBuilder setModuleName(String moduleName) {
        this.moduleName = moduleName;
        return this;
    }

    public String getDistinctName() {
        return distinctName;
    }

    public EjbInvocationBuilder setDistinctName(String distinctName) {
        this.distinctName = distinctName;
        return this;
    }

    public String getBeanName() {
        return beanName;
    }

    public EjbInvocationBuilder setBeanName(String beanName) {
        this.beanName = beanName;
        return this;
    }

    public String getBeanId() {
        return beanId;
    }

    public EjbInvocationBuilder setBeanId(String beanId) {
        this.beanId = beanId;
        return this;
    }

    public Method getMethod() {
        return method;
    }

    public EjbInvocationBuilder setMethod(Method method) {
        this.method = method;
        return this;
    }

    public String getView() {
        return view;
    }

    public EjbInvocationBuilder setView(String view) {
        this.view = view;
        return this;
    }

    public InvocationType getInvocationType() {
        return invocationType;
    }

    public EjbInvocationBuilder setInvocationType(InvocationType invocationType) {
        this.invocationType = invocationType;
        return this;
    }

    public Long getInvocationId() {
        return invocationId;
    }

    public EjbInvocationBuilder setInvocationId(Long invocationId) {
        this.invocationId = invocationId;
        return this;
    }

    public String getSessionId() {
        return sessionId;
    }

    public EjbInvocationBuilder setSessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    /**
     * Constructs an EJB invocation path
     * @param mountPoint The mount point of the EJB context
     * @param appName The application name
     * @param moduleName The module name
     * @param distinctName The distinct name
     * @param beanName The bean name
     *
     * @return The request path to invoke
     */
    private static String buildPath(final String mountPoint, final String appName, final String moduleName, final String distinctName, final String beanName) {
        StringBuilder sb = new StringBuilder();
        buildBeanPath(mountPoint, appName, moduleName, distinctName, beanName, sb);
        return sb.toString();
    }
    /**
     * Constructs an EJB invocation path
     * @param mountPoint The mount point of the EJB context
     * @param appName The application name
     * @param moduleName The module name
     * @param distinctName The distinct name
     * @param beanName The bean name
     *
     * @return The request path to invoke
     */
    private static String buildPath(final String mountPoint, final String appName, final String moduleName, final String distinctName, final String beanName, String view) {
        StringBuilder sb = new StringBuilder();
        buildBeanPath(mountPoint, appName, moduleName, distinctName, beanName, sb);
        sb.append("/").append(view);
        return sb.toString();
    }

    /**
     * Constructs an EJB invocation path
     * @param mountPoint The mount point of the EJB context
     * @param appName The application name
     * @param moduleName The module name
     * @param distinctName The distinct name
     * @param beanName The bean name
     * @param beanId The bean id
     *
     * @return The request path to invoke
     */
    private static String buildPath(final String mountPoint, final String appName, final String moduleName, final String distinctName, final String beanName, final String beanId, final String view, final Method method) {
        StringBuilder sb = new StringBuilder();
        buildBeanPath(mountPoint, appName, moduleName, distinctName, beanName, sb);
        sb.append("/");
        if(beanId == null) {
            sb.append("-");
        } else {
            sb.append(beanId);
        }
        sb.append("/");
        sb.append(view);
        sb.append("/");
        sb.append(method.getName());
        for(Class<?> param : method.getParameterTypes()) {
            sb.append("/");
            sb.append(param.getName());
        }
        return sb.toString();
    }

    private static void buildBeanPath(String mountPoint, String appName, String moduleName, String distinctName, String beanName, StringBuilder sb) {
        buildModulePath(mountPoint, appName, moduleName, distinctName, sb);
        sb.append("/");
        sb.append(beanName);
    }

    private static void buildModulePath(String mountPoint, String appName, String moduleName, String distinctName, StringBuilder sb) {
        if(mountPoint != null) {
            sb.append(mountPoint);
        }
        sb.append("/ejb/");
        if(appName == null || appName.isEmpty()) {
            sb.append("-");
        } else {
            sb.append(appName);
        }
        sb.append("/");
        if(moduleName == null || moduleName.isEmpty()) {
            sb.append("-");
        } else {
            sb.append(moduleName);
        }
        sb.append("/");
        if(distinctName == null || distinctName.isEmpty()) {
            sb.append("-");
        } else {
            sb.append(distinctName);
        }
    }

    public ClientRequest createRequest(String mountPoint) {
        ClientRequest clientRequest = new ClientRequest();
        clientRequest.setMethod(Methods.POST);
        if(sessionId != null) {
            clientRequest.getRequestHeaders().put(Headers.COOKIE, "JSESSIONID=" + sessionId); //TODO: fix this
        }
        if(invocationType == InvocationType.METHOD_INVOCATION) {
            clientRequest.getRequestHeaders().add(Headers.ACCEPT, ACCEPT);
            if (invocationId != null) {
                if (sessionId == null ) {
                    throw new IllegalStateException();
                }
                clientRequest.getRequestHeaders().put(INVOCATION_ID, invocationId);
            }
            clientRequest.setPath(buildPath(mountPoint, appName, moduleName, distinctName, beanName, beanId, view, method));
            clientRequest.getRequestHeaders().put(Headers.CONTENT_TYPE, EjbHeaders.INVOCATION_VERSION_ONE);
        } else if(invocationType == InvocationType.STATEFUL_CREATE) {
            clientRequest.setPath(buildPath(mountPoint, appName, moduleName, distinctName, beanName));
            clientRequest.getRequestHeaders().put(Headers.CONTENT_TYPE, EjbHeaders.SESSION_OPEN_VERSION_ONE);
        } else if(invocationType == InvocationType.AFFINITY) {
            clientRequest.setPath(mountPoint + "/ejb");
            clientRequest.getRequestHeaders().put(Headers.CONTENT_TYPE, EjbHeaders.AFFINITY_VERSION_ONE);
        }

        return clientRequest;
    }


    public enum InvocationType {
        METHOD_INVOCATION,
        STATEFUL_CREATE,
        CANCEL,
        AFFINITY
    }

}
