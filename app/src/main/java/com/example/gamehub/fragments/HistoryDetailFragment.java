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
import androidx.fragment.app.Fragment;
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

public class HistoryDetailFragment extends Fragment {
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
        playSimilarView = view.findViewById(R.id.history_detail_play_similar);

        view.findViewById(R.id.history_detail_back).setOnClickListener(v -> navigateBack());
        view.findViewById(R.id.history_detail_view_statistics).setOnClickListener(v -> openStatisticsRoot());
        playSimilarView.setOnClickListener(v -> openSimilarGame());

        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).setBottomNavVisible(false);
        }

        if (history == null) {
            Toast.makeText(requireContext(), "Không tìm thấy chi tiết trận này.", Toast.LENGTH_SHORT).show();
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
        subtitleView.setText(history.isSynced
                ? "Trận này đã được ghi lại trong lịch sử của bạn"
                : "Trận này vừa được thêm vào lịch sử");
        overviewGameView.setText(getDisplayGameName(history));
        overviewTitleView.setText(String.format(
                Locale.getDefault(),
                "%s trong %s",
                success ? "Hoàn thành" : "Kết thúc",
                GameRepository.formatDuration(history.timeSpent)
        ));
        overviewMetaView.setText(buildOverviewMeta(history));
        syncChipView.setText(history.isSynced ? "Đã lưu" : "Vừa chơi");
        syncChipView.setBackgroundResource(history.isSynced ? R.drawable.bg_chip_success : R.drawable.bg_chip_warning);

        primaryLabelView.setText(getPrimaryLabel(history));
        primaryValueView.setText(getPrimaryValue(history));
        primaryCaptionView.setText(getPrimaryCaption(history));

        secondaryLabelView.setText("Trạng thái");
        secondaryValueView.setText(getStatusLabel(history.status));
        secondaryCaptionView.setText(buildSecondaryCaption(history));
        playSimilarView.setText(getReplayLabel(history));
    }

    private void openSimilarGame() {
        if (history == null) {
            return;
        }
        Intent intent = null;
        if (isSudokuGame(history)) {
            intent = new Intent(requireContext(), SudokuActivity.class);
        } else if (isMemoryGame(history)) {
            intent = new Intent(requireContext(), MemoryGameActivity.class);
        } else if (isQuizGame(history)) {
            intent = new Intent(requireContext(), QuizActivity.class);
        }
        if (intent != null) {
            startActivity(intent);
        }
    }

    private void navigateBack() {
        FragmentManager fragmentManager = requireActivity().getSupportFragmentManager();
        if (fragmentManager.getBackStackEntryCount() > 0) {
            fragmentManager.popBackStack();
            return;
        }
        openStatisticsRoot();
    }

    private void openStatisticsRoot() {
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).showStatisticsRoot();
            return;
        }
        requireActivity().getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

    private String buildOverviewMeta(LocalHistory item) {
        return String.format(
                Locale.getDefault(),
                "%s · %s · Hoàn thành ngày %s",
                getDisplayGameName(item),
                getStatusLabel(item.status),
                new SimpleDateFormat("dd/MM", new Locale("vi", "VN")).format(new Date(item.playDate))
        );
    }

    private String getPrimaryLabel(LocalHistory item) {
        if (isMemoryGame(item)) {
            return "Lượt đoán";
        }
        return "Điểm";
    }

    private String getPrimaryValue(LocalHistory item) {
        if (isMemoryGame(item)) {
            return String.valueOf(item.attemptCount);
        }
        if (isSudokuGame(item) && item.score > 0) {
            return String.format(Locale.getDefault(), "+%d", item.score);
        }
        return String.valueOf(item.score);
    }

    private String getPrimaryCaption(LocalHistory item) {
        if (isMemoryGame(item)) {
            return "Tổng số lượt đoán của phiên ghi nhớ";
        }
        if (isQuizGame(item)) {
            return "Điểm số đạt được trong phiên đố vui";
        }
        return "Điểm thưởng của phiên";
    }

    private String buildSecondaryCaption(LocalHistory item) {
        if (item.detail != null && !item.detail.trim().isEmpty()) {
            return item.detail.trim();
        }
        return item.isSynced ? "Đã có trong lịch sử của bạn" : "Điểm số sẽ sớm xuất hiện trên bảng xếp hạng";
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

    private String getReplayLabel(LocalHistory item) {
        if (isSudokuGame(item)) {
            return "Chơi bàn Sudoku khác";
        }
        if (isMemoryGame(item)) {
            return "Chơi màn ghi nhớ khác";
        }
        return "Chơi lượt đố vui khác";
    }

    private String getDisplayGameName(LocalHistory item) {
        if (isQuizGame(item)) {
            return "Đố vui";
        }
        if (isMemoryGame(item)) {
            return "Ghi nhớ";
        }
        if (isSudokuGame(item)) {
            return "Sudoku";
        }
        return item.gameName;
    }

    private boolean isQuizGame(LocalHistory item) {
        String normalized = normalizeGameName(item);
        return normalized.contains("quiz") || normalized.contains("đố vui");
    }

    private boolean isMemoryGame(LocalHistory item) {
        String normalized = normalizeGameName(item);
        return normalized.contains("memory") || normalized.contains("ghi nhớ");
    }

    private boolean isSudokuGame(LocalHistory item) {
        return normalizeGameName(item).contains("sudoku");
    }

    private String normalizeGameName(LocalHistory item) {
        return item.gameName == null ? "" : item.gameName.toLowerCase(Locale.getDefault());
    }

    private boolean isSuccessful(String status) {
        return "won".equalsIgnoreCase(status) || "completed".equalsIgnoreCase(status);
    }
}
