package com.example.gamehub.activities;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.gamehub.R;
import com.example.gamehub.data.local.AppDatabase;
import com.example.gamehub.data.local.entities.LocalFriend;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FriendsActivity extends AppCompatActivity {

    private LinearLayout llSuggestions, llRequests, llFriendList;
    private ImageView btnBack;
    private EditText etSearchFriend;
    private AppDatabase db;
    private FirebaseFirestore firestore;
    private String currentUid;
    private ListenerRegistration usersRegistration, friendshipsRegistration;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friends);

        db = AppDatabase.getInstance(this);
        firestore = FirebaseFirestore.getInstance();
        currentUid = FirebaseAuth.getInstance().getUid();

        initViews();
        setupListeners();
        
        loadLocalData();
        listenToFriendships();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (usersRegistration != null) usersRegistration.remove();
        if (friendshipsRegistration != null) friendshipsRegistration.remove();
        databaseExecutor.shutdown();
    }

    private void initViews() {
        llSuggestions = findViewById(R.id.llSuggestions);
        llRequests = findViewById(R.id.llRequests);
        llFriendList = findViewById(R.id.llFriendList);
        btnBack = findViewById(R.id.btnBack);
        etSearchFriend = findViewById(R.id.etSearchFriend);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        etSearchFriend.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderUi(s.toString().toLowerCase().trim());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadLocalData() {
        databaseExecutor.execute(() -> {
            List<LocalFriend> localFriends = db.friendDao().getAllFriends();
            runOnUiThread(() -> renderUiList(localFriends, etSearchFriend.getText().toString().trim()));
        });
    }

    private void listenToFriendships() {
        if (currentUid == null) return;

        usersRegistration = firestore.collection("Users").addSnapshotListener((userSnap, userErr) -> {
            if (userErr != null || userSnap == null) return;

            Map<String, LocalFriend> allUsersMap = new HashMap<>();
            for (DocumentSnapshot doc : userSnap.getDocuments()) {
                String uid = doc.getId();
                if (uid.equals(currentUid)) continue;
                String nickname = doc.getString("nickname");
                String avatar = doc.getString("avatar_url");
                allUsersMap.put(uid, new LocalFriend(uid, nickname != null ? nickname : "Người chơi", avatar, "suggestion"));
            }

            if (friendshipsRegistration != null) friendshipsRegistration.remove();

            friendshipsRegistration = firestore.collection("Friendships").addSnapshotListener((friendSnap, friendErr) -> {
                if (friendErr != null || friendSnap == null) return;

                Map<String, LocalFriend> usersCopy = new HashMap<>(allUsersMap);
                List<LocalFriend> finalFriends = new ArrayList<>();

                for (DocumentSnapshot doc : friendSnap.getDocuments()) {
                    String from = doc.getString("from_uid");
                    String to = doc.getString("to_uid");
                    String status = doc.getString("status");

                    if (currentUid.equals(from)) {
                        LocalFriend f = usersCopy.get(to);
                        if (f != null) {
                            f.status = "accepted".equals(status) ? "accepted" : "sent";
                            finalFriends.add(new LocalFriend(f.friend_uid, f.nickname, f.avatar_path, f.status));
                            usersCopy.remove(to);
                        }
                    } else if (currentUid.equals(to)) {
                        LocalFriend f = usersCopy.get(from);
                        if (f != null) {
                            f.status = "accepted".equals(status) ? "accepted" : "pending";
                            finalFriends.add(new LocalFriend(f.friend_uid, f.nickname, f.avatar_path, f.status));
                            usersCopy.remove(from);
                        }
                    }
                }
                
                for (LocalFriend f : usersCopy.values()) {
                    finalFriends.add(new LocalFriend(f.friend_uid, f.nickname, f.avatar_path, "suggestion"));
                }

                databaseExecutor.execute(() -> {
                    db.friendDao().deleteAllFriends();
                    db.friendDao().insertAll(finalFriends);
                    runOnUiThread(() -> renderUiList(finalFriends, etSearchFriend.getText().toString().trim()));
                });
            });
        });
    }

    private void renderUi(String query) {
        databaseExecutor.execute(() -> {
            List<LocalFriend> friends = db.friendDao().getAllFriends();
            runOnUiThread(() -> renderUiList(friends, query));
        });
    }

    private void renderUiList(List<LocalFriend> friends, String query) {
        llSuggestions.removeAllViews();
        llRequests.removeAllViews();
        llFriendList.removeAllViews();

        if (friends == null) return;

        for (LocalFriend f : friends) {
            if (!query.isEmpty() && !f.nickname.toLowerCase().contains(query)) continue;

            switch (f.status) {
                case "accepted":
                    addFriendItem(f);
                    break;
                case "pending":
                    addRequestItem(f);
                    break;
                case "sent":
                    addSentItem(f);
                    break;
                case "suggestion":
                    addSuggestionItem(f);
                    break;
            }
        }
    }

    private void addSuggestionItem(LocalFriend friend) {
        View view = getLayoutInflater().inflate(R.layout.item_friend_action, llSuggestions, false);
        setupItem(view, friend, "+", v -> {
            updateLocalStatus(friend.friend_uid, "sent");
            String id = currentUid + "_" + friend.friend_uid;
            Map<String, Object> data = new HashMap<>();
            data.put("from_uid", currentUid);
            data.put("to_uid", friend.friend_uid);
            data.put("status", "pending");
            firestore.collection("Friendships").document(id).set(data);
        });
        llSuggestions.addView(view);
    }

    private void addSentItem(LocalFriend friend) {
        View view = getLayoutInflater().inflate(R.layout.item_friend_action, llSuggestions, false);
        setupItem(view, friend, "Hủy", v -> {
            updateLocalStatus(friend.friend_uid, "suggestion");
            String id = currentUid + "_" + friend.friend_uid;
            firestore.collection("Friendships").document(id).delete();
        });
        llSuggestions.addView(view);
    }

    private void addRequestItem(LocalFriend friend) {
        View view = getLayoutInflater().inflate(R.layout.item_friend_request, llRequests, false);
        ((TextView) view.findViewById(R.id.tvFriendName)).setText(friend.nickname);
        ImageView ivAvatar = view.findViewById(R.id.ivAvatar);
        loadAvatar(ivAvatar, friend.avatar_path);

        view.findViewById(R.id.btnDecline).setOnClickListener(v -> {
            removeLocalFriend(friend.friend_uid);
            String id = friend.friend_uid + "_" + currentUid;
            firestore.collection("Friendships").document(id).delete();
        });

        view.findViewById(R.id.btnAccept).setOnClickListener(v -> {
            updateLocalStatus(friend.friend_uid, "accepted");
            String id = friend.friend_uid + "_" + currentUid;
            Map<String, Object> data = new HashMap<>();
            data.put("status", "accepted");
            firestore.collection("Friendships").document(id).update(data);
        });
        llRequests.addView(view);
    }

    private void addFriendItem(LocalFriend friend) {
        View view = getLayoutInflater().inflate(R.layout.item_friend_action, llFriendList, false);
        setupItem(view, friend, "-", v -> {
            updateLocalStatus(friend.friend_uid, "suggestion");
            String id1 = currentUid + "_" + friend.friend_uid;
            String id2 = friend.friend_uid + "_" + currentUid;
            firestore.collection("Friendships").document(id1).delete();
            firestore.collection("Friendships").document(id2).delete();
        });
        llFriendList.addView(view);
    }

    private void setupItem(View view, LocalFriend friend, String actionText, View.OnClickListener listener) {
        TextView tvName = view.findViewById(R.id.tvFriendName);
        ImageView ivAvatar = view.findViewById(R.id.ivAvatar);
        TextView btnAction = view.findViewById(R.id.btnAction);

        tvName.setText(friend.nickname);
        loadAvatar(ivAvatar, friend.avatar_path);
        btnAction.setText(actionText);
        btnAction.setOnClickListener(listener);
        
        if ("Hủy".equals(actionText)) {
            btnAction.setTextSize(14);
        } else {
            btnAction.setTextSize(24);
        }
    }

    private void loadAvatar(ImageView imageView, String url) {
        if (isFinishing() || isDestroyed() || imageView == null) return;
        
        if (url == null || url.isEmpty()) {
            imageView.setImageResource(R.drawable.img_avatar_cat);
            return;
        }

        String optimizedUrl = url;
        if (url.contains("/svg")) {
            optimizedUrl = url.replace("/svg", "/png");
        } else if (url.endsWith(".svg")) {
            optimizedUrl = url.replace(".svg", ".png");
        }

        Glide.with(this)
                .load(optimizedUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL) // Đã thêm dòng lưu cache để xem offline
                .placeholder(R.drawable.img_avatar_cat)
                .error(R.drawable.img_avatar_cat)
                .circleCrop()
                .into(imageView);
    }

    private void updateLocalStatus(String uid, String status) {
        databaseExecutor.execute(() -> {
            db.friendDao().updateStatus(uid, status);
            List<LocalFriend> updatedList = db.friendDao().getAllFriends();
            runOnUiThread(() -> renderUiList(updatedList, etSearchFriend.getText().toString().trim()));
        });
    }

    private void removeLocalFriend(String uid) {
        databaseExecutor.execute(() -> {
            db.friendDao().deleteFriend(uid);
            List<LocalFriend> updatedList = db.friendDao().getAllFriends();
            runOnUiThread(() -> renderUiList(updatedList, etSearchFriend.getText().toString().trim()));
        });
    }
}
