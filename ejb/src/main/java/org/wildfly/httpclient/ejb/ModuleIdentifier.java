package org.wildfly.httpclient.ejb;

/**
 * @author Stuart Douglas
 */
class ModuleIdentifier {

    private final String app, module, distinct;

    ModuleIdentifier(String app, String module, String distinct) {
        this.app = app;
        this.module = module;
        this.distinct = distinct;
    }

    public String getApp() {
        return app;
    }

    public String getModule() {
        return module;
    }

    public String getDistinct() {
        return distinct;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ModuleIdentifier that = (ModuleIdentifier) o;

        if (app != null ? !app.equals(that.app) : that.app != null) return false;
        if (module != null ? !module.equals(that.module) : that.module != null) return false;
        return distinct != null ? distinct.equals(that.distinct) : that.distinct == null;
    }

    @Override
    public int hashCode() {
        int result = app != null ? app.hashCode() : 0;
        result = 31 * result + (module != null ? module.hashCode() : 0);
        result = 31 * result + (distinct != null ? distinct.hashCode() : 0);
        return result;
    }
}
