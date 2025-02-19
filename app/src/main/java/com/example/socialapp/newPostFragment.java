package com.example.socialapp;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import io.appwrite.Client;
import io.appwrite.coroutines.CoroutineCallback;
import io.appwrite.exceptions.AppwriteException;
import io.appwrite.models.User;
import io.appwrite.services.Account;
import io.appwrite.services.Databases;

public class newPostFragment extends Fragment {
    Button publishButton;
    EditText postContentEditText;
    NavController navController;
    Client client;
    Account account;
    // ...
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflar el layout para este fragmento
        return inflater.inflate(R.layout.fragment_new_post, container, false);
    }
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle
            savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        navController = Navigation.findNavController(view);
        client = new Client(requireContext())
                .setProject(getString(R.string.APPWRITE_PROJECT_ID));
        publishButton = view.findViewById(R.id.publishButton);
        postContentEditText = view.findViewById(R.id.postContentEditText);
        publishButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                publicar();
            }
        });
    }
    private void publicar() {
        String postContent = postContentEditText.getText().toString();
        if(TextUtils.isEmpty(postContent)){
            postContentEditText.setError("Required");
            return;
        }
        publishButton.setEnabled(false);
        // Obtenemos información de la cuenta del autor
        account = new Account(client);
        try {
            account.get(new CoroutineCallback<>((result, error) -> {
                if (error != null) {
                    error.printStackTrace();
                    return;
                }
                guardarEnAppWrite(result, postContent);
            }));
        } catch (AppwriteException e) {
            throw new RuntimeException(e);
        }
    }
    private void guardarEnAppWrite(User<Map<String, Object>> user, String content)
    {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        // Crear instancia del servicio Databases
        Databases databases = new Databases(client);

        // Datos del documento
        Map<String, Object> data = new HashMap<>();
        data.put("uid", user.getId().toString());
        data.put("author", user.getName().toString());
        data.put("authorPhotoUrl", null);
        data.put("content", content);
        // Crear el documento
        try {
            databases.createDocument(
                    getString(R.string.APPWRITE_DATABASE_ID),
                    getString(R.string.APPWRITE_POSTS_COLLECTION_ID),
                    "unique()", // Generar un ID único automáticamente
                    data,
                    new ArrayList<>(), // Permisos opcionales, como ["role:all"]
                    new CoroutineCallback<>((result, error) -> {
                        if (error != null) {
                            Snackbar.make(requireView(), "Error: " + error.toString(), Snackbar.LENGTH_LONG).show();
                        }
                        else
                        {
                            System.out.println("Post creado:" +
                                    result.toString());
                            mainHandler.post(() ->
                            {
                                navController.popBackStack();
                            });
                        }
                    })
            );
        } catch (AppwriteException e) {
            throw new RuntimeException(e);
        }
    }
}