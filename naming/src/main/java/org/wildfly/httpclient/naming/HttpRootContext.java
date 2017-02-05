package org.wildfly.httpclient.naming;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import javax.naming.Binding;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NameClassPair;
import javax.naming.NamingException;

import org.jboss.marshalling.InputStreamByteInput;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.Unmarshaller;
import org.wildfly.httpclient.common.ContentType;
import org.wildfly.httpclient.common.HttpTargetContext;
import org.wildfly.httpclient.common.WildflyHttpContext;
import org.wildfly.naming.client.AbstractFederatingContext;
import org.wildfly.naming.client.CloseableNamingEnumeration;
import org.wildfly.naming.client.util.FastHashtable;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;

/**
 * @author Stuart Douglas
 */
public class HttpRootContext extends AbstractFederatingContext {

    private final String ACCEPT_VALUE = "application/x-wf-jndi-jbmar-value;version=1,application/x-wf-jbmar-exception;version=1";
    private final ContentType VALUE_TYPE = new ContentType("application/x-wf-jndi-jbmar-value", 1);

    private final HttpNamingProvider httpNamingProvider;
    private final String scheme;

    protected HttpRootContext(FastHashtable<String, Object> environment, HttpNamingProvider httpNamingProvider, String scheme) {
        super(environment);
        this.httpNamingProvider = httpNamingProvider;
        this.scheme = scheme;
    }

    @Override
    public void bind(String name, Object obj) throws NamingException {
        super.bind(name, obj);
    }

    @Override
    protected Object lookupNative(Name name) throws NamingException {
        URI providerUri = httpNamingProvider.getProviderUri();
        StringBuilder sb = new StringBuilder(providerUri.getPath());
        sb.append("/naming/v1/lookup/");
        return processInvocation(name, Methods.POST, providerUri, sb);
    }

    @Override
    protected Object lookupLinkNative(Name name) throws NamingException {
        URI providerUri = httpNamingProvider.getProviderUri();
        StringBuilder sb = new StringBuilder(providerUri.getPath());
        sb.append("/naming/v1/lookuplink/");
        return processInvocation(name, Methods.POST, providerUri, sb);
    }

    @Override
    protected CloseableNamingEnumeration<NameClassPair> listNative(Name name) throws NamingException {
        URI providerUri = httpNamingProvider.getProviderUri();
        StringBuilder sb = new StringBuilder(providerUri.getPath());
        sb.append("/naming/v1/list/");
        Collection<NameClassPair> result = (Collection<NameClassPair>) processInvocation(name, Methods.GET, providerUri, sb);
        return CloseableNamingEnumeration.fromIterable(result);
    }

    @Override
    protected CloseableNamingEnumeration<Binding> listBindingsNative(Name name) throws NamingException {
        URI providerUri = httpNamingProvider.getProviderUri();
        StringBuilder sb = new StringBuilder(providerUri.getPath());
        sb.append("/naming/v1/list-bindings/");
        Collection<Binding> result = (Collection<Binding>) processInvocation(name, Methods.GET, providerUri, sb);
        return CloseableNamingEnumeration.fromIterable(result);
    }

    @Override
    protected void bindNative(Name name, Object obj) throws NamingException {
        URI providerUri = httpNamingProvider.getProviderUri();
        StringBuilder sb = new StringBuilder(providerUri.getPath());
        sb.append("/naming/v1/bind/");
        processInvocation(name, Methods.PUT, providerUri, obj, sb);
    }

    @Override
    protected void rebindNative(Name name, Object obj) throws NamingException {
        URI providerUri = httpNamingProvider.getProviderUri();
        StringBuilder sb = new StringBuilder(providerUri.getPath());
        sb.append("/naming/v1/rebind/");
        processInvocation(name, Methods.PUT, providerUri, obj, sb);
    }

    @Override
    protected void unbindNative(Name name) throws NamingException {
        URI providerUri = httpNamingProvider.getProviderUri();
        StringBuilder sb = new StringBuilder(providerUri.getPath());
        sb.append("/naming/v1/unbind/");
        processInvocation(name, Methods.PUT, providerUri, sb);
    }

    @Override
    protected void renameNative(Name oldName, Name newName) throws NamingException {
        URI providerUri = httpNamingProvider.getProviderUri();
        StringBuilder sb = new StringBuilder(providerUri.getPath());
        sb.append("/naming/v1/rename/");
        processInvocation(oldName, Methods.PUT, providerUri, sb, newName);
    }

    @Override
    protected void destroySubcontextNative(Name name) throws NamingException {
        URI providerUri = httpNamingProvider.getProviderUri();
        StringBuilder sb = new StringBuilder(providerUri.getPath());
        sb.append("/naming/v1/rename/");
        processInvocation(name, Methods.PUT, providerUri, sb);
    }

    @Override
    protected Context createSubcontextNative(Name name) throws NamingException {
        return super.createSubcontextNative(name);
    }

    private MarshallingConfiguration createMarshallingConfig() {
        final MarshallingConfiguration marshallingConfiguration = new MarshallingConfiguration();
        marshallingConfiguration.setVersion(2);
        return marshallingConfiguration;
    }

    private Object processInvocation(Name name, HttpString method, URI providerUri, StringBuilder sb) throws NamingException {
        return processInvocation(name, method, providerUri, sb, (Name)null);
    }

