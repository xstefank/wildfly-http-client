package org.wildfly.httpclient.ejb;

/**
 * @author Stuart Douglas
 */
class InvocationIdentifier {

    private final String id;
    private final String sessionId;

    InvocationIdentifier(String id, String sessionId) {
        this.id = id;
        this.sessionId = sessionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        InvocationIdentifier that = (InvocationIdentifier) o;

        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        return sessionId != null ? sessionId.equals(that.sessionId) : that.sessionId == null;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (sessionId != null ? sessionId.hashCode() : 0);
        return result;
    }

    public String getId() {
        return id;
    }

    public String getSessionId() {
        return sessionId;
    }
}
