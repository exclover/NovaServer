package com.nova.framework.plugins;

import com.nova.framework.plugin.*;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.security.KeyStore;

/**
 * SSL/TLS Plugin - OPTIONAL
 * Provides HTTPS support
 */
public final class SSLPlugin extends BasePlugin {

    private SSLContext sslContext;
    @SuppressWarnings("unused")
    private String keystorePath;
    @SuppressWarnings("unused")
    private String keystorePassword;

    @Override
    public String id() {
        return "ssl";
    }

    @Override
    public boolean isDefault() {
        return false; // Optional
    }

    @Override
    public PluginPriority priority() {
        return PluginPriority.HIGHEST; // Must initialize first
    }

    @Override
    public void initialize(PluginContext context) throws PluginException {
        this.context = context;
        context.log("SSL plugin initialized");
    }

    // ========== PUBLIC API ==========

    /**
     * Configure SSL with keystore
     */
    public SSLPlugin configure(String keystorePath, String keystorePassword) {
        this.keystorePath = keystorePath;
        this.keystorePassword = keystorePassword;

        try {
            // Load keystore
            KeyStore keyStore = KeyStore.getInstance("JKS");
            try (FileInputStream fis = new FileInputStream(keystorePath)) {
                keyStore.load(fis, keystorePassword.toCharArray());
            }

            // Initialize key manager
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, keystorePassword.toCharArray());

            // Initialize trust manager
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);

            // Create SSL context
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

            context.log("SSL configured successfully");

        } catch (Exception e) {
            throw new RuntimeException("Failed to configure SSL", e);
        }

        return this;
    }

    /**
     * Create SSL server socket
     */
    public ServerSocket createServerSocket(int port) throws IOException {
        if (sslContext == null) {
            throw new IllegalStateException("SSL not configured");
        }

        SSLServerSocketFactory factory = sslContext.getServerSocketFactory();
        SSLServerSocket serverSocket = (SSLServerSocket) factory.createServerSocket(port);

        // Enable modern protocols
        serverSocket.setEnabledProtocols(new String[] { "TLSv1.2", "TLSv1.3" });

        return serverSocket;
    }

    /**
     * Check if SSL is configured
     */
    public boolean isConfigured() {
        return sslContext != null;
    }
}
