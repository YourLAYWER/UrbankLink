package com.example.urbanklink;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
//public class ApiClient {
//    // Emulator alias for localhost. Change to your laptop's IP if using a real phone.
//    private static final String BASE_URL = "http://10.0.2.2:5000/";
//    private static Retrofit retrofit = null;
//
//    public static Retrofit getClient(String currentJwtToken) {
//
//        // This logs the network traffic in Logcat so you can debug easily
//        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
//        logging.setLevel(HttpLoggingInterceptor.Level.BODY);
//
//        OkHttpClient client = new OkHttpClient.Builder()
//                .addInterceptor(new AuthInterceptor(currentJwtToken))
//                .addInterceptor(logging)
//                .build();
//
//        if (retrofit == null) {
//            retrofit = new Retrofit.Builder()
//                    .baseUrl(BASE_URL)
//                    .addConverterFactory(GsonConverterFactory.create())
//                    .client(client)
//                    .build();
//        }
//        return retrofit;
//    }
//}

public class ApiClient {
    // Make sure this ends with a trailing slash!
    private static final String BASE_URL = "http://10.0.2.2:5000/";
    private static Retrofit retrofit = null;

    public static Retrofit getClient(String token) {

        // 1. Create the Logger
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        // Set to BODY to see the URL, Headers, and JSON!
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        // 2. Add the Logger to OkHttpClient
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .build();

        // 3. Build Retrofit
        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client) // Attach the client here
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }
}
