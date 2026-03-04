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

public class SignInActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in);

        EditText email = findViewById(R.id.inputEmail);
        EditText password = findViewById(R.id.inputPassword);
        Button signIn = findViewById(R.id.buttonSignIn);
        ProgressBar progress = findViewById(R.id.progressBar);
        TextView createAccount = findViewById(R.id.textCreateNewAccount);

        signIn.setOnClickListener(v -> {
            progress.setVisibility(ProgressBar.VISIBLE);

            FirebaseAuthManager.signIn(
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

        createAccount.setOnClickListener(v ->
                startActivity(new Intent(this, SignUpActivity.class)));
    }
}
