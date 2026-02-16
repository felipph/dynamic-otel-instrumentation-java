package com.otel.dynamic.config;

import com.otel.dynamic.util.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Monitors the instrumentation configuration file for changes using Java NIO WatchService.
 *
 * Implements debouncing to avoid multiple reload triggers from a single file edit.
 * When changes are detected, notifies all registered listeners.
 *
 * HIST-02: Watchdog & Retransformation - File monitoring component
 */
public class ConfigurationWatcher implements Runnable {

    private static final long DEBOUNCE_DELAY_MS = 500;
    private static final long WATCH_SERVICE_POLL_TIMEOUT_MS = 1000;

    private final WatchService watchService;
    private final Path configDir;
    private final String configFileName;
    private final Map<ConfigurationChangeListener, Boolean> listeners;

    private volatile long lastModifiedTime = 0;
    private volatile boolean running = false;
    private Thread watchThread;
    private ScheduledExecutorService debounceExecutor;

    /**
     * Interface for listeners interested in configuration file changes
     */
    public interface ConfigurationChangeListener {
        /**
         * Called when the configuration file is modified
         */
        void onConfigurationChanged();
    }

    /**
     * Create a watcher for the configuration file at the given path
     *
     * @param configFilePath full path to the configuration file
     * @throws IOException if the watch service cannot be initialized
     */
    public ConfigurationWatcher(String configFilePath) throws IOException {
        this.watchService = FileSystems.getDefault().newWatchService();
        this.listeners = new HashMap<>();

        File configFile = new File(configFilePath);
        this.configDir = configFile.getParentFile().toPath();
        this.configFileName = configFile.getName();

        // Register the directory for watch events
        this.configDir.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);

        Logger.info("ConfigurationWatcher initialized for: " + configFilePath);
    }

    /**
     * Add a listener to be notified of configuration changes
     */
    public void addListener(ConfigurationChangeListener listener) {
        if (listener != null) {
            listeners.put(listener, true);
        }
    }

    /**
     * Remove a listener
     */
    public void removeListener(ConfigurationChangeListener listener) {
        listeners.remove(listener);
    }

    /**
     * Start watching for configuration file changes
     */
    public void start() {
        if (running) {
            Logger.warn("ConfigurationWatcher is already running");
            return;
        }

        running = true;
        debounceExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ConfigWatcher-Debounce");
            t.setDaemon(true);
            return t;
        });

        watchThread = new Thread(this, "ConfigWatcher");
        watchThread.setDaemon(true);
        watchThread.start();

        Logger.info("ConfigurationWatcher started");
    }

    /**
     * Stop watching for configuration file changes
     */
    public void stop() {
        running = false;

        // Close the watch service to interrupt the watch thread
        try {
            if (watchService != null) {
                watchService.close();
            }
        } catch (IOException e) {
            Logger.debug("Error closing watch service", e);
        }

        // Shutdown the debounce executor
        if (debounceExecutor != null) {
            debounceExecutor.shutdownNow();
            debounceExecutor = null;
        }

        Logger.info("ConfigurationWatcher stopped");
    }

    @Override
    public void run() {
        Logger.info("ConfigurationWatcher thread started, monitoring: " + configFileName);

        try {
            WatchKey key;
            while ((key = watchService.poll(WATCH_SERVICE_POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS)) != null && running) {
                for (WatchEvent<?> event : key.pollEvents()) {
                    handleWatchEvent(event);
                }
                key.reset();
            }
        } catch (ClosedWatchServiceException e) {
            Logger.info("WatchService closed, stopping watcher");
        } catch (InterruptedException e) {
            Logger.info("ConfigurationWatcher interrupted");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            Logger.error("Unexpected error in ConfigurationWatcher", e);
        } finally {
            Logger.info("ConfigurationWatcher thread exiting");
        }
    }

    /**
     * Handle a watch service event
     */
    private void handleWatchEvent(WatchEvent<?> event) {
        WatchEvent.Kind<?> kind = event.kind();

        if (kind == StandardWatchEventKinds.OVERFLOW) {
            Logger.warn("WatchService overflow event detected");
            return;
        }

        @SuppressWarnings("unchecked")
        WatchEvent<Path> ev = (WatchEvent<Path>) event;
        Path filename = ev.context();

        // Only process events for our configuration file
        if (filename.toString().equals(configFileName)) {
            Logger.debug("File event detected: " + kind + " for " + filename);

            if (kind == StandardWatchEventKinds.ENTRY_CREATE ||
                kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                handleFileChange();
            } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                Logger.warn("Configuration file deleted: " + filename);
            }
        }
    }

    /**
     * Handle a configuration file change with debouncing
     */
    private void handleFileChange() {
        long now = System.currentTimeMillis();
        lastModifiedTime = now;

        Logger.debug("Scheduling configuration reload after debounce delay");

        // Schedule the reload with debouncing
        debounceExecutor.schedule(() -> {
            // Only reload if no new changes occurred during the debounce period
            if (lastModifiedTime == now) {
                Logger.info("Configuration file changed, triggering reload");
                notifyListeners();
            } else {
                Logger.debug("Debounce: Additional changes detected, skipping this reload");
            }
        }, DEBOUNCE_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Notify all registered listeners of the configuration change
     */
    private void notifyListeners() {
        for (ConfigurationChangeListener listener : listeners.keySet()) {
            try {
                listener.onConfigurationChanged();
            } catch (Exception e) {
                Logger.error("Error notifying configuration change listener", e);
            }
        }
    }

    /**
     * Check if the watcher is currently running
     */
    public boolean isRunning() {
        return running;
    }
}
