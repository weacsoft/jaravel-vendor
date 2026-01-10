package com.weacsoft.jaravel.jblade;

import java.io.StringWriter;
import java.io.Writer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class BladeTemplate {
    protected BladeContext context;
    private BladeEngine engine;
    private volatile boolean initialized = false;

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

    public void setEngine(BladeEngine engine) {
        this.engine = engine;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    public void resetContext() {
        this.context = new BladeContext();
        this.initialized = false;
    }

    public void resetContext(BladeContext newContext) {
        this.context = newContext;
        this.initialized = false;
    }

    protected void renderComponent(Writer writer, String componentName, Map<String, Object> data, Map<String, String> slots) throws Exception {
        if (engine == null) {
            throw new IllegalStateException("BladeEngine not set for template");
        }

        String prevComponent = context.getCurrentComponent();
        Map<String, String> prevSlots = new ConcurrentHashMap<>(context.getComponentSlots());

        context.startComponent(componentName);

        if (data != null) {
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                context.setComponentData(entry.getKey(), entry.getValue());
            }
        }

        if (slots != null) {
            for (Map.Entry<String, String> entry : slots.entrySet()) {
                context.getComponentSlots().put(entry.getKey(), entry.getValue());
            }
        }

        BladeTemplate componentTemplate = engine.loadTemplate(componentName);
        BladeContext componentCtx = componentTemplate.getContext();

        componentCtx.setVariable("$slot", context.getSlot("default"));
        for (Map.Entry<String, String> slotEntry : context.getComponentSlots().entrySet()) {
            componentCtx.setVariable("$" + slotEntry.getKey(), slotEntry.getValue());
        }
        for (Map.Entry<String, Object> dataEntry : context.getComponentData().entrySet()) {
            componentCtx.setVariable(dataEntry.getKey(), dataEntry.getValue());
        }

        componentTemplate.render(writer);

        context.endComponent();
        context.getComponentSlots().clear();
        context.getComponentSlots().putAll(prevSlots);
        context.clearComponentData();
    }
}
