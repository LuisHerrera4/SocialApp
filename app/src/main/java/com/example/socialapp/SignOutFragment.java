package com.example.socialapp;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import io.appwrite.Client;
import io.appwrite.coroutines.CoroutineCallback;
import io.appwrite.services.Account;

public class SignOutFragment extends Fragment {
    NavController navController;
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup
            container,
                             Bundle savedInstanceState) {
// Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_sign_out, container,
                false);
    }
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle
            savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        navController = Navigation.findNavController(view); //<-----------------
                Client client = new
                Client(requireActivity().getApplicationContext());
        client.setProject(getString(R.string.APPWRITE_PROJECT_ID));
// Cerramos la sesión actual
        Handler mainHandler = new Handler(Looper.getMainLooper());
        Account account = new Account(client);
        account.deleteSession(
                "current", // sessionId
                new CoroutineCallback<>((result, error) -> {
                    if (error != null) {
                        error.printStackTrace();
                        return;
                    }
                    mainHandler.post(() ->
                            Navigation.findNavController(view).navigate(R.id.signInFragment));
//Log.d("Appwrite", result.toString());
                })
        );
    }
}