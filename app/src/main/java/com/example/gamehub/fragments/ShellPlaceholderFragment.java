package com.example.gamehub.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.gamehub.R;

public class ShellPlaceholderFragment extends Fragment {
    private static final String ARG_TITLE = "arg_title";
    private static final String ARG_SUBTITLE = "arg_subtitle";

    public static ShellPlaceholderFragment newInstance(String title, String subtitle) {
        ShellPlaceholderFragment fragment = new ShellPlaceholderFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putString(ARG_SUBTITLE, subtitle);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_shell_placeholder, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        String title = args == null ? "" : args.getString(ARG_TITLE, "");
        String subtitle = args == null ? "" : args.getString(ARG_SUBTITLE, "");
        ((TextView) view.findViewById(R.id.placeholder_title)).setText(title);
        ((TextView) view.findViewById(R.id.placeholder_subtitle)).setText(subtitle);
    }
}
