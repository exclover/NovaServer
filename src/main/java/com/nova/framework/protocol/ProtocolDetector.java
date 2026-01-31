package com.nova.framework.protocol;

import java.io.*;
import java.net.Socket;
import java.util.Map;

public class ProtocolDetector {
    
    private static final int PEEK_SIZE = 16;
    
    public enum Protocol {
        HTTP, WEBSOCKET, CUSTOM, UNKNOWN
    }
     
    /**
     * Detect protocol with custom protocol handler support
     * * @param socket Client socket
     * @param customHandlers Map of magic bytes (hex string) to handlers
     * @return Detection result with protocol, wrapped socket, and magic key if custom protocol
     */
    public static Result detect(Socket socket, Map<String, CustomProtocolHandler> customHandlers) {
        try {
            InputStream original = socket.getInputStream();
            // Tampon boyutu kadar geri itme kapasitesi olan stream oluştur
            PushbackInputStream pushback = new PushbackInputStream(original, PEEK_SIZE);
            
            byte[] buffer = new byte[PEEK_SIZE];
            int totalBytesRead = 0;
            
            // --- DÜZELTME BAŞLANGICI ---
            // TCP Fragmentation Çözümü:
            // Veri ağdan parça parça gelebilir (örn: önce 2 byte, sonra 14 byte).
            // Buffer dolana kadar (16 byte) veya akış bitene kadar okumaya zorluyoruz.
            while (totalBytesRead < PEEK_SIZE) {
                int bytesRead = pushback.read(buffer, totalBytesRead, PEEK_SIZE - totalBytesRead);
                
                // Akış sonlandıysa döngüyü kır
                if (bytesRead == -1) {
                    break;
                }
                
                totalBytesRead += bytesRead;
                
                // Eğer socket engellemesiz (non-blocking) modda değilse ve veri akışı yavaşsa,
                // elimizdeki veriyle karar vermek için available kontrolü yapılabilir.
                // Ancak protokol tespiti için genelde tam PEEK_SIZE beklemek daha güvenlidir.
                // Bu örnekte veri gelmesini beklemeye devam ediyoruz.
            }
            // --- DÜZELTME BİTİŞİ ---
            
            // Hiç veri okunmadıysa veya socket hemen kapandıysa
            if (totalBytesRead <= 0) {
                return new Result(Protocol.UNKNOWN, socket, null);
            }
            
            // Okunan gerçek veri miktarı (totalBytesRead) ile analiz yap
            DetectionInfo detected = detectProtocol(buffer, totalBytesRead, customHandlers);
            
            // Okunan veriyi, okunduğu miktar kadar geri it (Pushback)
            // Böylece sonraki okumalar eksiksiz olur.
            if (totalBytesRead > 0) {
                pushback.unread(buffer, 0, totalBytesRead);
            }
            
            // Orijinal socket yerine, veriyi geri yüklediğimiz wrapper'ı döndür
            Socket wrappedSocket = new SocketWrapper(socket, pushback);
            return new Result(detected.protocol, wrappedSocket, detected.magicKey);
            
        } catch (Exception e) {
            // Hata durumunda güvenli çıkış
            return new Result(Protocol.UNKNOWN, socket, null);
        }
    }
    
