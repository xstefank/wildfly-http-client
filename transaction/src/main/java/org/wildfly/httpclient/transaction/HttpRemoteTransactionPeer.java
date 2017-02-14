package org.wildfly.httpclient.transaction;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import javax.transaction.SystemException;
import javax.transaction.xa.XAException;
import javax.transaction.xa.Xid;

import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.Unmarshaller;
import org.wildfly.httpclient.common.HttpTargetContext;
import org.wildfly.transaction.client.SimpleXid;
import org.wildfly.transaction.client.spi.RemoteTransactionPeer;
import org.wildfly.transaction.client.spi.SimpleTransactionControl;
import org.wildfly.transaction.client.spi.SubordinateTransactionControl;
import io.undertow.client.ClientRequest;
import io.undertow.util.Headers;
import io.undertow.util.Methods;

/**
 * @author Stuart Douglas
 */
public class HttpRemoteTransactionPeer implements RemoteTransactionPeer {
    private final HttpTargetContext targetContext;

    public HttpRemoteTransactionPeer(HttpTargetContext targetContext) {
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
        final CompletableFuture<Xid> beginXid = new CompletableFuture<>();


        targetContext.getConnectionPool().getConnection(connection -> {

            ClientRequest cr = new ClientRequest()
                    .setPath(connection.getUri().getPath() + "/txn/v1/ut/begin")
                    .setMethod(Methods.POST);
            cr.getRequestHeaders().put(Headers.ACCEPT, TransactionHeaders.NEW_TRANSACTION_ACCEPT);

            targetContext.sendRequest(connection, cr, null, (result, response) -> {
                try {
                    Unmarshaller unmarshaller = targetContext.createUnmarshaller(createMarshallingConf());
                    int formatId = unmarshaller.readInt();
                    int len = unmarshaller.readInt();
                    byte[] globalId = new byte[len];
                    unmarshaller.readFully(globalId);
                    len = unmarshaller.readInt();
                    byte[] branchId = new byte[len];
                    unmarshaller.readFully(branchId);
                    SimpleXid simpleXid = new SimpleXid(formatId, globalId, branchId);
                    beginXid.complete(simpleXid);
                } catch (Exception e) {
                    beginXid.completeExceptionally(e);
                }
            }, beginXid::completeExceptionally, TransactionHeaders.NEW_TRANSACTION, null);
        }, beginXid::completeExceptionally, false);
        try {
            Xid xid = beginXid.get();
            return new HttpRemoteTransactionHandle(xid, targetContext);

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            SystemException ex = new SystemException(e.getMessage());
            ex.initCause(e);
            throw ex;
        }
    }

    static MarshallingConfiguration createMarshallingConf() {
        MarshallingConfiguration marshallingConfiguration = new MarshallingConfiguration();
        marshallingConfiguration.setVersion(2);
        return marshallingConfiguration;
    }
}
