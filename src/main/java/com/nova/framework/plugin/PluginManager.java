package com.nova.framework.plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Professional plugin manager with dependency resolution
 * Thread-safe and allows dynamic plugin registration
 */
public final class PluginManager {

    private final Map<String, Plugin> plugins = new ConcurrentHashMap<>();
    private final List<Plugin> initializationOrder = new ArrayList<>();
    private volatile PluginContext context = null;
    private volatile boolean serverStarted = false;

    /**
     * Register a plugin
     */
    public synchronized void register(Plugin plugin) throws PluginException {
        if (serverStarted) {
            throw new IllegalStateException("Cannot register plugins after server has started");
        }

        if (plugins.containsKey(plugin.id())) {
            throw new PluginException.DuplicatePluginException(plugin.id());
        }

        plugins.put(plugin.id(), plugin);
        plugin.setState(PluginState.REGISTERED);
    }

    /**
     * Initialize a single plugin (can be called after initial batch)
     */
    public synchronized void initialize(Plugin plugin, PluginContext context) throws PluginException {
        if (serverStarted) {
            throw new IllegalStateException("Cannot initialize plugins after server has started");
        }
        initializePlugin(plugin, context);
    }

    /**
     * Initialize all plugins with dependency resolution
     */
    public synchronized void initializeAll(PluginContext context) throws PluginException {
        if (this.context != null) {
            throw new IllegalStateException("Plugins already initialized");
        }

        this.context = context;

        // Get all uninitialized plugins
        List<Plugin> toInitialize = plugins.values().stream()
                .filter(p -> p.state() == PluginState.REGISTERED)
                .collect(Collectors.toList());

        // Resolve dependencies and get initialization order
        List<Plugin> ordered = resolveDependencies(toInitialize);

        // Initialize in dependency order
        for (Plugin plugin : ordered) {
            initializePlugin(plugin, context);
        }
    }

    /**
     * Start all plugins
     */
    public synchronized void startAll() throws PluginException {
        if (context == null) {
            throw new IllegalStateException("Plugins not initialized");
        }

        serverStarted = true;

        for (Plugin plugin : initializationOrder) {
            if (plugin.state() == PluginState.INITIALIZED) {
                try {
                    plugin.start();
                    plugin.setState(PluginState.STARTED);
                } catch (Exception e) {
                    plugin.setState(PluginState.FAILED);
                    throw new PluginException(plugin.id(), "Failed to start", e);
                }
            }
        }
    }

    /**
     * Stop all plugins (reverse order)
     */
    public synchronized void stopAll() {
        if (!serverStarted)
            return;

        List<Plugin> reversed = new ArrayList<>(initializationOrder);
        Collections.reverse(reversed);

        for (Plugin plugin : reversed) {
            try {
                plugin.stop();
                plugin.setState(PluginState.STOPPED);
            } catch (Exception e) {
                System.err.println("Error stopping plugin " + plugin.id() + ": " + e.getMessage());
            }
        }

        serverStarted = false;
    }

    /**
     * Get plugin by ID and type
     */
    @SuppressWarnings("unchecked")
    public <T extends Plugin> T getPlugin(String pluginId, Class<T> pluginClass) {
        Plugin plugin = plugins.get(pluginId);
        if (plugin != null && pluginClass.isInstance(plugin)) {
            return (T) plugin;
        }
        return null;
    }

    /**
     * Check if plugin exists
     */
    public boolean hasPlugin(String pluginId) {
        return plugins.containsKey(pluginId);
    }

    /**
     * Get all plugins
     */
    public Collection<Plugin> getAllPlugins() {
        return Collections.unmodifiableCollection(plugins.values());
    }

    /**
     * Get plugins in execution order (sorted by priority)
     */
    public List<Plugin> getPluginsByPriority() {
        return plugins.values().stream()
                .sorted(Comparator.comparing(p -> p.priority().getValue()))
                .collect(Collectors.toUnmodifiableList());
    }

    // ========== PRIVATE METHODS ==========

    private void initializePlugin(Plugin plugin, PluginContext context) throws PluginException {
        if (plugin.state() == PluginState.INITIALIZED || plugin.state() == PluginState.STARTED) {
            return;
        }

        if (plugin.state() == PluginState.INITIALIZING) {
            throw new PluginException.CircularDependencyException(
                    plugin.id(),
                    "Plugin is already being initialized");
        }

        // Check dependencies are initialized
        for (String depId : plugin.dependencies()) {
            Plugin dependency = plugins.get(depId);
            if (dependency == null) {
                throw new PluginException.DependencyException(plugin.id(), depId);
            }
            if (dependency.state() == PluginState.REGISTERED) {
                // Initialize dependency first
                initializePlugin(dependency, context);
            }
        }

        plugin.setState(PluginState.INITIALIZING);

        try {
            plugin.initialize(context);
            plugin.setState(PluginState.INITIALIZED);
            
            // Add to initialization order if not already present
            if (!initializationOrder.contains(plugin)) {
                initializationOrder.add(plugin);
            }
        } catch (Exception e) {
            plugin.setState(PluginState.FAILED);
            throw new PluginException.InitializationException(plugin.id(), e.getMessage(), e);
        }
    }

    private List<Plugin> resolveDependencies(List<Plugin> pluginsToResolve) throws PluginException {
        List<Plugin> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();

        for (Plugin plugin : pluginsToResolve) {
            if (!visited.contains(plugin.id())) {
                visitPlugin(plugin, visited, visiting, result);
            }
        }

        return result;
    }

    private void visitPlugin(
            Plugin plugin,
            Set<String> visited,
            Set<String> visiting,
            List<Plugin> result) throws PluginException {
        String pluginId = plugin.id();

        if (visiting.contains(pluginId)) {
            throw new PluginException.CircularDependencyException(pluginId, "Circular dependency detected");
        }

        if (visited.contains(pluginId)) {
            return;
        }

        visiting.add(pluginId);

        // Visit dependencies first
        for (String depId : plugin.dependencies()) {
            Plugin dependency = plugins.get(depId);

            if (dependency == null) {
                throw new PluginException.DependencyException(pluginId, depId);
            }

            visitPlugin(dependency, visited, visiting, result);
        }

        visiting.remove(pluginId);
        visited.add(pluginId);
        result.add(plugin);
    }

    public int pluginCount() {
        return plugins.size();
    }

    public boolean isInitialized() {
        return context != null;
    }

    public boolean isStarted() {
        return serverStarted;
    }
}
