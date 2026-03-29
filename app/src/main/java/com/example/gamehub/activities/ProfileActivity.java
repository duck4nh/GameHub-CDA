package com.example.gamehub.activities;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.gamehub.R;
import com.example.gamehub.data.pref.PreferenceManager;

public class ProfileActivity extends AppCompatActivity {

    private EditText etEditNickname;
    private Button btnSaveProfile;
    private ImageView btnBack;
    private PreferenceManager preferenceManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        preferenceManager = new PreferenceManager(this);

        initViews();
        setupListeners();
        loadCurrentData();
    }

    private void initViews() {
        etEditNickname = findViewById(R.id.etEditNickname);
        btnSaveProfile = findViewById(R.id.btnSaveProfile);
        btnBack = findViewById(R.id.btnBack);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnSaveProfile.setOnClickListener(v -> {
            String newNickname = etEditNickname.getText().toString().trim();
            if (newNickname.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập biệt danh", Toast.LENGTH_SHORT).show();
                return;
            }

            // Lưu vào SharedPreferences (Trong thực tế cần lưu cả lên Firestore)
            preferenceManager.putString(PreferenceManager.KEY_CACHE_NICKNAME, newNickname);
            
            Toast.makeText(this, "Đã cập nhật hồ sơ thành công!", Toast.LENGTH_SHORT).show();
            finish(); // Quay lại màn hình trước
        });
    }

    private void loadCurrentData() {
        etEditNickname.setText(preferenceManager.getCacheNickname());
    }
}
