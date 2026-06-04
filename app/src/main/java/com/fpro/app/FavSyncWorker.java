package com.fpro.app;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONArray;
import java.io.*;
import java.net.*;

public class FavSyncWorker extends Worker {

    public FavSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            SharedPreferences prefs = getApplicationContext()
                .getSharedPreferences("fpro_prefs", Context.MODE_PRIVATE);
            String token = prefs.getString("api_token", null);
            String apiBase = prefs.getString("api_base", null);
            String favJson = prefs.getString("fav_lists_json", "[]");

            if (token == null || apiBase == null) return Result.success();

            // PUSH
            URL url = new URL(apiBase + "/api/favlists?token=" + token);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.getOutputStream().write(("{\"favLists\":" + favJson + "}").getBytes());
            conn.getResponseCode();
            conn.disconnect();

        } catch (Exception ignored) {}
        return Result.success();
    }
}
