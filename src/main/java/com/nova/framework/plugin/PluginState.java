package com.nova.framework.plugin;

/**
 * Plugin lifecycle states
 */
public enum PluginState {
    /** Plugin registered but not initialized */
    REGISTERED,

    /** Plugin is being initialized */
    INITIALIZING,

    /** Plugin initialization complete */
    INITIALIZED,

    /** Plugin is running */
    STARTED,

    /** Plugin has been stopped */
    STOPPED,

    /** Plugin failed during initialization or runtime */
    FAILED
}
