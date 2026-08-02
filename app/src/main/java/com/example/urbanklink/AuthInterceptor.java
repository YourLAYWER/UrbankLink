package com.example.urbanklink;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
public class AuthInterceptor implements Interceptor {
    private String token;

    // You will pass the toke here from SharedPreferences
    public AuthInterceptor(String token){
        this.token = token;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request originalRequest = chain.request();

        // If we don't have a token yet (like during login), just proceed
        if (token == null || token.isEmpty()){
            return chain.proceed(originalRequest);
        }

        // Otherwise, attach the JWT to the header
        Request newRequest = originalRequest.newBuilder().header("Authorization",
                "Bearer" + token).build();

        return chain.proceed(newRequest);
    }
}
