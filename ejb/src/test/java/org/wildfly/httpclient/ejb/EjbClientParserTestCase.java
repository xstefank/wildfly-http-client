package org.wildfly.httpclient.ejb;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;

import org.junit.Assert;
import org.junit.Test;
import org.wildfly.client.config.ConfigXMLParseException;

/**
 * @author Stuart Douglas
 */
public class EjbClientParserTestCase {

    @Test
    public void testXMLParsing() throws URISyntaxException, ConfigXMLParseException {
        EJBHttpContextBuilder builder = EjbHttpClientXmlParser.parseConfig(getClass().getClassLoader().getResource("ejb-client.xml").toURI());
        Assert.assertEquals(InetSocketAddress.createUnresolved("127.0.0.1", 3456), builder.getDefaultBindAddress());

        Assert.assertEquals(10000, builder.getIdleTimeout());
        Assert.assertEquals(1, builder.getMaxConnections());
        Assert.assertEquals(1, builder.getMaxStreamsPerConnection());
        Assert.assertEquals(false, builder.getEagerlyAcquireSession());


        Assert.assertEquals(1, builder.getTargets().size());
        EJBHttpContextBuilder.EJBTargetBuilder context = builder.getTargets().get(0);
        Assert.assertEquals(InetSocketAddress.createUnresolved("127.0.0.1", 5678), context.getBindAddress());
        Assert.assertEquals(30000, context.getIdleTimeout());
        Assert.assertEquals(20, context.getMaxConnections());
        Assert.assertEquals(20, context.getMaxStreamsPerConnection());
        Assert.assertEquals(true, context.getEagerlyAcquireSession());

        Assert.assertEquals(1, context.getUris().size());
        URI host = context.getUris().get(0);
        Assert.assertEquals("http://localhost:8080", host.toString());

    }

}
