package org.wildfly.httpclient.naming;

import java.security.Principal;

import org.wildfly.security.auth.client.PeerIdentity;

/**
 * @author Stuart Douglas
 */
public class HttpPeerIdentity extends PeerIdentity {
    protected HttpPeerIdentity(Configuration configuration, Principal peerPrincipal) {
        super(configuration, peerPrincipal);
    }
}
