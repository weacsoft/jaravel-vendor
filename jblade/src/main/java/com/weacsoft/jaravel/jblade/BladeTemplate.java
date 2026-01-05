package com.weacsoft.jaravel.jblade;

import java.io.StringWriter;
import java.io.Writer;

public abstract class BladeTemplate {
    protected BladeContext context;

    public BladeTemplate() {
        this.context = new BladeContext();
    }

    public abstract void init();

    public abstract void render(Writer writer) throws Exception;

    public String render() throws Exception {
        StringWriter writer = new StringWriter();
        render(writer);
        return writer.toString();
    }

    protected void write(Writer writer, String content) throws Exception {
        writer.write(content);
    }

    protected void write(Writer writer, Object content) throws Exception {
        if (content != null) {
            writer.write(content.toString());
        }
    }

    protected boolean toBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        if (value instanceof String) {
            return !((String) value).isEmpty();
        }
        return true;
    }

    public BladeContext getContext() {
        return context;
    }

    public void setContext(BladeContext context) {
        this.context = context;
    }
}
