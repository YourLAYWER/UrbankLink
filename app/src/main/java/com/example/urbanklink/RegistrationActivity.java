package com.example.urbanklink;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.regex.Pattern;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegistrationActivity extends AppCompatActivity {
    private TextInputEditText etFullName, etEmail, etPassword, etConfirmPassword;
    private CheckBox cbTerms;
    private MaterialButton btnRegister, btnGoogle;
    private ImageView btnBack;
    private TextView txtSignIn;

    // Regex for: 1 Capital, 1 Special Character, Min 8 Characters
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^" +
                    "(?=.*[A-Z])" +         // at least 1 upper case letter
                    "(?=.*[!@#$&*])" +      // at least 1 special character
                    ".{8,}" +               // at least 8 characters
                    "$");
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_registration);

        txtSignIn = findViewById(R.id.txtSignIn);

        txtSignIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Navigate to Login Activity
                Intent intent = new Intent(RegistrationActivity.this, LoginActivity.class);
                startActivity(intent);
                finish();
            }
        });

        // 2. Initialize the views
        etFullName = findViewById(R.id.etFullName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        cbTerms = findViewById(R.id.cbTerms);
        btnRegister = findViewById(R.id.btnRegister);
        btnGoogle = findViewById(R.id.btnGoogle);
        btnBack = findViewById(R.id.btnBack);

        // Note: I am binding to the clickable text, not the whole layout
        txtSignIn = findViewById(R.id.txtSignIn);

        // 3. Set Click Listeners
        btnRegister.setOnClickListener(v -> handleRegistration());

        btnBack.setOnClickListener(v -> finish()); // Closes activity and goes back

    }

    private void handleRegistration() {
        // Extract text from the inputs
        String fullName = etFullName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        // 4. Input Validation
        if (fullName.isEmpty()) {
            etFullName.setError("Full name is required");
            etFullName.requestFocus();
            return;
        }

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Please enter a valid email address");
            etEmail.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            etPassword.setError("Password is required");
            etPassword.requestFocus();
            return;
        }

        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            etPassword.setError("Password does not meet the security requirements");
            etPassword.requestFocus();
            return;
        }

        if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError("Passwords do not match");
            etConfirmPassword.requestFocus();
            return;
        }

        if (!cbTerms.isChecked()) {
            Toast.makeText(this, "You must agree to the Terms of Service", Toast.LENGTH_SHORT).show();
            return;
        }

        // 5. If all validation passes, send to your C# API
        registerUserWithBackend(fullName, email, password);
    }



    private void registerUserWithBackend(String fullName, String email, String password) {
        // Disable the button so the user doesn't spam the API
        btnRegister.setText("Registering...");
        btnRegister.setEnabled(false);

        String commuterProfile = "Student";
        // 1. Initialize Retrofit (No token needed for registration)
        UrbanLinkApiService apiService = ApiClient.getClient(null).create(UrbanLinkApiService.class);

        // 2. Package the request (Assuming you created a RegisterRequest DTO)
        RegistrationRequest request = new RegistrationRequest(fullName, email, password, commuterProfile);

        // 3. Execute the network call on a background thread
        apiService.registerUser(request).enqueue(new Callback<AuthResponse>() {

            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                // Re-enable the button
                btnRegister.setEnabled(true);
                btnRegister.setText("Register Account");

                if (response.isSuccessful() && response.body() != null) {
                    // 4. Registration successful!
                    // If your API automatically logs the user in and returns a token upon registration:
                    String token = response.body().getToken();
                    saveTokenToPrefs(token); // (Assuming you have this helper method here too)

                    Toast.makeText(RegistrationActivity.this, "Account created successfully!", Toast.LENGTH_SHORT).show();

                    // 5. Navigate straight to the dashboard
                    Intent intent = new Intent(RegistrationActivity.this, MainActivity.class);
                    startActivity(intent);

                    // Close the RegistrationActivity
                    finish();

                } else {
                    // Catches C# 400 Bad Request responses (e.g., "Email already in use")
                    Toast.makeText(RegistrationActivity.this, "Registration failed: Email might already be in use.", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                // Catches network drops, server downtime, or localhost routing issues
                btnRegister.setEnabled(true);
                btnRegister.setText("Register Account");
                Toast.makeText(RegistrationActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }
    // --- Helper Method to Save the Token securely ---
    private void saveTokenToPrefs(String token) {
        SharedPreferences sharedPreferences = getSharedPreferences("UrbanLinkPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("JWT_TOKEN", token);
        editor.apply(); // .apply() saves asynchronously in the background
    }

}