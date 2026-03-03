package com.example.motionwatch.firebase;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class UserProfileManager {

    public interface ProfileCallback {
        void onProfileFetched(@Nullable Map<String, Object> profile);
    }

    public static void saveProfile(
            String name,
            String age,
            String height,
            String weight,
            String sex,
            String bloodType,
            boolean shareData,
            String measurementUnit,
            ProfileCallback callback
    ) {

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            callback.onProfileFetched(null);
            return;
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);
        updates.put("age", age);
        updates.put("height", height);
        updates.put("weight", weight);
        updates.put("sex", sex);
        updates.put("bloodType", bloodType);
        updates.put("shareData", shareData);
        updates.put("measurementUnit", measurementUnit);

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .set(updates, SetOptions.merge())   // 🔥 MERGE (important)
                .addOnSuccessListener(unused -> callback.onProfileFetched(updates))
                .addOnFailureListener(e -> callback.onProfileFetched(null));
    }

    public static void fetchProfile(ProfileCallback callback) {

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            callback.onProfileFetched(null);
            return;
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        callback.onProfileFetched(document.getData());
                    } else {
                        callback.onProfileFetched(null);
                    }
                })
                .addOnFailureListener(e -> callback.onProfileFetched(null));
    }
}