package com.example.socialapp;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.google.android.material.snackbar.Snackbar;

import java.io.ByteArrayOutputStream;
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

public class RegisterFragment extends Fragment {
    private static final int PICK_IMAGE_REQUEST = 1;

    private NavController navController;
    private Button registerButton, selectImageButton;
    private EditText usernameEditText, emailEditText, passwordEditText;
    private ImageView profileImageView;
    private Uri imageUri = null;
    private Client client;

    public RegisterFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_register, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        navController = Navigation.findNavController(view);

        usernameEditText = view.findViewById(R.id.usernameEditText);
        emailEditText = view.findViewById(R.id.emailEditText);
        passwordEditText = view.findViewById(R.id.passwordEditText);
        registerButton = view.findViewById(R.id.registerButton);
        selectImageButton = view.findViewById(R.id.selectImageButton);
        profileImageView = view.findViewById(R.id.profileImageView);

        client = new Client(requireActivity().getApplicationContext());
        client.setProject(getString(R.string.APPWRITE_PROJECT_ID));

        registerButton.setOnClickListener(view1 -> crearCuenta());
        selectImageButton.setOnClickListener(view1 -> seleccionarImagen());
    }

    // Método para seleccionar una imagen de la galería
    private void seleccionarImagen() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            imageUri = data.getData();
            profileImageView.setImageURI(imageUri);
        }
    }

    private void crearCuenta() {
        if (!validarFormulario()) {
            return;
        }

        registerButton.setEnabled(false);
        Account account = new Account(client);
        Handler mainHandler = new Handler(Looper.getMainLooper());

        try {
            account.create(
                    "unique()", // userId
                    emailEditText.getText().toString(),
                    passwordEditText.getText().toString(),
                    usernameEditText.getText().toString(),
                    new CoroutineCallback<>((result, error) -> {
                        mainHandler.post(() -> registerButton.setEnabled(true));

                        if (error != null) {
                            Snackbar.make(requireView(), "Error: " + error.toString(), Snackbar.LENGTH_LONG).show();
                            return;
                        }

                        // Creamos la sesión con el nuevo usuario
                        account.createEmailPasswordSession(
                                emailEditText.getText().toString(),
                                passwordEditText.getText().toString(),
                                new CoroutineCallback<>((result2, error2) -> {
                                    if (error2 != null) {
                                        Snackbar.make(requireView(), "Error: " + error2.toString(), Snackbar.LENGTH_LONG).show();
                                    } else {
                                        System.out.println("Sesión creada para el usuario:" + result2.toString());
                                        if (imageUri != null) {
                                            subirImagenYGuardarUsuario(result.getId());
                                        } else {
                                            guardarUsuarioSinImagen(result.getId());
                                        }
                                    }
                                })
                        );
                    })
            );
        } catch (AppwriteException e) {
            throw new RuntimeException(e);
        }
    }

    private void subirImagenYGuardarUsuario(String userId) {
        Storage storage = new Storage(client);

        try {
            // Convertimos `imageUri` en un archivo real
            File file = convertirUriAFile(imageUri);

            // ✅ Usamos `fromPath()`, que SÍ funciona en Appwrite SDK 6.1.0
            InputFile inputFile = InputFile.Companion.fromFile(file);

            storage.createFile(
                    getString(R.string.APPWRITE_STORAGE_BUCKET_ID),
                    "unique()",
                    inputFile,
                    new CoroutineCallback<>((result, error) -> {
                        if (error != null) {
                            error.printStackTrace();
                            System.out.println("Error al subir imagen: " + error.getMessage());
                            return;
                        }
                        System.out.println("Imagen subida con éxito. ID: " + result.getId());

                        // ✅ Generamos manualmente la URL correcta de Appwrite
                        String imageUrl = obtenerUrlArchivo(result.getId());

                        try {
                            guardarUsuarioConImagen(userId, imageUrl);
                        } catch (AppwriteException e) {
                            throw new RuntimeException(e);
                        }
                    })
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


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


    private String obtenerUrlArchivo(String fileId) {
        return "https://cloud.appwrite.io/v1/storage/buckets/"
                + getString(R.string.APPWRITE_STORAGE_BUCKET_ID)
                + "/files/" + fileId + "/view?project="
                + getString(R.string.APPWRITE_PROJECT_ID);
    }



    private byte[] convertirInputStreamABytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int bytesRead;
        byte[] data = new byte[1024];
        while ((bytesRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, bytesRead);
        }
        buffer.flush();
        return buffer.toByteArray();
    }




    private void guardarUsuarioConImagen(String userId, String imageUrl) throws AppwriteException {
        Databases databases = new Databases(client);
        Map<String, Object> data = new HashMap<>();
        data.put("profilePhotoURL", imageUrl); // Asegúrate de que coincide con el nombre en Appwrite

        databases.updateDocument(
                getString(R.string.APPWRITE_DATABASE_ID),
                getString(R.string.APPWRITE_USERS_COLLECTION_ID), // Asegúrate de usar la colección de usuarios
                userId,
                data,
                new CoroutineCallback<>((result, error) -> {
                    if (error != null) {
                        error.printStackTrace();
                        return;
                    }
                    Snackbar.make(requireView(), "Perfil creado con imagen", Snackbar.LENGTH_SHORT).show();
                    actualizarUI("Ok");
                })
        );
    }


    private void guardarUsuarioSinImagen(String userId) {
        actualizarUI("Ok");
    }

    private void actualizarUI(String currentUser) {
        if (currentUser != null) {
            navController.navigate(R.id.homeFragment);
        }
    }

    private boolean validarFormulario() {
        boolean valid = true;

        if (TextUtils.isEmpty(emailEditText.getText().toString())) {
            emailEditText.setError("Required.");
            valid = false;
        } else {
            emailEditText.setError(null);
        }

        if (TextUtils.isEmpty(passwordEditText.getText().toString())) {
            passwordEditText.setError("Required.");
            valid = false;
        } else {
            passwordEditText.setError(null);
        }

        return valid;
    }
}
