package org.wildfly.httpclient.transaction;

import javax.transaction.xa.Xid;

/**
 * Gets the Xid of the associated transaction handle
 *
 * @author Stuart Douglas
 */
public interface XidProvider {

    Xid getXid();

}
