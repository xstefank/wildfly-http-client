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
