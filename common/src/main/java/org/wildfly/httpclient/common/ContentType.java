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

/**
 * @author Stuart Douglas
 */
public class ContentType {

    private final String type;
    private final int version;

    public ContentType(String type, int version) {
        this.type = type;
        this.version = version;
    }

    public String getType() {
        return type;
    }

    public int getVersion() {
        return version;
    }

    public String toString() {
        return type + ";version=" + version;
    }

    public static ContentType parse(String type) {
        if(type == null) {
            return null;
        }
        String[] parts = type.split(";");
        if(parts.length == 0) {
            return null;
        }
        int version = -1;
        for(int i = 1; i < parts.length; ++i) {
            if(parts[i].startsWith("version=")) {
                version = Integer.parseInt(parts[i].substring("version=".length()));
                break;
            }
        }
        return new ContentType(parts[0], version);
    }
}
