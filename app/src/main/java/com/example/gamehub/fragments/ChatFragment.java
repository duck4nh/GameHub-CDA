package com.example.gamehub.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

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

public class ChatFragment extends Fragment {
    private GameRepository repository;
    private ChatAdapter adapter;
    private View roomsContainer;
    private View chatRoomContainer;
    private RecyclerView chatMessages;
    private TextView chatTitle;
    private TextView chatSubtitle;
    private EditText messageInput;
    private OnBackPressedCallback backPressedCallback;

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
        messageInput = view.findViewById(R.id.message_input);

        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        chatMessages.setLayoutManager(layoutManager);
        chatMessages.setAdapter(adapter);

        view.findViewById(R.id.open_featured_room).setOnClickListener(v -> openRoom("Thử thách tuần", "214 thành viên · đang hoạt động"));
        view.findViewById(R.id.room_general).setOnClickListener(v -> openRoom("Thảo luận chung", "214 thành viên · có kiểm duyệt"));
        view.findViewById(R.id.room_quiz).setOnClickListener(v -> openRoom("Góc Đố vui", "91 thành viên · trao đổi chủ đề"));
        view.findViewById(R.id.room_sudoku).setOnClickListener(v -> openRoom("Câu lạc bộ Sudoku", "76 thành viên · xếp hạng tuần"));
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
    }

    private void openRoom(String title, String subtitle) {
        chatTitle.setText(title);
        chatSubtitle.setText(subtitle);
        roomsContainer.setVisibility(View.GONE);
        chatRoomContainer.setVisibility(View.VISIBLE);
        backPressedCallback.setEnabled(true);
        updateBottomNav(false);
        refreshMessages(true);
    }

    private void showRooms() {
        chatRoomContainer.setVisibility(View.GONE);
        roomsContainer.setVisibility(View.VISIBLE);
        if (backPressedCallback != null) {
            backPressedCallback.setEnabled(false);
        }
        updateBottomNav(true);
    }

    private void sendMessage() {
        repository.sendChatMessage(messageInput.getText().toString());
        messageInput.setText("");
        refreshMessages(true);
    }

    private void refreshMessages(boolean scrollToBottom) {
        adapter.submitList(repository.getChatMessages(), repository.getCurrentUid());
        if (scrollToBottom) {
            chatMessages.post(() -> {
                if (adapter.getItemCount() > 0) {
                    chatMessages.scrollToPosition(adapter.getItemCount() - 1);
                }
            });
        }
    }

    private void updateBottomNav(boolean visible) {
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).setBottomNavVisible(visible);
        }
    }

    @Override
    public void onDestroyView() {
        updateBottomNav(true);
        super.onDestroyView();
    }
}
