package com.example.motionwatch;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.motionwatch.firebase.FirebaseAuthManager;
import com.google.android.material.button.MaterialButton;

public class SignUpActivity extends AppCompatActivity {

    private EditText inputFirstName;
    private EditText inputLastName;
    private EditText inputEmail;
    private EditText inputPassword;
    private EditText inputConfirmPassword;

    private ProgressBar progressBar;
    private MaterialButton buttonSignUp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        inputFirstName = findViewById(R.id.inputFirstName);
        inputLastName = findViewById(R.id.inputLastName);
        inputEmail = findViewById(R.id.inputEmail);
        inputPassword = findViewById(R.id.inputPassword);
        inputConfirmPassword = findViewById(R.id.inputConfirmPassword);

        progressBar = findViewById(R.id.progressBar);
        buttonSignUp = findViewById(R.id.buttonSignUp);

        TextView textSignIn = findViewById(R.id.textSignIn);

        // Return to Sign In screen
        textSignIn.setOnClickListener(v -> finish());

        // Sign Up button logic
        buttonSignUp.setOnClickListener(v -> signUpUser());
    }

    private void signUpUser() {

        String firstName = inputFirstName.getText().toString().trim();
        String lastName = inputLastName.getText().toString().trim();
        String email = inputEmail.getText().toString().trim();
        String password = inputPassword.getText().toString().trim();
        String confirmPassword = inputConfirmPassword.getText().toString().trim();

        if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        FirebaseAuthManager.signUp(
                email,
                password,
                () -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Account created successfully!", Toast.LENGTH_SHORT).show();
                    finish();
                },
                error -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
                }
        );
    }
}