package com.example.socialapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

public class SettingsFragment extends Fragment {

    private Switch switchDarkMode;
    private SharedPreferences prefs;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Infla el layout para este fragmento
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        switchDarkMode = view.findViewById(R.id.switchDarkMode);
        prefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE);

        // Recupera la preferencia guardada; si no existe, se asume false
        boolean darkMode = prefs.getBoolean("dark_mode", false);
        switchDarkMode.setChecked(darkMode);
        updateDarkMode(darkMode);

        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateDarkMode(isChecked);
            prefs.edit().putBoolean("dark_mode", isChecked).apply();
        });
    }

    private void updateDarkMode(boolean darkMode) {
        if (darkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
    }
}
