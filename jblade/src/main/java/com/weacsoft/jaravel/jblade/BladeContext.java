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
    private StringBuilder currentSectionContent;
    private String parentTemplate;
    private boolean inSection;

    public BladeContext() {
        this.variables = new HashMap<>();
        this.sections = new HashMap<>();
        this.sectionRenderers = new HashMap<>();
        this.sectionStack = new Stack<>();
        this.currentSectionContent = new StringBuilder();
        this.inSection = false;
    }

    public void setVariable(String name, Object value) {
        variables.put(name, value);
    }

    public Object getVariable(String name) {
        return variables.get(name);
    }

    public void setSection(String name, String content) {
        sections.put(name, content);
    }

    public String getSection(String name) {
        return sections.get(name);
    }

    public void setSectionRenderer(String name, Consumer<Writer> renderer) {
        sectionRenderers.put(name, renderer);
    }

    public Consumer<Writer> getSectionRenderer(String name) {
        return sectionRenderers.get(name);
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
}
