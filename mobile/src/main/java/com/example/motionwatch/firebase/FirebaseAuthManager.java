package com.example.motionwatch.firebase;

import com.google.firebase.auth.FirebaseAuth;

import java.util.function.Consumer;

public class FirebaseAuthManager {

    private static final FirebaseAuth auth = FirebaseAuth.getInstance();

    public static void signIn(
            String email,
            String password,
            Runnable onSuccess,
            Consumer<Exception> onError
    ) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> onSuccess.run())
                .addOnFailureListener(onError::accept);
    }

    public static void signUp(
            String email,
            String password,
            Runnable onSuccess,
            Consumer<Exception> onError
    ) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> onSuccess.run())
                .addOnFailureListener(onError::accept);
    }

    public static void signOut() {
        auth.signOut();
    }
}
