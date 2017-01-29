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
        HttpContextBuilder builder = EjbHttpClientXmlParser.parseConfig(getClass().getClassLoader().getResource("ejb-client.xml").toURI());
        Assert.assertEquals(InetSocketAddress.createUnresolved("127.0.0.1", 3456), builder.getDefaultBindAddress());
        Assert.assertEquals(1, builder.getConnections().size());
        HttpContextBuilder.ConnectionBuilder connection = builder.getConnections().get(0);
        Assert.assertEquals(InetSocketAddress.createUnresolved("127.0.0.1", 5678), connection.getBindAddress());
        Assert.assertEquals(30000, connection.getIdleTimeout());
        Assert.assertEquals(20, connection.getMaxConnections());
        Assert.assertEquals(20, connection.getMaxStreamsPerConnection());

        Assert.assertEquals(1, connection.getModules().size());
        HttpContext.Module module = connection.getModules().get(0);
        Assert.assertEquals("myapp", module.getApp().toString());
        Assert.assertEquals("mymodule", module.getModule().toString());
        Assert.assertEquals("distinct", module.getDistinct().toString());

        Assert.assertEquals(1, connection.getUris().size());
        URI host = connection.getUris().get(0);
        Assert.assertEquals("http://localhost:8080", host.toString());

    }

}
