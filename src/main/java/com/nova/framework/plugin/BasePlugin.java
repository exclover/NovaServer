package com.nova.framework.plugin;

import java.util.Set;

/**
 * Abstract base plugin with state management
 * Extend this for simpler plugin implementation
 */
public abstract class BasePlugin implements Plugin {

    private PluginState state = PluginState.REGISTERED;
    protected PluginContext context;

    @Override
    public final PluginState state() {
        return state;
    }

    @Override
    public final void setState(PluginState state) {
        this.state = state;
    }

    @Override
    public Set<String> dependencies() {
        return Set.of(); // No dependencies by default
    }

    @Override
    public boolean isDefault() {
        return false; // Not default by default
    }

    @Override
    public PluginPriority priority() {
        return PluginPriority.NORMAL;
    }

    @Override
    public String version() {
        return "1.0.0";
    }

    @Override
    public String description() {
        return "";
    }

    @Override
    public String name() {
        return id();
    }
}
