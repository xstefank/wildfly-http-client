package org.wildfly.httpclient.naming;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
        final HttpTargetContext targetContext = WildflyHttpContext.getCurrent().getTargetContext(providerUri);

        StringBuilder sb = new StringBuilder(providerUri.getPath());
        sb.append("/jndi/v1/lookup/");
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
                .setMethod(Methods.POST);
        clientRequest.getRequestHeaders().put(Headers.ACCEPT, ACCEPT_VALUE);

        final CompletableFuture<Object> result = new CompletableFuture<>();

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
                        return;
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
        } catch (InterruptedException | ExecutionException e) {
            NamingException namingException = new NamingException(e.getMessage());
            namingException.initCause(e);
            throw namingException;
        }
    }

    private MarshallingConfiguration createMarshallingConfig() {
        final MarshallingConfiguration marshallingConfiguration = new MarshallingConfiguration();
        marshallingConfiguration.setVersion(2);
        return marshallingConfiguration;
    }

    @Override
    protected Object lookupLinkNative(Name name) throws NamingException {
        return null;
    }

    @Override
    protected CloseableNamingEnumeration<NameClassPair> listNative(Name name) throws NamingException {
        return null;
    }

    @Override
    protected CloseableNamingEnumeration<Binding> listBindingsNative(Name name) throws NamingException {
        return null;
    }

    @Override
    protected void bindNative(Name name, Object obj) throws NamingException {
        super.bindNative(name, obj);
    }

    @Override
    protected void rebindNative(Name name, Object obj) throws NamingException {
        super.rebindNative(name, obj);
    }

    @Override
    protected void unbindNative(Name name) throws NamingException {
        super.unbindNative(name);
    }

    @Override
    protected void renameNative(Name oldName, Name newName) throws NamingException {
        super.renameNative(oldName, newName);
    }

    @Override
    protected void destroySubcontextNative(Name name) throws NamingException {
        super.destroySubcontextNative(name);
    }

    @Override
    protected Context createSubcontextNative(Name name) throws NamingException {
        return super.createSubcontextNative(name);
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
