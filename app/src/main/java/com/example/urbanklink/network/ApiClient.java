package com.example.urbanklink.network;
import android.content.Context;

import com.example.urbanklink.auth.TokenAuthenticator;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    // Automatically routes the emulator to 10.0.2.2 and the physical phone to localhost
    private static final String BASE_URL = isRunningOnEmulator()
            ? "http://10.0.2.2:5000/"
            : "http://localhost:5000/";

    private static boolean isRunningOnEmulator() {
        return android.os.Build.FINGERPRINT.startsWith("generic")
                || android.os.Build.FINGERPRINT.startsWith("unknown")
                || android.os.Build.MODEL.contains("google_sdk")
                || android.os.Build.MODEL.toLowerCase().contains("sdk")
                || android.os.Build.MODEL.contains("Emulator")
                || android.os.Build.HARDWARE.contains("ranchu")
                || android.os.Build.HARDWARE.contains("goldfish")
                || android.os.Build.PRODUCT.toLowerCase().contains("sdk");
    }
    private static Retrofit retrofit = null;
    private static Retrofit refreshRetrofit = null;

    public static Retrofit getClient(Context context) {
        // 1. FAIL FAST: Prevent the OkHttp Dispatcher crash
        if (context == null) {
            throw new IllegalArgumentException("Fatal: ApiClient requires a valid Context.");
        }

        // 2. LEAK PREVENTION: Strip the Activity context to the app-level context
        Context appContext = context.getApplicationContext();

        // 3. THREAD SAFETY: Double-checked locking
        if (retrofit == null) {
            synchronized (ApiClient.class) {
                if (retrofit == null) {
                    HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
                    logging.setLevel(HttpLoggingInterceptor.Level.BODY);

                    OkHttpClient client = new OkHttpClient.Builder()
                            .addInterceptor(logging)
                            .addInterceptor(new AuthInterceptor(appContext)) // Safe context used
                            .authenticator(new TokenAuthenticator(appContext)) // Safe context used
                            .connectTimeout(30, TimeUnit.SECONDS)
                            .readTimeout(30, TimeUnit.SECONDS)
                            .build();

                    retrofit = new Retrofit.Builder()
                            .baseUrl(BASE_URL)
                            .client(client)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build();
                }
            }
        }
        return retrofit;
    }

    // 4. The Clean Client (For the TokenAuthenticator to use)
    public static Retrofit getRefreshClient() {
        // THREAD SAFETY for the refresh client as well
        if (refreshRetrofit == null) {
            synchronized (ApiClient.class) {
                if (refreshRetrofit == null) {
                    HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
                    logging.setLevel(HttpLoggingInterceptor.Level.BODY);

                    // Notice there is NO AuthInterceptor or TokenAuthenticator attached here.
                    OkHttpClient cleanClient = new OkHttpClient.Builder()
                            .addInterceptor(logging)
                            .connectTimeout(30, TimeUnit.SECONDS)
                            .readTimeout(30, TimeUnit.SECONDS)
                            .build();

                    refreshRetrofit = new Retrofit.Builder()
                            .baseUrl(BASE_URL)
                            .client(cleanClient)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build();
                }
            }
        }
        return refreshRetrofit;
    }
}
