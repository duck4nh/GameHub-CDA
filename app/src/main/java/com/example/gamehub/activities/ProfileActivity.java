package com.example.gamehub.activities;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.gamehub.R;
import com.example.gamehub.data.pref.PreferenceManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class ProfileActivity extends AppCompatActivity {

    private EditText etEditNickname, etEditEmail;
    private Button btnSaveProfile;
    private ImageView btnBack, ivProfilePic;
    private PreferenceManager preferenceManager;
    private FirebaseAuth mAuth;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        preferenceManager = new PreferenceManager(this);
        mAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        initViews();
        setupListeners();
        loadCurrentData();
        fetchLatestAvatar();
    }

    private void initViews() {
        etEditEmail = findViewById(R.id.etEditEmail);
        etEditNickname = findViewById(R.id.etEditNickname);
        btnSaveProfile = findViewById(R.id.btnSaveProfile);
        btnBack = findViewById(R.id.btnBack);
        ivProfilePic = findViewById(R.id.ivProfilePic);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnSaveProfile.setOnClickListener(v -> {
            String newNickname = etEditNickname.getText().toString().trim();
            if (newNickname.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập biệt danh", Toast.LENGTH_SHORT).show();
                return;
            }

            // Lưu vào SharedPreferences
            preferenceManager.putString(PreferenceManager.KEY_CACHE_NICKNAME, newNickname);
            
            Toast.makeText(this, "Đã cập nhật biệt danh thành công!", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void loadCurrentData() {
        // Nạp biệt danh từ SharedPreferences
        etEditNickname.setText(preferenceManager.getCacheNickname());
        
        // Nạp Email từ Firebase (Nếu có đăng nhập)
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            etEditEmail.setText(user.getEmail());
        }

        // Nạp avatar từ cache
        loadAvatar(preferenceManager.getCacheAvatar());
    }

    private void fetchLatestAvatar() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        firestore.collection("Users").document(user.getUid()).get()
                .addOnSuccessListener(document -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (document.exists()) {
                        String avatarUrl = document.getString("avatar_url");
                        if (avatarUrl != null && !avatarUrl.isEmpty()) {
                            preferenceManager.putString(PreferenceManager.KEY_CACHE_AVATAR, avatarUrl);
                            loadAvatar(avatarUrl);
                        }
                    }
                });
    }

    private void loadAvatar(String url) {
        if (ivProfilePic == null || url == null || url.isEmpty()) return;

        String optimizedUrl = url;
        if (url.contains("/svg")) {
            optimizedUrl = url.replace("/svg", "/png");
        } else if (url.endsWith(".svg")) {
            optimizedUrl = url.replace(".svg", ".png");
        }

        Glide.with(this)
                .load(optimizedUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.img_avatar_cat)
                .error(R.drawable.img_avatar_cat)
                .circleCrop()
                .into(ivProfilePic);
    }
}
