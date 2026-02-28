package com.example.motionwatch;

import android.os.Bundle;
import android.widget.Toast;


import androidx.appcompat.app.AppCompatActivity;

import com.example.motionwatch.firebase.FirebaseAuthManager;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import android.widget.EditText;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

public class SignUpActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        EditText email = findViewById(R.id.inputEmail);
        EditText password = findViewById(R.id.inputPassword);
        EditText confirm = findViewById(R.id.inputConfirmPassword);
        EditText firstName = findViewById(R.id.inputFirstName);
        EditText lastName = findViewById(R.id.inputLastName);

        MaterialButton signUp = findViewById(R.id.buttonSignUp);
        TextView back = findViewById(R.id.textSignIn);

        signUp.setOnClickListener(v -> {
            String emailText = email.getText().toString().trim();
            String passwordText = password.getText().toString();
            String confirmText = confirm.getText().toString();
            String firstNameText = firstName.getText().toString().trim();
            String lastNameText = lastName.getText().toString().trim();

            if (emailText.isEmpty() || passwordText.isEmpty() || confirmText.isEmpty()
                    || firstNameText.isEmpty() || lastNameText.isEmpty()) {
                Toast.makeText(SignUpActivity.this, "Please fill all required fields", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!passwordText.equals(confirmText)) {
                Toast.makeText(SignUpActivity.this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }


            //signUp(email, password, onSuccess Runnable, onError Consumer<Exception>)
            FirebaseAuthManager.signUp(
                    emailText,
                    passwordText,
                    () -> runOnUiThread(() -> {
                        // Auth user created successfully -> now write Firestore user doc
                        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                            Toast.makeText(SignUpActivity.this, "Account created, but no user session found", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

                        String username = emailText.contains("@")
                                ? emailText.substring(0, emailText.indexOf("@"))
                                : emailText;

                        String name = firstNameText + " " + lastNameText;

                        Map<String, Object> userMap = new HashMap<>();
                        userMap.put("email", emailText);
                        userMap.put("username", username);
                        userMap.put("name", name);
                        userMap.put("first_name", firstNameText);
                        userMap.put("last_name", lastNameText);

                        FirebaseFirestore.getInstance()
                                .collection("users")
                                .document(uid)
                                .set(userMap)
                                .addOnSuccessListener(unused -> {
                                    Toast.makeText(SignUpActivity.this, "Account created", Toast.LENGTH_SHORT).show();
                                    finish();
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(SignUpActivity.this, "Firestore write failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
                                );
                    }),
                    e -> runOnUiThread(() ->
                            Toast.makeText(SignUpActivity.this,
                                    "Sign up failed: " + (e != null ? e.getMessage() : "Unknown error"),
                                    Toast.LENGTH_LONG).show()
                    )
            );
        });

        back.setOnClickListener(v -> finish());
    }
}