    private Object processInvocation(Name name, HttpString method, URI providerUri, StringBuilder sb, Name newName) throws NamingException {
        try {
            sb.append(URLEncoder.encode(name.toString(), StandardCharsets.UTF_8.name()));
            if (newName != null) {
                sb.append("?new=");
                sb.append(URLEncoder.encode(newName.toString(), StandardCharsets.UTF_8.name()));
            }
        } catch (UnsupportedEncodingException e) {
            NamingException namingException = new NamingException(e.getMessage());
            namingException.initCause(e);
            throw namingException;
        }
        String path = sb.toString();
        final ClientRequest clientRequest = new ClientRequest()
                .setPath(path)
                .setMethod(method);
        clientRequest.getRequestHeaders().put(Headers.ACCEPT, ACCEPT_VALUE);

        final CompletableFuture<Object> result = new CompletableFuture<>();

        final HttpTargetContext targetContext = WildflyHttpContext.getCurrent().getTargetContext(providerUri);
        targetContext.getConnectionPool().getConnection(connection -> targetContext.sendRequest(connection, clientRequest, null, new HttpTargetContext.HttpResultHandler() {
            @Override
            public void handleResult(InputStream input, ClientResponse response) {
                Exception exception = null;
                Object returned = null;
                try {

                    final MarshallingConfiguration marshallingConfiguration = createMarshallingConfig();
                    final Unmarshaller unmarshaller = targetContext.createUnmarshaller(marshallingConfiguration);
                    unmarshaller.start(new InputStreamByteInput(input));
                    returned = unmarshaller.readObject();
                    // finish unmarshalling
                    if (unmarshaller.read() != -1) {
                        exception = HttpNamingClientMessages.MESSAGES.unexpectedDataInResponse();
                    }
                    unmarshaller.finish();
                    connection.done(false);

                    if (response.getResponseCode() >= 400) {
                        exception = (Exception) returned;
                    }
                } catch (Exception e) {
                    exception = e;
                }
                if (exception != null) {
                    result.completeExceptionally(exception);
                } else {
                    result.complete(returned);
                }
            }
        }, result::completeExceptionally, VALUE_TYPE, null), result::completeExceptionally, false);

        try {
            return result.get();
        } catch (InterruptedException e) {
            NamingException namingException = new NamingException(e.getMessage());
            namingException.initCause(e);
            throw namingException;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if(cause instanceof NamingException) {
                throw (NamingException)cause;
            } else {
                NamingException namingException = new NamingException();
                namingException.initCause(cause);
                throw namingException;
            }
        }
    }


    private void processInvocation(Name name, HttpString method, URI providerUri, Object object, StringBuilder sb) throws NamingException {
        try {
            sb.append(URLEncoder.encode(name.toString(), StandardCharsets.UTF_8.name()));
        } catch (UnsupportedEncodingException e) {
            NamingException namingException = new NamingException(e.getMessage());
            namingException.initCause(e);
            throw namingException;
        }
        String path = sb.toString();
        final ClientRequest clientRequest = new ClientRequest()
                .setPath(path)
                .setMethod(method);
        clientRequest.getRequestHeaders().put(Headers.ACCEPT, ACCEPT_VALUE);

        final CompletableFuture<Object> result = new CompletableFuture<>();

        final HttpTargetContext targetContext = WildflyHttpContext.getCurrent().getTargetContext(providerUri);
        targetContext.getConnectionPool().getConnection(connection -> targetContext.sendRequest(connection, clientRequest, null, new HttpTargetContext.HttpResultHandler() {
            @Override
            public void handleResult(InputStream input, ClientResponse response) {
                Exception exception = null;
                Object returned = null;
                try {

                    final MarshallingConfiguration marshallingConfiguration = createMarshallingConfig();
                    final Unmarshaller unmarshaller = targetContext.createUnmarshaller(marshallingConfiguration);
                    unmarshaller.start(new InputStreamByteInput(input));
                    returned = unmarshaller.readObject();
                    // finish unmarshalling
                    if (unmarshaller.read() != -1) {
                        exception = HttpNamingClientMessages.MESSAGES.unexpectedDataInResponse();
                    }
                    unmarshaller.finish();
                    connection.done(false);

                    if (response.getResponseCode() >= 400) {
                        exception = (Exception) returned;
                    }
                } catch (Exception e) {
                    exception = e;
                }
                if (exception != null) {
                    result.completeExceptionally(exception);
                } else {
                    result.complete(returned);
                }
            }
        }, result::completeExceptionally, VALUE_TYPE, null), result::completeExceptionally, false);

        try {
            result.get();
        } catch (InterruptedException e) {
            NamingException namingException = new NamingException(e.getMessage());
            namingException.initCause(e);
            throw namingException;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if(cause instanceof NamingException) {
                throw (NamingException)cause;
            } else {
                NamingException namingException = new NamingException();
                namingException.initCause(cause);
                throw namingException;
            }
        }
    }

    @Override
    public void close() throws NamingException {

    }

    @Override
    public String getNameInNamespace() throws NamingException {
        final String scheme = this.scheme;
        return scheme == null || scheme.isEmpty() ? "" : scheme + ":";
    }
}
