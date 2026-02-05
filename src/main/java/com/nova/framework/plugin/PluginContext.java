package com.nova.framework.plugin;

import com.nova.framework.config.NovaConfig;

import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Context provided to plugins during initialization
 * Provides access to server resources and services
 * 
 * @param config        Server configuration (immutable)
 * @param executor      Executor service for async tasks
 * @param logger        Logging function
 * @param pluginManager Plugin manager for accessing other plugins
 */
public record PluginContext(
        NovaConfig config,
        ExecutorService executor,
        Consumer<LogMessage> logger,
        PluginManager pluginManager) {

    /**
     * Log an info message
     */
    public void log(String message) {
        logger.accept(new LogMessage(LogLevel.INFO, message, null));
    }

    /**
     * Log a warning
     */
    public void warn(String message) {
        logger.accept(new LogMessage(LogLevel.WARN, message, null));
    }

    /**
     * Log an error
     */
    public void error(String message, Throwable throwable) {
        logger.accept(new LogMessage(LogLevel.ERROR, message, throwable));
    }

    /**
     * Get another plugin by ID
     */
    public <T extends Plugin> T getPlugin(String pluginId, Class<T> pluginClass) {
        return pluginManager.getPlugin(pluginId, pluginClass);
    }

    /**
     * Check if a plugin is registered
     */
    public boolean hasPlugin(String pluginId) {
        return pluginManager.hasPlugin(pluginId);
    }

    /**
     * Log message record
     */
    public record LogMessage(LogLevel level, String message, Throwable throwable) {
    }

    /**
     * Log levels
     */
    public enum LogLevel {
        DEBUG, INFO, WARN, ERROR
    }
}
