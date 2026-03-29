package com.example.gamehub;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.example.gamehub.fragments.ChatFragment;
import com.example.gamehub.fragments.GamesFragment;
import com.example.gamehub.fragments.HistoryDetailFragment;
import com.example.gamehub.fragments.HistoryFragment;
import com.example.gamehub.fragments.LeaderboardFragment;
import com.example.gamehub.fragments.ProfileFragment;
import com.example.gamehub.fragments.ShellPlaceholderFragment;
import com.example.gamehub.fragments.StatisticsFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {
    private BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        bottomNavigationView = findViewById(R.id.bottom_nav);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            showTopLevelDestination(item.getItemId());
            return true;
        });
        bottomNavigationView.setOnItemReselectedListener(item -> showTopLevelDestination(item.getItemId()));

        if (savedInstanceState == null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_home);
        }
    }

    public void showLeaderboard() {
        openStatisticsChild(new LeaderboardFragment());
    }

    public void showHistory() {
        openStatisticsChild(new HistoryFragment());
    }

    public void showHistoryDetail(int historyId) {
        Fragment fragment = HistoryDetailFragment.newInstance(historyId);
        if (bottomNavigationView.getSelectedItemId() != R.id.nav_statistics) {
            bottomNavigationView.setSelectedItemId(R.id.nav_statistics);
            findViewById(R.id.fragment_container).post(() -> openStatisticsChild(fragment));
            return;
        }
        openStatisticsChild(fragment);
    }

    public void showStatistics() {
        bottomNavigationView.setSelectedItemId(R.id.nav_statistics);
    }

    public void setBottomNavVisible(boolean visible) {
        bottomNavigationView.setVisibility(visible ? View.VISIBLE : View.GONE);
        View fragmentContainer = findViewById(R.id.fragment_container);
        ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) fragmentContainer.getLayoutParams();
        layoutParams.bottomMargin = visible ? getResources().getDimensionPixelSize(R.dimen.shell_bottom_nav_margin) : 0;
        fragmentContainer.setLayoutParams(layoutParams);
    }

    private void showTopLevelDestination(int itemId) {
        clearBackStack();
        setBottomNavVisible(true);
        if (itemId == R.id.nav_home) {
            replaceFragment(ShellPlaceholderFragment.newInstance(getString(R.string.nav_home), getString(R.string.placeholder_home_subtitle)), false);
            return;
        }
        if (itemId == R.id.nav_games) {
            replaceFragment(new GamesFragment(), false);
            return;
        }
        if (itemId == R.id.nav_friends) {
            replaceFragment(new ChatFragment(), false);
            return;
        }
        if (itemId == R.id.nav_statistics) {
            replaceFragment(new StatisticsFragment(), false);
            return;
        }
        if (itemId == R.id.nav_profile) {
            replaceFragment(new ProfileFragment(), false);
        }
    }

    private void openStatisticsChild(Fragment fragment) {
        if (bottomNavigationView.getSelectedItemId() != R.id.nav_statistics) {
            bottomNavigationView.setSelectedItemId(R.id.nav_statistics);
            return;
        }
        setBottomNavVisible(true);
        replaceFragment(fragment, true);
    }

    private void clearBackStack() {
        getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

    private void replaceFragment(Fragment fragment, boolean addToBackStack) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }
}
