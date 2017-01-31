package org.wildfly.httpclient.ejb;

import io.undertow.client.ClientRequest;
import io.undertow.util.FlexBase64;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;

import java.lang.reflect.Method;

import static io.undertow.util.Headers.BASIC;

/**
 * Builder for invocations against a specific EJB, such as invocation and session open
 *
 * @author Stuart Douglas
 */
class EJBInvocationBuilder {

    private static final HttpString INVOCATION_ID = new HttpString("X-wf-invocation-id");
    private static final String INVOCATION_ACCEPT = "application/x-wf-ejb-response;version=1,application/x-wf-jbmar-exception;version=1";
    private static final String STATEFUL_CREATE_ACCEPT = "application/x-wf-jbmar-exception;version=1";
    private static final String AFFINITY_ACCEPT = "application/x-wf-jbmar-exception;version=1";

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
    private int version = 1;

    public String getAppName() {
        return appName;
    }

    public EJBInvocationBuilder setAppName(String appName) {
        this.appName = appName;
        return this;
    }

    public String getModuleName() {
        return moduleName;
    }

    public EJBInvocationBuilder setModuleName(String moduleName) {
        this.moduleName = moduleName;
        return this;
    }

    public String getDistinctName() {
        return distinctName;
    }

    public EJBInvocationBuilder setDistinctName(String distinctName) {
        this.distinctName = distinctName;
        return this;
    }

    public String getBeanName() {
        return beanName;
    }

    public EJBInvocationBuilder setBeanName(String beanName) {
        this.beanName = beanName;
        return this;
    }

    public String getBeanId() {
        return beanId;
    }

    public EJBInvocationBuilder setBeanId(String beanId) {
        this.beanId = beanId;
        return this;
    }

    public Method getMethod() {
        return method;
    }

    public EJBInvocationBuilder setMethod(Method method) {
        this.method = method;
        return this;
    }

    public String getView() {
        return view;
    }

    public EJBInvocationBuilder setView(String view) {
        this.view = view;
        return this;
    }

    public InvocationType getInvocationType() {
        return invocationType;
    }

    public EJBInvocationBuilder setInvocationType(InvocationType invocationType) {
        this.invocationType = invocationType;
        return this;
    }

    public Long getInvocationId() {
        return invocationId;
    }

    public EJBInvocationBuilder setInvocationId(Long invocationId) {
        this.invocationId = invocationId;
        return this;
    }

    public String getSessionId() {
        return sessionId;
    }

    public EJBInvocationBuilder setSessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    public int getVersion() {
        return version;
    }

    public EJBInvocationBuilder setVersion(int version) {
        this.version = version;
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
    private String buildPath(final String mountPoint, final String appName, final String moduleName, final String distinctName, final String beanName) {
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
     * @param beanId The bean id
     *
     * @return The request path to invoke
     */
    private String buildPath(final String mountPoint, final String appName, final String moduleName, final String distinctName, final String beanName, final String beanId, final String view, final Method method) {
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

    private void buildBeanPath(String mountPoint, String appName, String moduleName, String distinctName, String beanName, StringBuilder sb) {
        buildModulePath(mountPoint, appName, moduleName, distinctName, sb);
        sb.append("/");
        sb.append(beanName);
    }

    private void buildModulePath(String mountPoint, String appName, String moduleName, String distinctName, StringBuilder sb) {
        if(mountPoint != null) {
            sb.append(mountPoint);
        }
        sb.append("/ejb/v");
        sb.append(version);
        sb.append("/");
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
        if(sessionId != null) {
            clientRequest.getRequestHeaders().put(Headers.COOKIE, "JSESSIONID=" + sessionId); //TODO: fix this
        }
        clientRequest.getRequestHeaders().put(Headers.AUTHORIZATION, BASIC + " " + FlexBase64.encodeString("user1:password1".getBytes(), false));
        if(invocationType == InvocationType.METHOD_INVOCATION) {
            clientRequest.setMethod(Methods.POST);
            clientRequest.getRequestHeaders().add(Headers.ACCEPT, INVOCATION_ACCEPT);
            if (invocationId != null) {
                if (sessionId == null ) {
                    throw new IllegalStateException();
                }
                clientRequest.getRequestHeaders().put(INVOCATION_ID, invocationId);
            }
            clientRequest.setPath(buildPath(mountPoint, appName, moduleName, distinctName, beanName, beanId, view, method));
            clientRequest.getRequestHeaders().put(Headers.CONTENT_TYPE, EjbHeaders.INVOCATION_VERSION_ONE);
        } else if(invocationType == InvocationType.STATEFUL_CREATE) {
            clientRequest.setMethod(Methods.POST);
            clientRequest.setPath(buildPath(mountPoint, appName, moduleName, distinctName, beanName));
            clientRequest.getRequestHeaders().put(Headers.CONTENT_TYPE, EjbHeaders.SESSION_OPEN_VERSION_ONE);
            clientRequest.getRequestHeaders().add(Headers.ACCEPT, STATEFUL_CREATE_ACCEPT);
        }

        return clientRequest;
    }


    public enum InvocationType {
        METHOD_INVOCATION,
        STATEFUL_CREATE,
        CANCEL,
    }

}