    private static DetectionInfo detectProtocol(byte[] data, int length, Map<String, CustomProtocolHandler> customHandlers) {
        // En azından minimal bir HTTP metodu (örn: "GET") veya en kısa magic byte için veri lazım
        if (length < 2) {
            return new DetectionInfo(Protocol.UNKNOWN, null);
        }
        
        // Sadece okunan uzunluk (length) kadar veriyi string'e çevir
        String start = new String(data, 0, Math.min(length, 10), java.nio.charset.StandardCharsets.US_ASCII);
        
        // HTTP Metot Kontrolleri
        if (length >= 3) { // En kısa metodlar için min uzunluk
            if (start.startsWith("GET ") || start.startsWith("PUT ") ||
                start.startsWith("HEAD ")) {
                return new DetectionInfo(Protocol.HTTP, null);
            }
        }
        
        if (length >= 4) {
             if (start.startsWith("POST ") || start.startsWith("PATCH ")) {
                return new DetectionInfo(Protocol.HTTP, null);
             }
        }
        
        if (length >= 5) {
             if (start.startsWith("DELETE ") || start.startsWith("TRACE ")) {
                return new DetectionInfo(Protocol.HTTP, null);
             }
        }
        
        if (length >= 7) {
             if (start.startsWith("OPTIONS ") || start.startsWith("CONNECT ")) {
                return new DetectionInfo(Protocol.HTTP, null);
             }
        }
        
        // Custom Protokol (Magic Bytes) Kontrolleri
        if (customHandlers != null && !customHandlers.isEmpty()) {
            for (Map.Entry<String, CustomProtocolHandler> entry : customHandlers.entrySet()) {
                String magicHex = entry.getKey();
                int magicLenBytes = magicHex.length() / 2;
                
                // ÖNEMLİ: Elimizdeki veri, aranan magic key'den kısaysa kontrol etme (Hata önleyici)
                if (length >= magicLenBytes) {
                    if (matchesMagicBytes(data, length, magicHex)) {
                        return new DetectionInfo(Protocol.CUSTOM, magicHex);
                    }
                }
            }
        }
        
        return new DetectionInfo(Protocol.UNKNOWN, null);
    }
    


    /**
     * Check if data starts with magic bytes
     * 
     * @param data Received data
     * @param length Length of received data
     * @param magicHex Hex string of magic bytes (e.g., "4E4F5641" for "NOVA")
     * @return true if matches
     */
    private static boolean matchesMagicBytes(byte[] data, int length, String magicHex) {
        if (magicHex == null || magicHex.length() % 2 != 0) {
            return false;
        }
        
        int magicLength = magicHex.length() / 2;
        if (length < magicLength) {
            return false;
        }
        
        try {
            for (int i = 0; i < magicLength; i++) {
                String hexByte = magicHex.substring(i * 2, i * 2 + 2);
                int expectedByte = Integer.parseInt(hexByte, 16);
                if ((data[i] & 0xFF) != expectedByte) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Detection info with protocol and magic key
     */
    private static class DetectionInfo {
        final Protocol protocol;
        final String magicKey;
        
        DetectionInfo(Protocol protocol, String magicKey) {
            this.protocol = protocol;
            this.magicKey = magicKey;
        }
    }
    
    /**
     * Detection result
     */
    public static class Result {
        private final Protocol protocol;
        private final Socket socket;
        private final String magicKey;
        
        Result(Protocol protocol, Socket socket, String magicKey) {
            this.protocol = protocol;
            this.socket = socket;
            this.magicKey = magicKey;
        }
        
        public Protocol protocol() { return protocol; }
        public Socket socket() { return socket; }
        public String magicKey() { return magicKey; }
    }
    
    private static class SocketWrapper extends Socket {
        private final Socket client;
        private final InputStream inputStream;
        
        SocketWrapper(Socket client, InputStream inputStream) {
            this.client = client;
            this.inputStream = inputStream;
        }
        
        @Override
        public InputStream getInputStream() {
            return inputStream;
        }
        
        @Override
        public OutputStream getOutputStream() throws IOException {
            return client.getOutputStream();
        }
        
        @Override
        public void close() throws IOException {
            client.close();
        }
        
        @Override
        public java.net.InetAddress getInetAddress() {
            return client.getInetAddress();
        }
        
        @Override
        public boolean isClosed() {
            return client.isClosed();
        }
        
        @Override
        public void setSoTimeout(int timeout) throws java.net.SocketException {
            client.setSoTimeout(timeout);
        }
        
        @Override
        public void setTcpNoDelay(boolean on) throws java.net.SocketException {
            client.setTcpNoDelay(on);
        }
        
        @Override
        public void setKeepAlive(boolean on) throws java.net.SocketException {
            client.setKeepAlive(on);
        }
    }
}
