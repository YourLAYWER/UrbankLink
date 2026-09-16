package com.example.urbanklink;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private EditText editEmail;
    private EditText editPassword;
    private Button btnLogin;
    private TextView createAccount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        createAccount = findViewById(R.id.txtCreateAccount);

        // 2. Set an OnClickListener to make it act like a button
        createAccount.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 3. Create the Intent to navigate to the next screen
                // Replace 'NextActivity.class' with the Activity you want to open
                Intent intent = new Intent(LoginActivity.this, RegistrationActivity.class);

                // 4. Execute the intent
                startActivity(intent);
            }
        });


        // Map your XML UI elements
        editEmail = findViewById(R.id.editEmail);
        editPassword = findViewById(R.id.editPassword);
        btnLogin = findViewById(R.id.btnLogin);

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performLogin();
            }
        });
    }

    private void performLogin() {
        String email = editEmail.getText().toString().trim();
        String password = editPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        // Disable the button so the user doesn't spam the API
        btnLogin.setEnabled(false);
        btnLogin.setText("Logging in...");

        // 1. Initialize Retrofit (Pass null for the token since we are trying to get one)
        UrbanLinkApiService apiService = ApiClient.getClient(this).create(UrbanLinkApiService.class);

        // 2. Package the request
        LoginRequest request = new LoginRequest(email, password);

        // 3. .enqueue() executes the network call on a background thread!
        apiService.loginUser(request).enqueue(new Callback<AuthResponse>() {

            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                btnLogin.setEnabled(true);
                btnLogin.setText("Login");

                if (response.isSuccessful() && response.body() != null) {
                    // 4. We got a 200 OK! Extract the JWT
                    String token = response.body().getToken();
                    String role = response.body().getRole(); // Grab the role from the API

                    // 5. Save the token to local storage
                    // Save BOTH to local storage
                    saveAuthDataToPrefs(token, role);

                    Toast.makeText(LoginActivity.this, "Welcome back!", Toast.LENGTH_SHORT).show();

                    Intent intent;
                    // Route the user based on their role
                    if (role != null && role.equalsIgnoreCase("Conductor")) {
                        intent = new Intent(LoginActivity.this, ShiftStartActivity.class);
                    } else if (role != null && role.equalsIgnoreCase("Driver")) {
                        intent = new Intent(LoginActivity.this, ShiftStartActivity.class);
                    } else {
                        // Default to Commuter Dashboard
                        intent = new Intent(LoginActivity.this, MainActivity.class);
                    }

                    startActivity(intent);
                    finish();
                } else {
                    // This catches your C# 401 Unauthorized responses
                    Toast.makeText(LoginActivity.this, "Invalid credentials", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                // This catches network drops, server downtime, or localhost routing issues
                btnLogin.setEnabled(true);
                btnLogin.setText("Login");
                Toast.makeText(LoginActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    // --- Helper Method to Save the Token securely ---
    private void saveAuthDataToPrefs(String token, String role) {
        SharedPreferences sharedPreferences = getSharedPreferences("UrbanLinkPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("JWT_TOKEN", token);
        editor.putString("USER_ROLE", role); // Save the role for later
        editor.apply();
    }
}