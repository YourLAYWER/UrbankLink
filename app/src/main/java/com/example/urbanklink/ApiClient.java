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
    // Make sure this ends with a trailing slash!
    //private static final String BASE_URL = "http://10.0.2.2:5000/";
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

    public static Retrofit getClient(Context context) { // CHANGED: Pass Context instead of String

        if (retrofit == null) {
            // 1. Create the Logger
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            // 2. Create the Auth Interceptor
            Interceptor authInterceptor = new Interceptor() {
                @Override
                public Response intercept(Chain chain) throws IOException {
                    Request original = chain.request();
                    Request.Builder requestBuilder = original.newBuilder();

                    // Dynamically fetch the token on every single request
                    if (context != null) {
                        SharedPreferences prefs = context.getSharedPreferences("UrbanLinkPrefs", Context.MODE_PRIVATE);
                        // Verify this key matches exactly what you use during login!
                        String token = prefs.getString("JWT_TOKEN", null);

                        if (token != null && !token.isEmpty()) {
                            requestBuilder.header("Authorization", "Bearer " + token);
                        }
                    }

                    return chain.proceed(requestBuilder.build());
                }
            };

            // 3. Add BOTH Interceptors to OkHttpClient
            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(authInterceptor) // Must be added before logging to see the header in logs
                    .addInterceptor(logging)
                    .build();

            // 4. Build Retrofit
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }
}
