package com.example.gamehub.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.gamehub.MainActivity;
import com.example.gamehub.R;
import com.example.gamehub.adapter.ChatAdapter;
import com.example.gamehub.data.repository.GameRepository;
import com.example.gamehub.models.ChatMessage;
import com.example.gamehub.models.User;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class ChatFragment extends Fragment {
    private static final String ROOM_WEEKLY = "weekly_challenge";
    private static final String ROOM_GENERAL = "general";
    private static final String ROOM_QUIZ = "quiz";
    private static final String ROOM_SUDOKU = "sudoku";

    private GameRepository repository;
    private ChatAdapter adapter;
    private View roomsContainer;
    private View chatRoomContainer;
    private RecyclerView chatMessages;
    private TextView chatTitle;
    private TextView chatSubtitle;
    private TextView generalRoomSubtitleView;
    private TextView quizRoomSubtitleView;
    private TextView sudokuRoomSubtitleView;
    private EditText messageInput;
    private View composerReplyContainer;
    private TextView composerReplyTitle;
    private TextView composerReplyContent;
    private ImageView communityHeaderAvatar;
    private OnBackPressedCallback backPressedCallback;
    private String activeRoomId = ROOM_GENERAL;
    private String activeRoomSuffix = "";
    private boolean requestedCurrentUserProfile;
    @Nullable
    private ChatMessage replyingToMessage;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        repository = GameRepository.getInstance(requireContext());
        adapter = new ChatAdapter();
        adapter.setMessageActionListener(new ChatAdapter.MessageActionListener() {
            @Override
            public void onMessageLongPressed(ChatMessage message) {
                showMessageActions(message);
            }

            @Override
            public void onReplyPreviewClicked(ChatMessage message) {
                scrollToReferencedMessage(message);
            }
        });

        roomsContainer = view.findViewById(R.id.community_rooms_container);
        chatRoomContainer = view.findViewById(R.id.chat_room_container);
        chatMessages = view.findViewById(R.id.chat_messages);
        chatTitle = view.findViewById(R.id.chat_title);
        chatSubtitle = view.findViewById(R.id.chat_subtitle);
        generalRoomSubtitleView = view.findViewById(R.id.room_general_subtitle);
        quizRoomSubtitleView = view.findViewById(R.id.room_quiz_subtitle);
        sudokuRoomSubtitleView = view.findViewById(R.id.room_sudoku_subtitle);
        messageInput = view.findViewById(R.id.message_input);
        composerReplyContainer = view.findViewById(R.id.composer_reply_container);
        composerReplyTitle = view.findViewById(R.id.composer_reply_title);
        composerReplyContent = view.findViewById(R.id.composer_reply_content);
        communityHeaderAvatar = view.findViewById(R.id.community_header_avatar);

        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        chatMessages.setLayoutManager(layoutManager);
        chatMessages.setAdapter(adapter);

        view.findViewById(R.id.open_featured_room).setOnClickListener(v -> openRoom(ROOM_WEEKLY, "Thử thách tuần", "đang hoạt động"));
        view.findViewById(R.id.room_general).setOnClickListener(v -> openRoom(ROOM_GENERAL, "Thảo luận chung", "có kiểm duyệt"));
        view.findViewById(R.id.room_quiz).setOnClickListener(v -> openRoom(ROOM_QUIZ, "Góc Đố vui", "trao đổi chủ đề"));
        view.findViewById(R.id.room_sudoku).setOnClickListener(v -> openRoom(ROOM_SUDOKU, "Câu lạc bộ Sudoku", "xếp hạng tuần"));
        view.findViewById(R.id.chat_back).setOnClickListener(v -> showRooms());
        view.findViewById(R.id.send_button).setOnClickListener(v -> sendMessage());
        view.findViewById(R.id.composer_reply_close).setOnClickListener(v -> clearReplyComposer());

        backPressedCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                showRooms();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), backPressedCallback);

        showRooms();
        bindCurrentUserAvatar();
        refreshRoomSummaries();
    }

    @Override
    public void onResume() {
        super.onResume();
        bindCurrentUserAvatar();
        refreshRoomSummaries();
    }

    private void openRoom(String roomId, String title, String suffix) {
        activeRoomId = roomId;
        activeRoomSuffix = suffix;
        clearReplyComposer();
        chatTitle.setText(title);
        chatSubtitle.setText("Đang tải cuộc trò chuyện...");
        roomsContainer.setVisibility(View.GONE);
        chatRoomContainer.setVisibility(View.VISIBLE);
        backPressedCallback.setEnabled(true);
        updateBottomNav(false);
        repository.startChatMessagesListener(roomId, new GameRepository.ChatMessagesListener() {
            @Override
            public void onMessagesChanged(List<ChatMessage> messages) {
                if (!isAdded()) {
                    return;
                }
                adapter.submitList(messages, repository.getCurrentUid());
                String subtitle = buildRoomSubtitle(countParticipants(messages), activeRoomSuffix);
                if (messages.isEmpty()) {
                    chatSubtitle.setText(subtitle + " · chưa có tin nhắn");
                } else {
                    chatSubtitle.setText(subtitle);
                    chatMessages.post(() -> chatMessages.scrollToPosition(Math.max(0, adapter.getItemCount() - 1)));
                }
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) {
                    return;
                }
                chatSubtitle.setText("Không tải được cuộc trò chuyện");
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showRooms() {
        repository.stopChatMessagesListener();
        clearReplyComposer();
        chatRoomContainer.setVisibility(View.GONE);
        roomsContainer.setVisibility(View.VISIBLE);
        refreshRoomSummaries();
        if (backPressedCallback != null) {
            backPressedCallback.setEnabled(false);
        }
        updateBottomNav(true);
    }

    private void sendMessage() {
        repository.sendChatMessage(activeRoomId, messageInput.getText().toString(), replyingToMessage, new GameRepository.ActionCallback() {
            @Override
            public void onSuccess() {
                if (!isAdded()) {
                    return;
                }
                messageInput.setText("");
                clearReplyComposer();
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) {
                    return;
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void beginReply(ChatMessage message) {
        replyingToMessage = message;
        composerReplyContainer.setVisibility(View.VISIBLE);
        String senderLabel = message.getSenderUid().equals(repository.getCurrentUid()) ? "chính bạn" : message.getSenderNickname();
        composerReplyTitle.setText("Đang trả lời " + senderLabel);
        composerReplyContent.setText(message.getContent());
        messageInput.requestFocus();
    }

    private void clearReplyComposer() {
        replyingToMessage = null;
        composerReplyContainer.setVisibility(View.GONE);
        composerReplyTitle.setText("");
        composerReplyContent.setText("");
    }

    private void showMessageActions(ChatMessage message) {
        if (!isAdded()) {
            return;
        }

        List<CharSequence> options = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        options.add("Trả lời");
        actions.add(() -> beginReply(message));

        options.add("👍 Thích");
        actions.add(() -> reactToMessage(message, "👍"));
        options.add("❤️ Yêu thích");
        actions.add(() -> reactToMessage(message, "❤️"));
        options.add("😂 Haha");
        actions.add(() -> reactToMessage(message, "😂"));
        options.add("😮 Bất ngờ");
        actions.add(() -> reactToMessage(message, "😮"));

        if (!message.getUserReaction(repository.getCurrentUid()).isEmpty()) {
            options.add("Gỡ cảm xúc");
            actions.add(() -> reactToMessage(message, ""));
        }

        new AlertDialog.Builder(requireContext())
                .setItems(options.toArray(new CharSequence[0]), (dialog, which) -> actions.get(which).run())
                .show();
    }

    private void reactToMessage(ChatMessage message, String emoji) {
        repository.toggleChatReaction(message, emoji, new GameRepository.ActionCallback() {
            @Override
            public void onSuccess() {
                // Snapshot listener sẽ tự cập nhật lại danh sách.
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) {
                    return;
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void scrollToReferencedMessage(ChatMessage message) {
        String targetMessageId = message.getReplyToMessageId();
        int position = adapter.findPositionByMessageId(targetMessageId);
        if (position == RecyclerView.NO_POSITION) {
            Toast.makeText(requireContext(), "Không tìm thấy tin nhắn gốc trong cuộc trò chuyện này.", Toast.LENGTH_SHORT).show();
            return;
        }
        adapter.highlightMessage(targetMessageId);
        RecyclerView.LayoutManager layoutManager = chatMessages.getLayoutManager();
        if (layoutManager instanceof LinearLayoutManager) {
            ((LinearLayoutManager) layoutManager).scrollToPositionWithOffset(position, 32);
        } else {
            chatMessages.scrollToPosition(position);
        }
        chatMessages.postDelayed(() -> adapter.highlightMessage(null), 1600L);
    }

    private void refreshRoomSummaries() {
        if (!isAdded()) {
            return;
        }
        setRoomSummaryFallbacks();
        repository.fetchChatRoomParticipantCounts(new GameRepository.ChatRoomCountsCallback() {
            @Override
            public void onLoaded(Map<String, Integer> participantCounts) {
                if (!isAdded()) {
                    return;
                }
                generalRoomSubtitleView.setText(buildRoomSubtitle(participantCounts.get(ROOM_GENERAL), "có kiểm duyệt"));
                quizRoomSubtitleView.setText(buildRoomSubtitle(participantCounts.get(ROOM_QUIZ), "trao đổi chủ đề"));
                sudokuRoomSubtitleView.setText(buildRoomSubtitle(participantCounts.get(ROOM_SUDOKU), "xếp hạng tuần"));
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) {
                    return;
                }
                setRoomSummaryFallbacks();
            }
        });
    }

    private void setRoomSummaryFallbacks() {
        generalRoomSubtitleView.setText("Đang tải thành viên · có kiểm duyệt");
        quizRoomSubtitleView.setText("Đang tải thành viên · trao đổi chủ đề");
        sudokuRoomSubtitleView.setText("Đang tải thành viên · xếp hạng tuần");
    }

    private String buildRoomSubtitle(Integer participantCount, String suffix) {
        int safeCount = participantCount == null ? 0 : Math.max(0, participantCount);
        return safeCount + " người tham gia · " + suffix;
    }

    private int countParticipants(List<ChatMessage> messages) {
        HashSet<String> participants = new HashSet<>();
        for (ChatMessage message : messages) {
            if (!message.getSenderUid().isEmpty()) {
                participants.add(message.getSenderUid());
            } else if (!message.getSenderNickname().trim().isEmpty()) {
                participants.add(message.getSenderNickname().trim().toLowerCase());
            }
        }
        return participants.size();
    }

    private void updateBottomNav(boolean visible) {
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).setBottomNavVisible(visible);
        }
    }

    private void bindCurrentUserAvatar() {
        loadAvatar(repository.getCachedAvatarUrl());
        if (requestedCurrentUserProfile) {
            return;
        }
        requestedCurrentUserProfile = true;
        repository.fetchCurrentUserProfile(new GameRepository.UserProfileCallback() {
            @Override
            public void onLoaded(User user) {
                if (!isAdded()) {
                    return;
                }
                loadAvatar(user.getAvatarUrl());
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) {
                    return;
                }
                loadAvatar("");
            }
        });
    }

    private void loadAvatar(@Nullable String url) {
        if (!isAdded()) {
            return;
        }
        String optimizedUrl = url == null ? "" : url.trim();
        if (optimizedUrl.contains("/svg")) {
            optimizedUrl = optimizedUrl.replace("/svg", "/png");
        } else if (optimizedUrl.endsWith(".svg")) {
            optimizedUrl = optimizedUrl.replace(".svg", ".png");
        }
        String cacheKey = optimizedUrl.isEmpty() ? "__fallback__" : optimizedUrl;
        loadIntoAvatarView(communityHeaderAvatar, optimizedUrl, cacheKey);
    }

    private void loadIntoAvatarView(@Nullable ImageView imageView, String url, String cacheKey) {
        if (imageView == null) {
            return;
        }
        Object currentTag = imageView.getTag();
        if (cacheKey.equals(currentTag)) {
            return;
        }
        imageView.setTag(cacheKey);
        Glide.with(this)
                .load(url.isEmpty() ? R.drawable.img_avatar_cat : url)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.img_avatar_cat)
                .error(R.drawable.img_avatar_cat)
                .circleCrop()
                .into(imageView);
    }

    @Override
    public void onDestroyView() {
        repository.stopChatMessagesListener();
        updateBottomNav(true);
        super.onDestroyView();
    }
}
