package com.example.gamehub.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.example.gamehub.MainActivity;
import com.example.gamehub.R;
import com.example.gamehub.data.local.entities.LocalHistory;
import com.example.gamehub.data.repository.GameRepository;
import com.example.gamehub.games.memory.MemoryGameActivity;
import com.example.gamehub.games.quiz.QuizActivity;
import com.example.gamehub.games.sudoku.SudokuActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class HistoryDetailFragment extends androidx.fragment.app.Fragment {
    private static final String ARG_HISTORY_ID = "history_id";

    public static HistoryDetailFragment newInstance(int historyId) {
        HistoryDetailFragment fragment = new HistoryDetailFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_HISTORY_ID, historyId);
        fragment.setArguments(args);
        return fragment;
    }

    private GameRepository repository;
    private LocalHistory history;

    private TextView subtitleView;
    private TextView overviewGameView;
    private TextView overviewTitleView;
    private TextView overviewMetaView;
    private TextView syncChipView;
    private TextView primaryLabelView;
    private TextView primaryValueView;
    private TextView primaryCaptionView;
    private TextView secondaryLabelView;
    private TextView secondaryValueView;
    private TextView secondaryCaptionView;
    private TextView notesView;
    private TextView playSimilarView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        repository = GameRepository.getInstance(requireContext());
        int historyId = getArguments() == null ? -1 : getArguments().getInt(ARG_HISTORY_ID, -1);
        history = repository.getHistoryById(historyId);

        subtitleView = view.findViewById(R.id.history_detail_subtitle);
        overviewGameView = view.findViewById(R.id.history_detail_overview_game);
        overviewTitleView = view.findViewById(R.id.history_detail_overview_title);
        overviewMetaView = view.findViewById(R.id.history_detail_overview_meta);
        syncChipView = view.findViewById(R.id.history_detail_sync_chip);
        primaryLabelView = view.findViewById(R.id.history_detail_primary_label);
        primaryValueView = view.findViewById(R.id.history_detail_primary_value);
        primaryCaptionView = view.findViewById(R.id.history_detail_primary_caption);
        secondaryLabelView = view.findViewById(R.id.history_detail_secondary_label);
        secondaryValueView = view.findViewById(R.id.history_detail_secondary_value);
        secondaryCaptionView = view.findViewById(R.id.history_detail_secondary_caption);
        notesView = view.findViewById(R.id.history_detail_notes);
        playSimilarView = view.findViewById(R.id.history_detail_play_similar);

        view.findViewById(R.id.history_detail_back).setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
        view.findViewById(R.id.history_detail_view_statistics).setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE));
        playSimilarView.setOnClickListener(v -> openSimilarGame());

        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).setBottomNavVisible(false);
        }

        if (history == null) {
            Toast.makeText(requireContext(), "Không tìm thấy chi tiết trận trong lịch sử cục bộ.", Toast.LENGTH_SHORT).show();
            requireActivity().getSupportFragmentManager().popBackStack();
            return;
        }

        renderHistory();
    }

    @Override
    public void onDestroyView() {
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).setBottomNavVisible(true);
        }
        super.onDestroyView();
    }

    private void renderHistory() {
        boolean success = isSuccessful(history.status);
        subtitleView.setText(history.isSynced ? "Lưu cục bộ và đã đồng bộ vào hồ sơ" : "Đang lưu cục bộ và chờ đồng bộ hồ sơ");
        overviewGameView.setText(history.gameName);
        overviewTitleView.setText(String.format(
                Locale.getDefault(),
                "%s trong %s",
                success ? "Hoàn thành" : "Kết thúc",
                GameRepository.formatDuration(history.timeSpent)
        ));
        overviewMetaView.setText(buildOverviewMeta(history));
        syncChipView.setText(history.isSynced ? "Đã đồng bộ" : "Chỉ cục bộ");
        syncChipView.setBackgroundResource(history.isSynced ? R.drawable.bg_chip_success : R.drawable.bg_chip_warning);

        primaryLabelView.setText(getPrimaryLabel(history));
        primaryValueView.setText(getPrimaryValue(history));
        primaryCaptionView.setText(getPrimaryCaption(history));

        secondaryLabelView.setText("Trạng thái");
        secondaryValueView.setText(getStatusLabel(history.status));
        secondaryCaptionView.setText(history.isSynced ? "Đã đồng bộ vào hồ sơ" : "Đang chờ mạng và luồng sync");

        notesView.setText(buildNotes(history));
        playSimilarView.setText(getReplayLabel(history));
    }

    private void openSimilarGame() {
        if (history == null) {
            return;
        }
        Intent intent = null;
        String gameName = history.gameName.toLowerCase(Locale.getDefault());
        if (gameName.contains("sudoku")) {
            intent = new Intent(requireContext(), SudokuActivity.class);
        } else if (gameName.contains("ghi nhớ")) {
            intent = new Intent(requireContext(), MemoryGameActivity.class);
        } else if (gameName.contains("đố vui")) {
            intent = new Intent(requireContext(), QuizActivity.class);
        }
        if (intent != null) {
            startActivity(intent);
        }
    }

    private String buildOverviewMeta(LocalHistory item) {
        return String.format(
                Locale.getDefault(),
                "%s · %s · Hoàn thành ngày %s",
                getPrimaryLabel(item).toLowerCase(Locale.getDefault()) + " " + getPrimaryValue(item),
                getStatusLabel(item.status).toLowerCase(Locale.getDefault()),
                new SimpleDateFormat("dd/MM", new Locale("vi", "VN")).format(new Date(item.playDate))
        );
    }

    private String getPrimaryLabel(LocalHistory item) {
        String gameName = item.gameName.toLowerCase(Locale.getDefault());
        if (gameName.contains("ghi nhớ")) {
            return "Lượt";
        }
        if (gameName.contains("đố vui")) {
            return "Điểm";
        }
        return "Điểm";
    }

    private String getPrimaryValue(LocalHistory item) {
        String gameName = item.gameName.toLowerCase(Locale.getDefault());
        if (gameName.contains("sudoku") && item.score > 0) {
            return String.format(Locale.getDefault(), "+%d", item.score);
        }
        return String.valueOf(item.score);
    }

    private String getPrimaryCaption(LocalHistory item) {
        String gameName = item.gameName.toLowerCase(Locale.getDefault());
        if (gameName.contains("đố vui")) {
            return "Số câu đúng của phiên";
        }
        if (gameName.contains("ghi nhớ")) {
            return "Số lượt ghép được ghi nhận";
        }
        return "Điểm thưởng của phiên";
    }

    private String getStatusLabel(String status) {
        if ("won".equalsIgnoreCase(status) || "completed".equalsIgnoreCase(status)) {
            return "Thắng";
        }
        if ("lost".equalsIgnoreCase(status)) {
            return "Thua";
        }
        return status == null || status.isEmpty() ? "N/A" : status;
    }

    private String buildNotes(LocalHistory item) {
        StringBuilder builder = new StringBuilder();
        builder.append("Phiên này đang hiển thị đúng theo dữ liệu thật có trong Local_History: điểm, thời gian, trạng thái và trạng thái đồng bộ.");
        if (!item.isSynced) {
            builder.append(" Bản ghi hiện vẫn ở hàng chờ cục bộ.");
        }
        return builder.toString();
    }

    private String getReplayLabel(LocalHistory item) {
        String gameName = item.gameName.toLowerCase(Locale.getDefault());
        if (gameName.contains("sudoku")) {
            return "Chơi bàn tương tự";
        }
        if (gameName.contains("ghi nhớ")) {
            return "Chơi màn ghi nhớ khác";
        }
        return "Chơi lượt đố vui khác";
    }

    private boolean isSuccessful(String status) {
        return "won".equalsIgnoreCase(status) || "completed".equalsIgnoreCase(status);
    }
}
