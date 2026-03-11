package com.example.motionwatch.firebase;
import android.util.Log;
import androidx.annotation.Nullable;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class FirebaseUserFetcher {
    public static class UserData {
        public final String uid;
        public final String email;
        public final String username;
        public final String name;

        public UserData(String uid, String email, String username, String name) {
            this.uid = uid;
            this.email = email;
            this.username = username;
            this.name = name;
        }
    }
    public interface UserCallback {
        void onUserFetched(@Nullable UserData user);
    }
    public static void fetchCurrentUser(UserCallback callback) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Log.d("USERDATA", "No user is currently signed in.");
            callback.onUserFetched(null);
            return;
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        String authEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists() && document.getData() != null) {
                        String email = document.getString("email");
                        String username = document.getString("username");
                        String name = document.getString("name");

                        //Fields
                        if (email == null) email = authEmail;
                        if (username == null && email != null && email.contains("@")) username = email.substring(0, email.indexOf("@"));
                        if (name == null) name = "--";

                        callback.onUserFetched(new UserData(uid, email != null ? email : "--", username != null ? username : "--", name));
                    } else {
                        Log.d("USERDATA", "No Firestore doc for user: " + uid);
                        String email = authEmail != null ? authEmail : "--";
                        String username = (authEmail != null && authEmail.contains("@")) ? authEmail.substring(0, authEmail.indexOf("@")) : "--";
                        callback.onUserFetched(new UserData(uid, email, username, "--"));
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("USERDATA", "Failed to fetch user", e);
                    callback.onUserFetched(null);
                });
    }
}

