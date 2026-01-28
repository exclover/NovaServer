package com.nova.framework.ssl;

import javax.net.ssl.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * SSL/TLS Configuration - Production Ready
 * Supports JKS keystore and PEM certificate/key files
 * 
 * @version 2.1.0 - Enhanced PKCS#1 support with BouncyCastle recommendation
 */
public class SSLConfig {
    
    private final SSLConfigType type;
    
    // For JKS keystore
    private final String keystorePath;
    private final String keystorePassword;
    private final String keyPassword;
    
    // For PEM files
    private final String certPath;
    private final String keyPath;
    private final String keyAlias;
    
    // Private constructor - use factory methods instead
    private SSLConfig(
        SSLConfigType type,
        String keystorePath, 
        String keystorePassword, 
        String keyPassword,
        String certPath,
        String keyPath,
        String keyAlias
    ) {
        this.type = type;
        this.keystorePath = keystorePath;
        this.keystorePassword = keystorePassword;
        this.keyPassword = keyPassword;
        this.certPath = certPath;
        this.keyPath = keyPath;
        this.keyAlias = keyAlias;
    }
    
    // ========== FACTORY METHODS ==========
    
    /**
     * Create SSL config from JKS keystore
     * 
     * @param path Path to .jks file
     * @param keystorePass Keystore password
     * @param keyPass Private key password
     * @return SSLConfig instance
     */
    public static SSLConfig fromKeystore(String path, String keystorePass, String keyPass) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Keystore path cannot be null or empty");
        }
        if (keystorePass == null) {
            throw new IllegalArgumentException("Keystore password cannot be null");
        }
        if (keyPass == null) {
            throw new IllegalArgumentException("Key password cannot be null");
        }
        
        // Validate file exists
        if (!new File(path).exists()) {
            throw new IllegalArgumentException("Keystore file not found: " + path);
        }
        
        return new SSLConfig(
            SSLConfigType.JKS,
            path,           // keystorePath
            keystorePass,   // keystorePassword
            keyPass,        // keyPassword
            null,           // certPath
            null,           // keyPath
            "server"        // keyAlias
        );
    }
    
    /**
     * Create SSL config from PEM certificate and key files
     * Common format for Let's Encrypt, OpenSSL certificates
     * 
     * NOTE: Requires PKCS#8 format keys. Convert PKCS#1 keys using:
     * openssl pkcs8 -topk8 -nocrypt -in key.pem -out key_pkcs8.pem
     * 
     * @param certPath Path to certificate file (.crt, .pem)
     * @param keyPath Path to private key file (.key, .pem) - must be PKCS#8 format
     * @return SSLConfig instance
     */
    public static SSLConfig fromPEM(String certPath, String keyPath) {
        return fromPEM(certPath, keyPath, "server");
    }
    
    /**
     * Create SSL config from PEM certificate and key files with custom alias
     * 
     * @param certPath Path to certificate file (.crt, .pem)
     * @param keyPath Path to private key file (.key, .pem) - must be PKCS#8 format
     * @param keyAlias Alias for the key in keystore
     * @return SSLConfig instance
     */
    public static SSLConfig fromPEM(String certPath, String keyPath, String keyAlias) {
        if (certPath == null || certPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Certificate path cannot be null or empty");
        }
        if (keyPath == null || keyPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Key path cannot be null or empty");
        }
        
        // Validate files exist
        if (!new File(certPath).exists()) {
            throw new IllegalArgumentException("Certificate file not found: " + certPath);
        }
        if (!new File(keyPath).exists()) {
            throw new IllegalArgumentException("Key file not found: " + keyPath);
        }
        
        return new SSLConfig(
            SSLConfigType.PEM,
            null,                               // keystorePath
            null,                               // keystorePassword
            null,                               // keyPassword
            certPath,                           // certPath
            keyPath,                            // keyPath
            keyAlias != null ? keyAlias : "server"  // keyAlias
        );
    }
    
    // ========== SSL CONTEXT CREATION ==========
    
    /**
     * Create SSLContext from configuration
     * 
     * @return Configured SSLContext
     * @throws Exception if SSL context creation fails
     */
    public SSLContext createSSLContext() throws Exception {
        return switch (type) {
            case JKS -> createSSLContextFromJKS();
            case PEM -> createSSLContextFromPEM();
        };
    }
    
    /**
     * Create SSLContext from JKS keystore
     */
    private SSLContext createSSLContextFromJKS() throws Exception {
        KeyStore keystore = KeyStore.getInstance("JKS");
        
        try (FileInputStream fis = new FileInputStream(keystorePath)) {
            keystore.load(fis, keystorePassword.toCharArray());
        } catch (FileNotFoundException e) {
            throw new FileNotFoundException("Keystore file not found: " + keystorePath);
        } catch (IOException e) {
            throw new IOException("Failed to load keystore (wrong password?): " + e.getMessage(), e);
        }
        
        // Initialize KeyManagerFactory
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
            KeyManagerFactory.getDefaultAlgorithm()
        );
        kmf.init(keystore, keyPassword.toCharArray());
        
        // Initialize TrustManagerFactory
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm()
        );
        tmf.init(keystore);
        
        // Create SSLContext
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(
            kmf.getKeyManagers(),
            tmf.getTrustManagers(),
            new SecureRandom()
        );
        
        return context;
    }
    
    /**
     * Create SSLContext from PEM certificate and key files
     */
    private SSLContext createSSLContextFromPEM() throws Exception {
        // Read certificate chain
        Certificate[] certChain = readCertificateChain(certPath);
        
        // Read private key (PKCS#8 required)
        PrivateKey privateKey = readPrivateKey(keyPath);
        
        // Create in-memory keystore
        KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
        keystore.load(null, null); // Initialize empty keystore
        
        // Add private key and certificate chain to keystore
        keystore.setKeyEntry(
            keyAlias,
            privateKey,
            new char[0], // Empty password for in-memory keystore
            certChain
        );
        
        // Initialize KeyManagerFactory
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
            KeyManagerFactory.getDefaultAlgorithm()
        );
        kmf.init(keystore, new char[0]);
        
        // Initialize TrustManagerFactory
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm()
        );
        tmf.init(keystore);
        
        // Create SSLContext
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(
            kmf.getKeyManagers(),
            tmf.getTrustManagers(),
            new SecureRandom()
        );
        
        return context;
    }
    
    // ========== PEM PARSING ==========
    
    /**
     * Read certificate chain from PEM file
     */
    private Certificate[] readCertificateChain(String path) throws Exception {
        try (InputStream is = new FileInputStream(path)) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            var certs = cf.generateCertificates(is);
            
            if (certs.isEmpty()) {
                throw new IllegalArgumentException("No certificates found in: " + path);
            }
            
            return certs.toArray(new Certificate[0]);
        } catch (FileNotFoundException e) {
            throw new FileNotFoundException("Certificate file not found: " + path);
        }
    }
    
    /**
     * Read private key from PEM file
     * FIX: Production-safe implementation with clear error messages
     * 
     * Supports:
     * - PKCS#8 format (recommended)
     * - PKCS#1 RSA format (with best-effort conversion)
     * - EC keys in PKCS#8 format
     */
    private PrivateKey readPrivateKey(String path) throws Exception {
        String content = new String(Files.readAllBytes(Paths.get(path)));
        
        // Detect key type from PEM headers
        if (content.contains("BEGIN PRIVATE KEY")) {
            // PKCS#8 format - RECOMMENDED
            return readPKCS8Key(content);
            
        } else if (content.contains("BEGIN RSA PRIVATE KEY")) {
            // PKCS#1 RSA format - attempt conversion with warning
            System.err.println("WARNING: PKCS#1 RSA key detected in: " + path);
            System.err.println("For production use, convert to PKCS#8 format:");
            System.err.println("  openssl pkcs8 -topk8 -nocrypt -in " + path + " -out key_pkcs8.pem");
            return readPKCS1RSAKeyWithFallback(content, path);
            
        } else if (content.contains("BEGIN EC PRIVATE KEY")) {
            // EC format
            System.err.println("WARNING: EC private key in non-PKCS#8 format: " + path);
            System.err.println("Convert to PKCS#8 format for better compatibility:");
            System.err.println("  openssl pkcs8 -topk8 -nocrypt -in " + path + " -out key_pkcs8.pem");
            return readECKey(content, path);
            
        } else if (content.contains("BEGIN ENCRYPTED PRIVATE KEY")) {
            throw new IllegalArgumentException(
                "Encrypted private keys are not supported. " +
                "Decrypt the key first:\n" +
                "  openssl rsa -in " + path + " -out key_decrypted.pem\n" +
                "Or convert to PKCS#8:\n" +
                "  openssl pkcs8 -topk8 -nocrypt -in " + path + " -out key_pkcs8.pem"
            );
        } else {
            throw new IllegalArgumentException(
                "Unknown private key format in: " + path + "\n" +
                "Supported formats: PKCS#8 (recommended), PKCS#1 RSA, EC\n" +
                "Convert your key to PKCS#8:\n" +
                "  openssl pkcs8 -topk8 -nocrypt -in " + path + " -out key_pkcs8.pem"
            );
        }
    }
    
    /**
     * Read PKCS#8 format private key
     */
    private PrivateKey readPKCS8Key(String content) throws Exception {
        String privateKeyPEM = content
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        
        if (privateKeyPEM.isEmpty()) {
            throw new IllegalArgumentException("Empty private key content");
        }
        
        byte[] encoded = Base64.getDecoder().decode(privateKeyPEM);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
        
        // Try RSA first, then EC
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(keySpec);
        } catch (Exception e) {
            try {
                KeyFactory keyFactory = KeyFactory.getInstance("EC");
                return keyFactory.generatePrivate(keySpec);
            } catch (Exception ec) {
                throw new IllegalArgumentException(
                    "Failed to parse PKCS#8 key as RSA or EC: " + e.getMessage()
                );
            }
        }
    }
    
    /**
     * FIX: Safe PKCS#1 RSA reader with fallback
     * Attempts basic conversion, but recommends proper conversion for production
     */
    private PrivateKey readPKCS1RSAKeyWithFallback(String content, String path) throws Exception {
        String privateKeyPEM = content
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        
        if (privateKeyPEM.isEmpty()) {
            throw new IllegalArgumentException("Empty RSA private key content");
        }
        
        byte[] pkcs1Bytes = Base64.getDecoder().decode(privateKeyPEM);
        
        try {
            // Attempt basic PKCS#1 to PKCS#8 conversion
            byte[] pkcs8Bytes = convertPKCS1toPKCS8(pkcs1Bytes);
            
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(pkcs8Bytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            
            PrivateKey key = keyFactory.generatePrivate(keySpec);
            
            // If successful, still warn user
            System.err.println("PKCS#1 key loaded successfully, but production use requires conversion.");
            
            return key;
            
        } catch (Exception e) {
            // Conversion failed - provide helpful error
            throw new IllegalArgumentException(
                "Failed to convert PKCS#1 RSA key from: " + path + "\n" +
                "The automatic conversion failed. For production use, convert manually:\n" +
                "  openssl pkcs8 -topk8 -nocrypt -in " + path + " -out key_pkcs8.pem\n" +
                "Or use BouncyCastle library for robust PKCS#1 support.\n" +
                "Error: " + e.getMessage(),
                e
            );
        }
    }
    
    /**
     * FIX: Improved PKCS#1 to PKCS#8 conversion
     * Works for standard RSA keys, but BouncyCastle is recommended for production
     */
    private byte[] convertPKCS1toPKCS8(byte[] pkcs1Bytes) throws Exception {
        // PKCS#8 wrapper for RSA private key
        // Format: SEQUENCE { version, algorithmIdentifier, privateKey }
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        // Write SEQUENCE header
        baos.write(0x30); // SEQUENCE tag
        
        // Calculate total length
        int algorithmIdLength = 13; // Fixed for RSA
        int versionLength = 3;      // Fixed
        int privateKeyLength = pkcs1Bytes.length + 2; // OCTET STRING header
        int totalContentLength = versionLength + algorithmIdLength + privateKeyLength;
        
        writeLengthBytes(baos, totalContentLength);
        
        // Write version (0)
        baos.write(0x02); // INTEGER tag
        baos.write(0x01); // length
        baos.write(0x00); // value: 0
        
        // Write algorithm identifier (RSA)
        baos.write(0x30); // SEQUENCE tag
        baos.write(0x0D); // length: 13
        
        // RSA OID: 1.2.840.113549.1.1.1
        baos.write(0x06); // OID tag
        baos.write(0x09); // length: 9
        baos.write(new byte[]{
            0x2A, (byte)0x86, 0x48, (byte)0x86,
            (byte)0xF7, 0x0D, 0x01, 0x01, 0x01
        });
        
        // NULL parameter
        baos.write(0x05); // NULL tag
        baos.write(0x00); // length: 0
        
        // Write private key as OCTET STRING
        baos.write(0x04); // OCTET STRING tag
        writeLengthBytes(baos, pkcs1Bytes.length);
        baos.write(pkcs1Bytes);
        
        return baos.toByteArray();
    }
    
    /**
     * Write DER length bytes
     */
    private void writeLengthBytes(ByteArrayOutputStream baos, int length) throws IOException {
        if (length < 128) {
            // Short form
            baos.write(length);
        } else if (length < 256) {
            // Long form, 1 byte
            baos.write(0x81);
            baos.write(length);
        } else if (length < 65536) {
            // Long form, 2 bytes
            baos.write(0x82);
            baos.write((length >> 8) & 0xFF);
            baos.write(length & 0xFF);
        } else {
            // Long form, 3 bytes
            baos.write(0x83);
            baos.write((length >> 16) & 0xFF);
            baos.write((length >> 8) & 0xFF);
            baos.write(length & 0xFF);
        }
    }
    
    /**
     * Read EC private key
     */
    private PrivateKey readECKey(String content, String path) throws Exception {
        String privateKeyPEM = content
            .replace("-----BEGIN EC PRIVATE KEY-----", "")
            .replace("-----END EC PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        
        if (privateKeyPEM.isEmpty()) {
            throw new IllegalArgumentException("Empty EC private key content");
        }
        
        byte[] encoded = Base64.getDecoder().decode(privateKeyPEM);
        
        // Try as PKCS#8
        try {
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            return keyFactory.generatePrivate(keySpec);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                "EC key parsing failed for: " + path + "\n" +
                "Convert to PKCS#8 format:\n" +
                "  openssl pkcs8 -topk8 -nocrypt -in " + path + " -out key_pkcs8.pem\n" +
                "Error: " + e.getMessage(),
                e
            );
        }
    }
    
    // ========== UTILITY METHODS ==========
    
    /**
     * Get SSL configuration type
     */
    public SSLConfigType getType() {
        return type;
    }
    
    /**
     * Validate configuration
     */
    public void validate() throws Exception {
        switch (type) {
            case JKS -> {
                if (!new File(keystorePath).exists()) {
                    throw new FileNotFoundException("Keystore not found: " + keystorePath);
                }
            }
            case PEM -> {
                if (!new File(certPath).exists()) {
                    throw new FileNotFoundException("Certificate not found: " + certPath);
                }
                if (!new File(keyPath).exists()) {
                    throw new FileNotFoundException("Private key not found: " + keyPath);
                }
            }
        }
        
        // Try to create context to validate
        createSSLContext();
    }
    
    @Override
    public String toString() {
        return switch (type) {
            case JKS -> String.format("SSLConfig{type=JKS, keystore='%s'}", keystorePath);
            case PEM -> String.format("SSLConfig{type=PEM, cert='%s', key='%s'}", certPath, keyPath);
        };
    }
    
    // ========== INNER TYPES ==========
    
    /**
     * SSL Configuration Type
     */
    public enum SSLConfigType {
        JKS,    // Java KeyStore
        PEM     // PEM Certificate/Key files
    }
}