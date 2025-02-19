package com.example.socialapp;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import com.google.android.material.snackbar.Snackbar;

import io.appwrite.Client;
import io.appwrite.coroutines.CoroutineCallback;
import io.appwrite.services.Account;

public class SignInFragment extends Fragment {
    NavController navController;
    private EditText emailEditText, passwordEditText;
    private Button emailSignInButton;
    private LinearLayout signInForm;
    private ProgressBar signInProgressBar;
    Client client;
    Account account;
    public SignInFragment() {}
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_sign_in, container, false);
    }
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        navController = Navigation.findNavController(view);

        emailEditText = view.findViewById(R.id.emailEditText);
        passwordEditText = view.findViewById(R.id.passwordEditText);
        emailSignInButton = view.findViewById(R.id.emailSignInButton);
        signInForm = view.findViewById(R.id.signInForm);
        signInProgressBar = view.findViewById(R.id.signInProgressBar);

        view.findViewById(R.id.gotoCreateAccountTextView).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                navController.navigate(R.id.registerFragment);
            }
        });
        Handler mainHandler = new Handler(Looper.getMainLooper());
        client = new Client(requireActivity().getApplicationContext());
        client.setProject(getString(R.string.APPWRITE_PROJECT_ID));
        account = new Account(client);
        account.getSession(
                "current", // sessionId
                new CoroutineCallback<>((result, error) -> {
                    if (error != null) {
                        error.printStackTrace();
                        return;
                    }
                    if (result != null)
                        mainHandler.post(() -> actualizarUI("Ok"));
                })
        );
        emailSignInButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                accederConEmail();
            }
        });

    }

    private void accederConEmail () {
        signInForm.setVisibility(View.GONE);
        signInProgressBar.setVisibility(View.VISIBLE);
        Account account = new Account(client);
        Handler mainHandler = new Handler(Looper.getMainLooper());
        account.createEmailPasswordSession(
                emailEditText.getText().toString(), // email
                passwordEditText.getText().toString(), // password
                new CoroutineCallback<>((result, error) -> {
                    if (error != null) {
                        Snackbar.make(requireView(), "Error: " + error.toString(), Snackbar.LENGTH_LONG).show();
                    } else {
                        System.out.println("Sesión creada para el usuario:" + result.toString());
                        mainHandler.post(() -> actualizarUI("Ok"));
                    }
                    mainHandler.post(() -> {
                        signInForm.setVisibility(View.VISIBLE);
                        signInProgressBar.setVisibility(View.GONE);
                    });
                })
        );
    }
    private void actualizarUI (String currentUser){
        if (currentUser != null) {
            navController.navigate(R.id.homeFragment);
        }
    }
}
