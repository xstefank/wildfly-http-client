package org.wildfly.ejb.http;

import java.net.URI;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.jboss.ejb.client.ContextSelector;
import org.jboss.ejb.client.EJBClientContext;
import org.jboss.ejb.client.EJBClientContextIdentifier;
import org.jboss.ejb.client.IdentityEJBClientContextSelector;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.river.RiverMarshallerFactory;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import io.undertow.connector.ByteBufferPool;
import io.undertow.server.DefaultByteBufferPool;

/**
 * @author Stuart Douglas
 */
public class DefaultHTTPContextSelector implements IdentityEJBClientContextSelector {

    private final ConcurrentMap<EJBClientContextIdentifier, EJBClientContext> identifiableContexts = new ConcurrentHashMap<EJBClientContextIdentifier, EJBClientContext>();

    private final ContextSelector<EJBClientContext> delegate;
    private final List<ReceiverProperties> recivers;

    private volatile boolean init = false;

    public DefaultHTTPContextSelector(ContextSelector<EJBClientContext> delegate, List<ReceiverProperties> recivers) {
        this.delegate = delegate;
        this.recivers = recivers;
    }

    @Override
    public void registerContext(final EJBClientContextIdentifier identifier, final EJBClientContext context) {
        final EJBClientContext previousRegisteredContext = this.identifiableContexts.putIfAbsent(identifier, context);
        if (previousRegisteredContext != null) {
            throw HttpClientMessages.MESSAGES.ejbClientContextAlreadyRegisteredForIdentifier(identifier);
        }
    }

    @Override
    public EJBClientContext unRegisterContext(final EJBClientContextIdentifier identifier) {
        return this.identifiableContexts.remove(identifier);
    }

    @Override
    public EJBClientContext getContext(final EJBClientContextIdentifier identifier) {
        return this.identifiableContexts.get(identifier);
    }

    @Override
    public EJBClientContext getCurrent() {
        if (!init) {
            synchronized (this) {
                if (!init) {
                    EJBClientContext del = delegate.getCurrent();
                    initialize(del);
                    init = true;
                }
            }
        }
        return delegate.getCurrent();
    }

    private void initialize(EJBClientContext del) {
        if (recivers == null) {
            return;
        }
        Xnio xnio = Xnio.getInstance();
        try {
            //todo: lots of configurability
            XnioWorker worker = xnio.createWorker(OptionMap.create(Options.THREAD_DAEMON, true));
            ByteBufferPool pool = new DefaultByteBufferPool(true, 1024 * 8, 10, 0, 0);
            MarshallerFactory factory = new RiverMarshallerFactory();
            for (ReceiverProperties rec : recivers) {
                del.registerEJBReceiver(new HttpEJBReceiver(rec.uri.toASCIIString(), rec.uri, worker, pool, null, OptionMap.EMPTY, factory, false, rec.modules.toArray(new HttpEJBReceiver.ModuleID[0])));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static class ReceiverProperties {
        private final URI uri;
        private final List<HttpEJBReceiver.ModuleID> modules;

        ReceiverProperties(URI uri, List<HttpEJBReceiver.ModuleID> modules) {
            this.uri = uri;
            this.modules = modules;
        }
    }
}
