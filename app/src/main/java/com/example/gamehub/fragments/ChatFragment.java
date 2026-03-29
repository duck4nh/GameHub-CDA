package com.example.gamehub.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gamehub.MainActivity;
import com.example.gamehub.R;
import com.example.gamehub.adapter.ChatAdapter;
import com.example.gamehub.data.repository.GameRepository;
import com.example.gamehub.models.ChatMessage;

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
    private OnBackPressedCallback backPressedCallback;
    private String activeRoomId = ROOM_GENERAL;
    private String activeRoomSuffix = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        repository = GameRepository.getInstance(requireContext());
        adapter = new ChatAdapter();

        roomsContainer = view.findViewById(R.id.community_rooms_container);
        chatRoomContainer = view.findViewById(R.id.chat_room_container);
        chatMessages = view.findViewById(R.id.chat_messages);
        chatTitle = view.findViewById(R.id.chat_title);
        chatSubtitle = view.findViewById(R.id.chat_subtitle);
        generalRoomSubtitleView = view.findViewById(R.id.room_general_subtitle);
        quizRoomSubtitleView = view.findViewById(R.id.room_quiz_subtitle);
        sudokuRoomSubtitleView = view.findViewById(R.id.room_sudoku_subtitle);
        messageInput = view.findViewById(R.id.message_input);

        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        chatMessages.setLayoutManager(layoutManager);
        chatMessages.setAdapter(adapter);

        view.findViewById(R.id.open_featured_room).setOnClickListener(v -> openRoom(ROOM_WEEKLY, "Thử thách tuần", "đang hoạt động"));
        view.findViewById(R.id.room_general).setOnClickListener(v -> openRoom(ROOM_GENERAL, "Thảo luận chung", "có kiểm duyệt"));
        view.findViewById(R.id.room_quiz).setOnClickListener(v -> openRoom(ROOM_QUIZ, "Góc Đố vui", "trao đổi chủ đề"));
        view.findViewById(R.id.room_sudoku).setOnClickListener(v -> openRoom(ROOM_SUDOKU, "Câu lạc bộ Sudoku", "xếp hạng tuần"));
        view.findViewById(R.id.chat_back).setOnClickListener(v -> showRooms());
        view.findViewById(R.id.send_button).setOnClickListener(v -> sendMessage());

        backPressedCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                showRooms();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), backPressedCallback);

        showRooms();
        refreshRoomSummaries();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshRoomSummaries();
    }

    private void openRoom(String roomId, String title, String suffix) {
        activeRoomId = roomId;
        activeRoomSuffix = suffix;
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
                    chatMessages.post(() -> chatMessages.scrollToPosition(adapter.getItemCount() - 1));
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
        chatRoomContainer.setVisibility(View.GONE);
        roomsContainer.setVisibility(View.VISIBLE);
        refreshRoomSummaries();
        if (backPressedCallback != null) {
            backPressedCallback.setEnabled(false);
        }
        updateBottomNav(true);
    }

    private void sendMessage() {
        repository.sendChatMessage(activeRoomId, messageInput.getText().toString(), new GameRepository.ActionCallback() {
            @Override
            public void onSuccess() {
                if (!isAdded()) {
                    return;
                }
                messageInput.setText("");
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
        java.util.HashSet<String> participants = new java.util.HashSet<>();
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

    @Override
    public void onDestroyView() {
        repository.stopChatMessagesListener();
        updateBottomNav(true);
        super.onDestroyView();
    }
}
