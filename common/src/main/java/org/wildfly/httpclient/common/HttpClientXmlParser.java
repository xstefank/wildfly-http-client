/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.httpclient.common;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Collections;

import org.wildfly.client.config.ClientConfiguration;
import org.wildfly.client.config.ConfigXMLParseException;
import org.wildfly.client.config.ConfigurationXMLStreamReader;

/**
 * @author Stuart Douglas
 */
final class HttpClientXmlParser {
    private static final String NS_EJB_HTTP_CLIENT = "urn:wildfly-http-client:1.0";

    static WildflyHttpContext parseHttpContext() throws ConfigXMLParseException, IOException {
        final ClientConfiguration clientConfiguration = ClientConfiguration.getInstance();
        final WildflyHttpContext.Builder builder = new WildflyHttpContext.Builder();
        if (clientConfiguration != null) {
            try (final ConfigurationXMLStreamReader streamReader = clientConfiguration.readConfiguration(Collections.singleton(NS_EJB_HTTP_CLIENT))) {
                parseDocument(streamReader, builder);
            }
        }
        return builder.build();
    }

    //for testing
    static WildflyHttpContext.Builder parseConfig(URI uri) throws ConfigXMLParseException {
        final WildflyHttpContext.Builder builder = new WildflyHttpContext.Builder();
        try (final ConfigurationXMLStreamReader streamReader = ClientConfiguration.getInstance(uri).readConfiguration(Collections.singleton(NS_EJB_HTTP_CLIENT))) {
            parseDocument(streamReader, builder);
            return builder;
        }
    }

    private static void parseDocument(final ConfigurationXMLStreamReader reader, final WildflyHttpContext.Builder builder) throws ConfigXMLParseException {
        if (reader.hasNext()) switch (reader.nextTag()) {
            case START_ELEMENT: {
                switch (reader.getNamespaceURI()) {
                    case NS_EJB_HTTP_CLIENT:
                        break;
                    default:
                        throw reader.unexpectedElement();
                }
                switch (reader.getLocalName()) {
                    case "http-client": {
                        parseRootElement(reader, builder);
                        break;
                    }
                    default:
                        throw reader.unexpectedElement();
                }
                break;
            }
            default: {
                throw reader.unexpectedContent();
            }
        }
    }

