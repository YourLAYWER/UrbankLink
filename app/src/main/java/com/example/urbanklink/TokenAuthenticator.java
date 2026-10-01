package com.example.urbanklink;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;

import okhttp3.Authenticator;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

public class TokenAuthenticator implements Authenticator {
    private final Context context;

    public TokenAuthenticator(Context context) {
        this.context = context;
    }

    @Nullable
    @Override
    public Request authenticate(@Nullable Route route, @NonNull Response response) throws IOException {
        SharedPreferences prefs = context.getSharedPreferences("UrbanLinkPrefs", Context.MODE_PRIVATE);
        String currentToken = prefs.getString("JWT_TOKEN", "");
        String currentRefreshToken = prefs.getString("REFRESH_TOKEN", "");

        // If we don't have a refresh token at all, don't bother trying
        if (currentRefreshToken.isEmpty()) {
            forceLogout();
            return null;
        }

        // Create a NEW Retrofit instance for the refresh call to avoid infinite loops
        UrbanLinkApiService refreshService = ApiClient.getRefreshClient().create(UrbanLinkApiService.class);

        AuthModels.RefreshRequest refreshPayload = new AuthModels.RefreshRequest(currentToken, currentRefreshToken);

        // Execute SYNCHRONOUSLY (.execute() instead of .enqueue())
        // so we block the original request until we get the new tokens
        retrofit2.Response<AuthModels.AuthResponse> refreshResponse = refreshService.refreshTokens(refreshPayload).execute();

        if (refreshResponse.isSuccessful() && refreshResponse.body() != null) {
            String newJwt = refreshResponse.body().token;
            String newRefresh = refreshResponse.body().refreshToken;

            // Save the brandnew tokens
            prefs.edit()
                    .putString("JWT_TOKEN", newJwt)
                    .putString("REFRESH_TOKEN", newRefresh)
                    .apply();

            // Retry the original request that failed, but attach the NEW JWT
            return response.request().newBuilder()
                    .header("Authorization", "Bearer " + newJwt)
                    .build();
        } else {
            // The refresh token died or was rejected.
            forceLogout();
            return null;
        }
    }

    private void forceLogout() {
        SharedPreferences prefs = context.getSharedPreferences("UrbanLinkPrefs", Context.MODE_PRIVATE);
        prefs.edit().clear().apply();

        Intent intent = new Intent(context, LoginActivity.class); // Adjust to your login screen
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
    }
}
