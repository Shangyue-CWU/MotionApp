package com.example.motionwatch;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.motionwatch.firebase.FirebaseAuthManager;

public class SignUpActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        EditText email = findViewById(R.id.inputEmail);
        EditText password = findViewById(R.id.inputPassword);
        EditText confirm = findViewById(R.id.inputConfirmPassword);
        Button signUp = findViewById(R.id.buttonSignUp);
        ProgressBar progress = findViewById(R.id.progressBar);
        TextView signIn = findViewById(R.id.textSignIn);

        signUp.setOnClickListener(v -> {
            if (!password.getText().toString()
                    .equals(confirm.getText().toString())) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }

            progress.setVisibility(ProgressBar.VISIBLE);

            FirebaseAuthManager.signUp(
                    email.getText().toString(),
                    password.getText().toString(),
                    () -> {
                        progress.setVisibility(ProgressBar.GONE);
                        finish();
                    },
                    error -> {
                        progress.setVisibility(ProgressBar.GONE);
                        Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();

                    }
            );
        });

        signIn.setOnClickListener(v ->
                startActivity(new Intent(this, SignInActivity.class)));
    }
}
