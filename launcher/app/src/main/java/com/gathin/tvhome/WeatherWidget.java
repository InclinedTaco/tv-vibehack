package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small cached current-weather request; never blocks home rendering or remote input. */
final class WeatherWidget {
    private static final long REFRESH_MS = 30 * 60_000L;
    private static final long MAX_AGE_MS = 2 * 60 * 60_000L;
    private static final String ENDPOINT = "https://api.open-meteo.com/v1/forecast"
            + "?latitude=11.3428&longitude=77.72741&current=temperature_2m,weather_code"
            + "&temperature_unit=celsius&timezone=Asia%2FKolkata&forecast_days=1";
    private final SharedPreferences cache;
    private final TextView view;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private boolean active, busy, closed;
    private final Runnable tick = this::refresh;

    WeatherWidget(Context context, TextView view) {
        cache = context.getSharedPreferences("weather_erode", Context.MODE_PRIVATE);
        this.view = view;
    }

    void start() { active = true; refresh(); }
    void stop() { active = false; handler.removeCallbacks(tick); }
    void close() { stop(); closed = true; worker.shutdownNow(); }

    private void render() {
        long age = System.currentTimeMillis() - cache.getLong("fetched", 0);
        if (age >= 0 && age < MAX_AGE_MS && cache.contains("temperature")) {
            WeatherIconDrawable icon = new WeatherIconDrawable(cache.getInt("code", -1));
            int size = Math.round(52 * view.getResources().getDisplayMetrics().density);
            icon.setBounds(0, 0, size, size);
            view.setCompoundDrawables(icon, null, null, null);
            view.setCompoundDrawablePadding(Math.round(8 * view.getResources().getDisplayMetrics().density));
            view.setText(String.format(Locale.getDefault(), "%d°C", cache.getInt("temperature", 0)));
            view.setContentDescription("Erode weather, " + view.getText() + ", "
                    + condition(cache.getInt("code", -1)) + ". Data by Open-Meteo.");
        } else {
            view.setCompoundDrawables(null, null, null, null);
            view.setText("—°C");
            view.setContentDescription("Erode weather unavailable");
        }
    }

    private void refresh() {
        if (!active || closed) return;
        handler.removeCallbacks(tick);
        render();
        long age = System.currentTimeMillis() - cache.getLong("fetched", 0);
        if (age >= 0 && age < REFRESH_MS) {
            handler.postDelayed(tick, REFRESH_MS - age);
            return;
        }
        if (busy) return;
        busy = true;
        worker.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(7000);
                connection.setRequestProperty("Accept", "application/json");
                if (connection.getResponseCode() != 200) throw new java.io.IOException("Weather HTTP error");
                StringBuilder body = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        body.append(line);
                        if (body.length() > 100_000) throw new java.io.IOException("Unexpected response size");
                    }
                }
                JSONObject current = new JSONObject(body.toString()).getJSONObject("current");
                double temperature = current.getDouble("temperature_2m");
                if (Double.isNaN(temperature) || Double.isInfinite(temperature)) throw new java.io.IOException("Invalid temperature");
                cache.edit().putInt("temperature", (int) Math.round(temperature))
                        .putInt("code", current.getInt("weather_code"))
                        .putLong("fetched", System.currentTimeMillis()).apply();
            } catch (Exception e) {
                android.util.Log.w("TvHomeWeather", "Weather refresh unavailable: " + e.getClass().getSimpleName());
            } finally {
                if (connection != null) connection.disconnect();
                handler.post(() -> {
                    busy = false;
                    if (active && !closed) {
                        render();
                        long elapsed = System.currentTimeMillis() - cache.getLong("fetched", 0);
                        handler.postDelayed(tick, elapsed >= 0 && elapsed < REFRESH_MS ? REFRESH_MS - elapsed : 5 * 60_000L);
                    }
                });
            }
        });
    }

    private static String condition(int code) {
        if (code == 0) return "Clear";
        if (code == 1) return "Mostly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code == 85 || code == 86) return "Snow showers";
        if (code >= 95 && code <= 99) return "Thunderstorm";
        return "Current weather";
    }
}
