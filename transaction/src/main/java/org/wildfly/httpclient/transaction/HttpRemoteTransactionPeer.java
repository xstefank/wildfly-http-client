package org.wildfly.httpclient.transaction;

import org.jboss.remoting3._private.IntIndexHashMap;
import org.jboss.remoting3._private.IntIndexMap;
import org.wildfly.httpclient.common.HttpTargetContext;
import org.wildfly.transaction.client.spi.RemoteTransactionPeer;
import org.wildfly.transaction.client.spi.SimpleTransactionControl;
import org.wildfly.transaction.client.spi.SubordinateTransactionControl;

import javax.transaction.SystemException;
import javax.transaction.xa.XAException;
import javax.transaction.xa.Xid;
import java.net.URI;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author Stuart Douglas
 */
public class HttpRemoteTransactionPeer implements RemoteTransactionPeer{
    private final URI uri;
    private final HttpTargetContext targetContext;
    private final IntIndexMap<HttpRemoteTransactionHandle> peerTransactionMap = new IntIndexHashMap<HttpRemoteTransactionHandle>(HttpRemoteTransactionHandle::getId);

    public HttpRemoteTransactionPeer(URI uri, HttpTargetContext targetContext) {
        this.uri = uri;
        this.targetContext = targetContext;
    }

    @Override
    public SubordinateTransactionControl lookupXid(Xid xid, int remainingTimeout) throws XAException {
        return null;
    }

    @Override
    public Xid[] recover(int flag, String parentName) throws XAException {
        return new Xid[0];
    }

    @Override
    public SimpleTransactionControl begin(int timeout) throws SystemException {
        int id;
        final ThreadLocalRandom random = ThreadLocalRandom.current();
        final IntIndexMap<HttpRemoteTransactionHandle> map = this.peerTransactionMap;
        HttpRemoteTransactionHandle handle;
        do {
            id = random.nextInt();
        } while (map.containsKey(id) || map.putIfAbsent(handle = new HttpRemoteTransactionHandle(id, targetContext)) != null);
        return handle;
    }

}
