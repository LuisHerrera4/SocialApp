package com.example.socialapp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import io.appwrite.Client;
import io.appwrite.coroutines.CoroutineCallback;
import io.appwrite.exceptions.AppwriteException;
import io.appwrite.models.InputFile;
import io.appwrite.services.Account;
import io.appwrite.services.Databases;
import io.appwrite.services.Storage;

public class ProfileFragment extends Fragment {
    private static final int PICK_IMAGE_REQUEST = 1;
    private static final String PREFS_NAME = "profile_prefs";
    private static final String KEY_PROFILE_URL = "profilePhotoURL";

    private ImageView photoImageView;
    private TextView displayNameTextView, emailTextView;
    private Client client;
    private Account account;
    private Databases databases;
    private Storage storage;
    private Uri imageUri;

    private AppViewModel appViewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        photoImageView = view.findViewById(R.id.photoImageView);
        displayNameTextView = view.findViewById(R.id.displayNameTextView);
        emailTextView = view.findViewById(R.id.emailTextView);

        client = new Client(requireContext()).setProject(getString(R.string.APPWRITE_PROJECT_ID));
        account = new Account(client);
        databases = new Databases(client);
        storage = new Storage(client);

        appViewModel = new ViewModelProvider(requireActivity()).get(AppViewModel.class);

        // Cargar la imagen de perfil almacenada (SharedPreferences o Appwrite)
        String storedUrl = getProfilePhotoUrl();
        if (storedUrl != null && !storedUrl.isEmpty()) {
            Glide.with(requireView()).load(storedUrl).circleCrop().into(photoImageView);
            appViewModel.profilePhotoUrl.setValue(storedUrl);
        } else {
            Glide.with(requireView()).load(R.drawable.user).into(photoImageView);
        }

        cargarDatosUsuario();

        // Al tocar la imagen se abre la galería para cambiarla
        photoImageView.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, PICK_IMAGE_REQUEST);
        });
    }

    private void cargarDatosUsuario() {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        try {
            account.get(new CoroutineCallback<>((result, error) -> {
                if (error != null) {
                    error.printStackTrace();
                    return;
                }
                mainHandler.post(() -> {
                    displayNameTextView.setText(result.getName());
                    emailTextView.setText(result.getEmail());
                });
                String userId = result.getId();
                try {
                    databases.getDocument(
                            getString(R.string.APPWRITE_DATABASE_ID),
                            getString(R.string.APPWRITE_USERS_COLLECTION_ID),
                            userId,
                            new CoroutineCallback<>((docResult, docError) -> {
                                if (docError != null) {
                                    docError.printStackTrace();
                                    return;
                                }
                                String imageUrl = "";
                                if (docResult.getData().get("profilePhotoURL") != null) {
                                    imageUrl = docResult.getData().get("profilePhotoURL").toString();
                                }
                                final String finalImageUrl = imageUrl;
                                mainHandler.post(() -> {
                                    if (!finalImageUrl.isEmpty()) {
                                        Glide.with(requireView())
                                                .load(finalImageUrl)
                                                .circleCrop()
                                                .into(photoImageView);
                                        saveProfilePhotoUrl(finalImageUrl);
                                        appViewModel.profilePhotoUrl.setValue(finalImageUrl);
                                    } else {
                                        Glide.with(requireView())
                                                .load(R.drawable.user)
                                                .into(photoImageView);
                                    }
                                });
                            })
                    );
                } catch (AppwriteException e) {
                    e.printStackTrace();
                }
            }));
        } catch (AppwriteException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            imageUri = data.getData();
            Glide.with(requireView()).load(imageUri).circleCrop().into(photoImageView);
            actualizarFotoPerfil();
        }
    }

    // Actualiza la foto de perfil: sube la imagen y actualiza el documento del usuario
    private void actualizarFotoPerfil() {
        try {
            File file = convertirUriAFile(imageUri);
            // Usamos InputFile.fromFile() con .Companion (porque es una clase Kotlin)
            InputFile inputFile = InputFile.Companion.fromFile(file);
            // Importante: usar el bucket de fotos de perfil (APPWRITE_STORAGE_PHOTO_PROFILE)
            storage.createFile(
                    getString(R.string.APPWRITE_STORAGE_PHOTO_PROFILE),
                    "unique()",
                    inputFile,
                    new CoroutineCallback<>((result, error) -> {
                        if (error != null) {
                            error.printStackTrace();
                            return;
                        }
                        // Construir la URL manualmente con el bucket de foto perfil
                        String imageUrl = "https://cloud.appwrite.io/v1/storage/buckets/"
                                + getString(R.string.APPWRITE_STORAGE_PHOTO_PROFILE)
                                + "/files/" + result.getId() + "/view?project="
                                + getString(R.string.APPWRITE_PROJECT_ID);
                        actualizarDocumentoUsuario(imageUrl);
                    })
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void actualizarDocumentoUsuario(String imageUrl) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        try {
            account.get(new CoroutineCallback<>((userResult, userError) -> {
                if (userError != null) {
                    userError.printStackTrace();
                    return;
                }
                String userId = userResult.getId();
                Map<String, Object> data = new HashMap<>();
                data.put("profilePhotoURL", imageUrl);
                try {
                    databases.updateDocument(
                            getString(R.string.APPWRITE_DATABASE_ID),
                            getString(R.string.APPWRITE_USERS_COLLECTION_ID),
                            userId,
                            data,
                            new CoroutineCallback<>((updateResult, updateError) -> {
                                if (updateError != null) {
                                    updateError.printStackTrace();
                                    return;
                                }
                                mainHandler.post(() -> {
                                    Snackbar.make(requireView(), "Foto de perfil actualizada", Snackbar.LENGTH_SHORT).show();
                                    saveProfilePhotoUrl(imageUrl);
                                    appViewModel.profilePhotoUrl.setValue(imageUrl);
                                });
                            })
                    );
                } catch (AppwriteException e) {
                    e.printStackTrace();
                }
            }));
        } catch (AppwriteException e) {
            e.printStackTrace();
        }
    }

    // Convierte la Uri en un File real para subir a Appwrite
    private File convertirUriAFile(Uri uri) throws IOException {
        File file = new File(requireContext().getCacheDir(), "profile_image.jpg");
        InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
        FileOutputStream outputStream = new FileOutputStream(file);
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) > 0) {
            outputStream.write(buffer, 0, length);
        }
        outputStream.close();
        inputStream.close();
        return file;
    }

    // Guarda la URL en SharedPreferences
    private void saveProfilePhotoUrl(String imageUrl) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_PROFILE_URL, imageUrl).apply();
    }

    // Obtiene la URL desde SharedPreferences
    private String getProfilePhotoUrl() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_PROFILE_URL, "");
    }
}
