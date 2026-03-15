package com.example.motionwatch;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.motionwatch.firebase.FirebaseAuthManager;
import com.google.android.material.button.MaterialButton;

public class SignInActivity extends AppCompatActivity {

    private EditText inputEmail;
    private EditText inputPassword;
    private ProgressBar progressBar;
    private MaterialButton buttonSignIn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in);

        inputEmail = findViewById(R.id.inputEmail);
        inputPassword = findViewById(R.id.inputPassword);
        progressBar = findViewById(R.id.progressBar);
        buttonSignIn = findViewById(R.id.buttonSignIn);

        TextView textCreateNewAccount = findViewById(R.id.textCreateNewAccount);

        // Go to Sign Up screen
        textCreateNewAccount.setOnClickListener(v ->
                startActivity(new Intent(SignInActivity.this, SignUpActivity.class))
        );

        // Sign In button logic
        buttonSignIn.setOnClickListener(v -> signInUser());
    }

    private void signInUser() {

        String email = inputEmail.getText().toString().trim();
        String password = inputPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        FirebaseAuthManager.signIn(
                email,
                password,
                () -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show();

                    startActivity(new Intent(SignInActivity.this, MainActivity.class));
                    finish();
                },
                error -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
                }
        );
    }
}