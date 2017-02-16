package org.wildfly.httpclient.naming;

import org.wildfly.security.auth.client.PeerIdentity;

import java.security.Principal;

/**
 * @author Stuart Douglas
 */
public class HttpPeerIdentity extends PeerIdentity {
    protected HttpPeerIdentity(Configuration configuration, Principal peerPrincipal) {
        super(configuration, peerPrincipal);
    }
}
