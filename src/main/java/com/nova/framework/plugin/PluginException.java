package com.nova.framework.plugin;

/**
 * Plugin-related exceptions
 */
public class PluginException extends Exception {

    private final String pluginId;

    public PluginException(String pluginId, String message) {
        super("Plugin '" + pluginId + "': " + message);
        this.pluginId = pluginId;
    }

    public PluginException(String pluginId, String message, Throwable cause) {
        super("Plugin '" + pluginId + "': " + message, cause);
        this.pluginId = pluginId;
    }

    public String getPluginId() {
        return pluginId;
    }

    /**
     * Plugin initialization failed
     */
    public static class InitializationException extends PluginException {
        public InitializationException(String pluginId, String message) {
            super(pluginId, "Initialization failed: " + message);
        }

        public InitializationException(String pluginId, String message, Throwable cause) {
            super(pluginId, "Initialization failed: " + message, cause);
        }
    }

    /**
     * Plugin dependency not found
     */
    public static class DependencyException extends PluginException {
        private final String missingDependency;

        public DependencyException(String pluginId, String missingDependency) {
            super(pluginId, "Missing dependency: " + missingDependency);
            this.missingDependency = missingDependency;
        }

        public String getMissingDependency() {
            return missingDependency;
        }
    }

    /**
     * Circular dependency detected
     */
    public static class CircularDependencyException extends PluginException {
        public CircularDependencyException(String pluginId, String cycle) {
            super(pluginId, "Circular dependency detected: " + cycle);
        }
    }

    /**
     * Plugin already registered
     */
    public static class DuplicatePluginException extends PluginException {
        public DuplicatePluginException(String pluginId) {
            super(pluginId, "Plugin already registered");
        }
    }
}
