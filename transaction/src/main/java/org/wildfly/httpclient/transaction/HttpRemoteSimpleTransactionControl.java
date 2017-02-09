package org.wildfly.httpclient.transaction;

import org.wildfly.httpclient.common.WildflyHttpContext;
import org.wildfly.transaction.client.spi.SimpleTransactionControl;

import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import java.net.URI;

/**
 * @author Stuart Douglas
 */
public class HttpRemoteSimpleTransactionControl implements SimpleTransactionControl{

    private final URI uri;
    private final WildflyHttpContext wildflyHttpContext;

    public HttpRemoteSimpleTransactionControl(URI uri, WildflyHttpContext wildflyHttpContext) {
        this.uri = uri;
        this.wildflyHttpContext = wildflyHttpContext;
    }

    @Override
    public void setRollbackOnly() throws SystemException {

    }

    @Override
    public void commit() throws RollbackException, HeuristicMixedException, HeuristicRollbackException, SecurityException, SystemException {

    }

    @Override
    public void rollback() throws SecurityException, SystemException {

    }

    @Override
    public <T> T getProviderInterface(Class<T> providerInterfaceType) {
        return null;
    }
}