    private static void parseRootElement(final ConfigurationXMLStreamReader reader, final WildflyHttpContext.Builder builder) throws ConfigXMLParseException {

        final int attributeCount = reader.getAttributeCount();
        for (int i = 0; i < attributeCount; i++) {
            switch (reader.getAttributeLocalName(i)) {
                default: {
                    throw reader.unexpectedAttribute(i);
                }
            }
        }
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case START_ELEMENT: {
                    switch (reader.getNamespaceURI()) {
                        case NS_EJB_HTTP_CLIENT:
                            break;
                        default:
                            throw reader.unexpectedElement();
                    }
                    switch (reader.getLocalName()) {
                        case "configs": {
                            parseConfigsElement(reader, builder);
                            break;
                        }
                        case "defaults": {
                            parseDefaults(reader, builder);
                            break;
                        }
                        default:
                            throw reader.unexpectedElement();
                    }
                    break;
                }
                case END_ELEMENT: {
                    return;
                }
            }
        }
    }

    private static InetSocketAddress parseBind(final ConfigurationXMLStreamReader reader) throws ConfigXMLParseException {
        final int attributeCount = reader.getAttributeCount();
        String address = null;
        int port = 0;
        for (int i = 0; i < attributeCount; i++) {
            switch (reader.getAttributeLocalName(i)) {
                case "address": {
                    address = reader.getAttributeValue(i);
                    break;
                }
                case "port": {
                    port = reader.getIntAttributeValue(i);
                    if (port < 0 || port > 65535) {
                        throw HttpClientMessages.MESSAGES.portValueOutOfRange(port);
                    }
                    break;
                }
                default: {
                    throw reader.unexpectedAttribute(i);
                }
            }
        }
        if (address == null) {
            throw reader.missingRequiredAttribute(null, "address");
        }
        final InetSocketAddress bindAddress = InetSocketAddress.createUnresolved(address, port);
        switch (reader.nextTag()) {
            case END_ELEMENT: {
                return bindAddress;
            }
            default: {
                throw reader.unexpectedElement();
            }
        }
    }

    private static long parseLongElement(final ConfigurationXMLStreamReader reader) throws ConfigXMLParseException {
        final int attributeCount = reader.getAttributeCount();
        Long value = null;
        for (int i = 0; i < attributeCount; i++) {
            switch (reader.getAttributeLocalName(i)) {
                case "value": {
                    value = reader.getLongAttributeValue(i);
                    break;
                }
                default: {
                    throw reader.unexpectedAttribute(i);
                }
            }
        }
        if (value == null) {
            throw reader.missingRequiredAttribute(null, "value");
        }
        switch (reader.nextTag()) {
            case END_ELEMENT: {
                return value;
            }
            default: {
                throw reader.unexpectedElement();
            }
        }
    }

    private static int parseIntElement(final ConfigurationXMLStreamReader reader) throws ConfigXMLParseException {
        final int attributeCount = reader.getAttributeCount();
        Integer value = null;
        for (int i = 0; i < attributeCount; i++) {
            switch (reader.getAttributeLocalName(i)) {
                case "value": {
                    value = reader.getIntAttributeValue(i);
                    break;
                }
                default: {
                    throw reader.unexpectedAttribute(i);
                }
            }
        }
        if (value == null) {
            throw reader.missingRequiredAttribute(null, "value");
        }
        switch (reader.nextTag()) {
            case END_ELEMENT: {
                return value;
            }
            default: {
                throw reader.unexpectedElement();
            }
        }
    }

    private static boolean parseBooleanElement(final ConfigurationXMLStreamReader reader) throws ConfigXMLParseException {
        final int attributeCount = reader.getAttributeCount();
        Boolean value = null;
        for (int i = 0; i < attributeCount; i++) {
            switch (reader.getAttributeLocalName(i)) {
                case "value": {
                    value = reader.getBooleanAttributeValue(i);
                    break;
                }
                default: {
                    throw reader.unexpectedAttribute(i);
                }
            }
        }
        if (value == null) {
            throw reader.missingRequiredAttribute(null, "value");
        }
        switch (reader.nextTag()) {
            case END_ELEMENT: {
                return value;
            }
            default: {
                throw reader.unexpectedElement();
            }
        }
    }

    private static void parseConfigsElement(final ConfigurationXMLStreamReader reader, final WildflyHttpContext.Builder builder) throws ConfigXMLParseException {
        final int attributeCount = reader.getAttributeCount();
        if (attributeCount > 0) {
            throw reader.unexpectedAttribute(0);
        }
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case START_ELEMENT: {
                    switch (reader.getNamespaceURI()) {
                        case NS_EJB_HTTP_CLIENT:
                            break;
                        default:
                            throw reader.unexpectedElement();
                    }
                    switch (reader.getLocalName()) {
                        case "config": {
                            parseConfig(reader, builder);
                            break;
                        }
                        default:
                            throw reader.unexpectedElement();
                    }
                    break;
                }
                case END_ELEMENT: {
                    return;
                }
            }
        }
    }

    private static void parseDefaults(final ConfigurationXMLStreamReader reader, final WildflyHttpContext.Builder builder) throws ConfigXMLParseException {
        if (reader.getAttributeCount() > 0) {
            throw reader.unexpectedAttribute(0);
        }
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case START_ELEMENT: {
                    switch (reader.getNamespaceURI()) {
                        case NS_EJB_HTTP_CLIENT:
                            break;
                        default:
                            throw reader.unexpectedElement();
                    }
                    switch (reader.getLocalName()) {
                        case "bind-address": {
                            builder.setDefaultBindAddress(parseBind(reader));
                            break;
                        }
                        case "idle-timeout": {
                            builder.setIdleTimeout(parseLongElement(reader));
                            break;
                        }
                        case "max-connections": {
                            builder.setMaxConnections(parseIntElement(reader));
                            break;
                        }
                        case "max-streams-per-connection": {
                            builder.setMaxStreamsPerConnection(parseIntElement(reader));
                            break;
                        }
                        case "eagerly-acquire-session": {
                            builder.setEagerlyAcquireSession(parseBooleanElement(reader));
                            break;
                        }
                        case "enable-http2": {
                            builder.setEnableHttp2(parseBooleanElement(reader));
                            break;
                        }
                        case "buffer-pool": {
                            builder.setBufferConfig(parseBufferConfig(reader));
                            break;
                        }
                        default:
                            throw reader.unexpectedElement();
                    }
                    break;
                }
                case END_ELEMENT: {
                    return;
                }
            }
        }
    }

    private static WildflyHttpContext.BufferBuilder parseBufferConfig(final ConfigurationXMLStreamReader reader) throws ConfigXMLParseException {
        final int attributeCount = reader.getAttributeCount();
        Integer bufferSize = null;
        Integer maxSize = null;
        Integer threadLocalSize = null;
        Boolean direct = null;
        for (int i = 0; i < attributeCount; i++) {
            switch (reader.getAttributeLocalName(i)) {
                case "buffer-size": {
                    bufferSize = reader.getIntAttributeValue(i);
                    break;
                }
                case "max-size": {
                    maxSize = reader.getIntAttributeValue(i);
                    break;
                }
                case "thread-local-size": {
                    threadLocalSize = reader.getIntAttributeValue(i);
                    break;
                }
                case "direct": {
                    direct = reader.getBooleanAttributeValue(i);
                    break;
                }
                default: {
                    throw reader.unexpectedAttribute(i);
                }
            }
        }
        if (bufferSize == null) {
            throw reader.missingRequiredAttribute(null, "value");
        }
        WildflyHttpContext.BufferBuilder value = new WildflyHttpContext.BufferBuilder();
        value.setBufferSize(bufferSize);
        if(maxSize != null) {
            value.setMaxSize(maxSize);
        }
        if(threadLocalSize != null) {
            value.setThreadLocalSize(threadLocalSize);
        }
        if(direct != null) {
            value.setDirect( direct);
        }
        switch (reader.nextTag()) {
            case END_ELEMENT: {
                return value;
            }
            default: {
                throw reader.unexpectedElement();
            }
        }
    }
    private static void parseConfig(final ConfigurationXMLStreamReader reader, final WildflyHttpContext.Builder builder) throws ConfigXMLParseException {

        final int attributeCount = reader.getAttributeCount();
        URI uri = null;
        for (int i = 0; i < attributeCount; i++) {
            switch (reader.getAttributeLocalName(i)) {
                case "uri": {
                    uri = reader.getURIAttributeValue(i);
                    break;
                }
                default: {
                    throw reader.unexpectedAttribute(i);
                }
            }
        }
        if (uri == null) {
            throw reader.missingRequiredAttribute(null, "uri");
        }
        final WildflyHttpContext.Builder.HttpConfigBuilder targetBuilder = builder.addConfig(uri);

        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case START_ELEMENT: {
                    switch (reader.getNamespaceURI()) {
                        case NS_EJB_HTTP_CLIENT:
                            break;
                        default:
                            throw reader.unexpectedElement();
                    }
                    switch (reader.getLocalName()) {
                        case "idle-timeout": {
                            targetBuilder.setIdleTimeout(parseLongElement(reader));
                            break;
                        }
                        case "max-connections": {
                            targetBuilder.setMaxConnections(parseIntElement(reader));
                            break;
                        }
                        case "max-streams-per-connection": {
                            targetBuilder.setMaxStreamsPerConnection(parseIntElement(reader));
                            break;
                        }
                        case "eagerly-acquire-session": {
                            targetBuilder.setEagerlyAcquireSession(parseBooleanElement(reader));
                            break;
                        }
                        case "enable-http2": {
                            targetBuilder.setEnableHttp2(parseBooleanElement(reader));
                            break;
                        }
                        case "bind-address": {
                            targetBuilder.setBindAddress(parseBind(reader));
                            break;
                        }
                        default:
                            throw reader.unexpectedElement();
                    }
                    break;
                }
                case END_ELEMENT: {
                    return;
                }
            }
        }
    }

}
