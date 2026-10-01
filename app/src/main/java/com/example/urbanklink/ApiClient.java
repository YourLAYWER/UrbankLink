package com.example.urbanklink;
import android.content.Context;
import android.content.SharedPreferences;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
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

    public static Retrofit getClient(Context context) { // CHANGED: Pass Context instead of String

        if (retrofit == null) {
            // 1. Create the Logger
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            // 2. Build OkHttpClient with Logger, Interceptor, and Authenticator
            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(logging) // Wired your logger up so you can see requests in Logcat!
                    .addInterceptor(new AuthInterceptor(context)) // Relies entirely on your dedicated class
                    .authenticator(new TokenAuthenticator(context)) // Handles the 401 token refresh
                    .build();

            // 3. Build Retrofit
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }

    // 4. The Clean Client (For the TokenAuthenticator to use)
    public static Retrofit getRefreshClient() {
        if (refreshRetrofit == null) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            // Notice there is NO AuthInterceptor or TokenAuthenticator attached here.
            // This prevents an infinite loop if the refresh request itself fails!
            OkHttpClient cleanClient = new OkHttpClient.Builder()
                    .addInterceptor(logging)
                    .build();

            refreshRetrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(cleanClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return refreshRetrofit;
    }
}
