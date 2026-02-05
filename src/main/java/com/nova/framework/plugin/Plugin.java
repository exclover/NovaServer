package com.nova.framework.plugin;

import java.util.Set;

/**
 * Core plugin interface
 * All plugins must implement this interface
 * 
 * @since 3.0.0
 */
public interface Plugin {

    /**
     * Unique plugin identifier
     * Must be unique across all plugins
     * 
     * @return plugin ID (e.g., "routing", "websocket")
     */
    String id();

    /**
     * Human-readable plugin name
     * 
     * @return plugin name
     */
    default String name() {
        return id();
    }

    /**
     * Plugin version
     * 
     * @return version string (e.g., "1.0.0")
     */
    default String version() {
        return "1.0.0";
    }

    /**
     * Plugin description
     * 
     * @return description
     */
    default String description() {
        return "";
    }

    /**
     * Whether this plugin is enabled by default
     * Default plugins are automatically registered
     * 
     * @return true if default plugin
     */
    default boolean isDefault() {
        return false;
    }

    /**
     * Plugin execution priority
     * Lower priority values execute first
     * 
     * @return priority
     */
    default PluginPriority priority() {
        return PluginPriority.NORMAL;
    }

    /**
     * Plugin dependencies (IDs of required plugins)
     * These plugins must be registered before this plugin
     * 
     * @return set of plugin IDs this plugin depends on
     */
    default Set<String> dependencies() {
        return Set.of();
    }

    /**
     * Initialize plugin with context
     * Called once when plugin is registered
     * 
     * @param context plugin context
     * @throws PluginException if initialization fails
     */
    void initialize(PluginContext context) throws PluginException;

    /**
     * Start the plugin
     * Called when server starts
     * 
     * @throws PluginException if start fails
     */
    default void start() throws PluginException {
        // Optional override
    }

    /**
     * Stop the plugin
     * Called when server stops
     * 
     * @throws PluginException if stop fails
     */
    default void stop() throws PluginException {
        // Optional override
    }

    /**
     * Get current plugin state
     * 
     * @return current state
     */
    PluginState state();

    /**
     * Set plugin state
     * Internal use only
     * 
     * @param state new state
     */
    void setState(PluginState state);
}
