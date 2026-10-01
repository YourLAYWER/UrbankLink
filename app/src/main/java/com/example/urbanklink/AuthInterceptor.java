package com.example.urbanklink;
import android.content.Context;
import android.content.SharedPreferences;

import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
public class AuthInterceptor implements Interceptor {
    private Context context;

    // Pass Context instead of the string
    public AuthInterceptor(Context context) {
        this.context = context;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request originalRequest = chain.request();

        // Fetch the token DYNAMICALLY on every request
        SharedPreferences prefs = context.getSharedPreferences("UrbanLinkPrefs", Context.MODE_PRIVATE);
        String token = prefs.getString("JWT_TOKEN", null);

        // If we don't have a token yet (like during login), just proceed
        if (token == null || token.isEmpty()) {
            return chain.proceed(originalRequest);
        }

        // Otherwise, attach the JWT to the header (Ensure there is a space after Bearer)
        Request newRequest = originalRequest.newBuilder()
                .header("Authorization", "Bearer " + token)
                .build();

        return chain.proceed(newRequest);
    }
}
