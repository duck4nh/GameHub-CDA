package com.example.gamehub.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.gamehub.MainActivity;
import com.example.gamehub.R;
import com.example.gamehub.data.local.AppDatabase;
import com.example.gamehub.data.pref.PreferenceManager;
import com.example.gamehub.data.repository.AuthRepository;

public class RegisterActivity extends AppCompatActivity {

    private EditText etEmail, etNickname, etPassword, etConfirmPassword;
    private Button btnRegister;
    private TextView tvLogin;
    private AuthRepository authRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Khởi tạo Repository
        AppDatabase db = AppDatabase.getInstance(this);
        PreferenceManager pref = new PreferenceManager(this);
        authRepository = new AuthRepository(db, pref);

        initViews();
        setupListeners();
    }

    private void initViews() {
        etEmail = findViewById(R.id.etEmail);
        etNickname = findViewById(R.id.etNickname);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnRegister = findViewById(R.id.btnRegister);
        tvLogin = findViewById(R.id.tvLogin);
    }

    private void setupListeners() {
        btnRegister.setOnClickListener(v -> handleRegister());

        tvLogin.setOnClickListener(v -> {
            finish(); // Quay lại màn hình Login
        });
    }

    private void handleRegister() {
        String email = etEmail.getText().toString().trim();
        String nickname = etNickname.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        if (email.isEmpty() || nickname.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, "Mật khẩu xác nhận không khớp", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(this, "Mật khẩu phải có ít nhất 6 ký tự", Toast.LENGTH_SHORT).show();
            return;
        }

        btnRegister.setEnabled(false);
        authRepository.signUp(email, password, nickname, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(String message) {
                Toast.makeText(RegisterActivity.this, message, Toast.LENGTH_SHORT).show();
                // Sau khi đăng ký thành công, AuthRepository đã lưu session, chuyển vào Home
                Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(String error) {
                btnRegister.setEnabled(true);
                Toast.makeText(RegisterActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
