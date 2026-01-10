package com.weacsoft.jaravel.jblade;

import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.function.Consumer;

public class BladeContext {
    private final Map<String, Object> variables;
    private final Map<String, String> sections;
    private final Map<String, Consumer<Writer>> sectionRenderers;
    private final Stack<String> sectionStack;
    private final Map<String, Object> componentData;
    private final Map<String, String> componentSlots;
    private StringBuilder currentSectionContent;
    private String parentTemplate;
    private boolean inSection;
    private String currentComponent;
    private String currentSlot;
    private StringBuilder currentSlotContent;
    private boolean inSlot;

    public BladeContext() {
        this.variables = new HashMap<>();
        this.sections = new HashMap<>();
        this.sectionRenderers = new HashMap<>();
        this.sectionStack = new Stack<>();
        this.currentSectionContent = new StringBuilder();
        this.inSection = false;
        this.componentData = new HashMap<>();
        this.componentSlots = new HashMap<>();
        this.currentSlotContent = new StringBuilder();
        this.inSlot = false;
    }

    public void setVariable(String name, Object value) {
        variables.put(name, value);
    }

    public Object getVariable(String name) {
        return variables.get(name);
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setSection(String name, String content) {
        sections.put(name, content);
    }

    public String getSection(String name) {
        return sections.get(name);
    }

    public Map<String, String> getSections() {
        return sections;
    }

    public void setSectionRenderer(String name, Consumer<Writer> renderer) {
        sectionRenderers.put(name, renderer);
    }

    public Consumer<Writer> getSectionRenderer(String name) {
        return sectionRenderers.get(name);
    }

    public Map<String, Consumer<Writer>> getSectionRenderers() {
        return sectionRenderers;
    }

    public void reset() {
        variables.clear();
        sections.clear();
        sectionRenderers.clear();
        sectionStack.clear();
        componentData.clear();
        componentSlots.clear();
        currentSectionContent = new StringBuilder();
        parentTemplate = null;
        inSection = false;
        currentComponent = null;
        currentSlot = null;
        currentSlotContent = new StringBuilder();
        inSlot = false;
    }

    public void startSection(String name) {
        sectionStack.push(name);
        currentSectionContent = new StringBuilder();
        inSection = true;
    }

    public void appendSectionContent(String content) {
        if (inSection) {
            currentSectionContent.append(content);
        }
    }

    public void endSection() {
        if (!sectionStack.isEmpty()) {
            String name = sectionStack.pop();
            sections.put(name, currentSectionContent.toString());
            inSection = !sectionStack.isEmpty();
            if (inSection) {
                currentSectionContent = new StringBuilder(sections.get(sectionStack.peek()));
            }
        }
    }

    public String getCurrentSectionName() {
        return sectionStack.isEmpty() ? null : sectionStack.peek();
    }

    public String getParentTemplate() {
        return parentTemplate;
    }

    public void setParentTemplate(String parentTemplate) {
        this.parentTemplate = parentTemplate;
    }

    public boolean isInSection() {
        return inSection;
    }

    public String getCurrentSectionContent() {
        return currentSectionContent.toString();
    }

    public void setCurrentSectionContent(String content) {
        currentSectionContent = new StringBuilder(content);
    }

    public void startComponent(String componentName) {
        this.currentComponent = componentName;
        this.componentSlots.clear();
        this.currentSlot = null;
        this.currentSlotContent = new StringBuilder();
        this.inSlot = false;
    }

    public void endComponent() {
        this.currentComponent = null;
    }

    public String getCurrentComponent() {
        return currentComponent;
    }

    public void setComponentData(String key, Object value) {
        componentData.put(key, value);
    }

    public Object getComponentData(String key) {
        return componentData.get(key);
    }

    public Map<String, Object> getComponentData() {
        return componentData;
    }

    public void clearComponentData() {
        componentData.clear();
    }

    public void startSlot(String slotName) {
        this.currentSlot = slotName;
        this.currentSlotContent = new StringBuilder();
        this.inSlot = true;
    }

    public void endSlot() {
        if (currentSlot != null && inSlot) {
            componentSlots.put(currentSlot, currentSlotContent.toString());
            currentSlot = null;
            currentSlotContent = new StringBuilder();
            inSlot = false;
        }
    }

    public void appendSlotContent(String content) {
        if (inSlot) {
            currentSlotContent.append(content);
        }
    }

    public String getSlot(String slotName) {
        return componentSlots.get(slotName);
    }

    public Map<String, String> getComponentSlots() {
        return componentSlots;
    }

    public boolean isInSlot() {
        return inSlot;
    }

    public String getCurrentSlot() {
        return currentSlot;
    }
}
