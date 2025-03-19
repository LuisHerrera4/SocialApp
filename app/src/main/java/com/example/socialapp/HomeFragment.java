package com.example.socialapp;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;

import io.appwrite.Client;
import io.appwrite.Query;
import io.appwrite.coroutines.CoroutineCallback;
import io.appwrite.exceptions.AppwriteException;
import io.appwrite.models.DocumentList;
import io.appwrite.services.Account;
import io.appwrite.services.Databases;

public class HomeFragment extends Fragment {

    private NavController navController;
    private AppViewModel appViewModel;
    private PostsAdapter adapter;
    private String userId;
    private ImageView photoImageView;
    private TextView displayNameTextView, emailTextView;
    private Client client;
    private Account account;

    public HomeFragment() { }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        NavigationView navigationView = view.getRootView().findViewById(R.id.nav_view);
        View header = navigationView.getHeaderView(0);
        photoImageView = header.findViewById(R.id.imageView);
        displayNameTextView = header.findViewById(R.id.displayNameTextView);
        emailTextView = header.findViewById(R.id.emailTextView);

        navController = Navigation.findNavController(view);
        appViewModel = new ViewModelProvider(requireActivity()).get(AppViewModel.class);

        appViewModel.profilePhotoUrl.observe(getViewLifecycleOwner(), newUrl -> {
            if (newUrl != null && !newUrl.isEmpty()) {
                Glide.with(requireView()).load(newUrl).circleCrop().into(photoImageView);
            } else {
                Glide.with(requireView()).load(R.drawable.user).into(photoImageView);
            }
        });

        client = new Client(requireContext()).setProject(getString(R.string.APPWRITE_PROJECT_ID));
        account = new Account(client);
        Handler mainHandler = new Handler(Looper.getMainLooper());

        try {
            account.get(new CoroutineCallback<>((result, error) -> {
                if (error != null) {
                    error.printStackTrace();
                    return;
                }
                mainHandler.post(() -> {
                    userId = result.getId();
                    displayNameTextView.setText(result.getName());
                    emailTextView.setText(result.getEmail());

                    Databases databases = new Databases(client);
                    try {
                        databases.getDocument(
                                getString(R.string.APPWRITE_DATABASE_ID),
                                getString(R.string.APPWRITE_USERS_COLLECTION_ID),
                                userId,
                                new CoroutineCallback<>((userResult, userError) -> {
                                    if (userError != null) {
                                        userError.printStackTrace();
                                        return;
                                    }
                                    String imageUrl = "";
                                    if (userResult.getData().get("profilePhotoURL") != null) {
                                        imageUrl = userResult.getData().get("profilePhotoURL").toString();
                                    }
                                    if (!imageUrl.isEmpty()) {
                                        Glide.with(requireView()).load(imageUrl).circleCrop().into(photoImageView);
                                    } else {
                                        Glide.with(requireView()).load(R.drawable.user).into(photoImageView);
                                    }
                                })
                        );
                    } catch (AppwriteException e) {
                        e.printStackTrace();
                    }
                    obtenerPosts();
                });
            }));
        } catch (AppwriteException e) {
            throw new RuntimeException(e);
        }

        view.findViewById(R.id.gotoNewPostFragmentButton).setOnClickListener(v ->
                navController.navigate(R.id.newPostFragment));

        RecyclerView postsRecyclerView = view.findViewById(R.id.postsRecyclerView);
        adapter = new PostsAdapter(client, userId, appViewModel, new PostsAdapter.NavControllerProvider() {
            @Override
            public void navigate(int resId, Bundle bundle) {
                navController.navigate(resId, bundle);
            }
        });
        postsRecyclerView.setAdapter(adapter);
    }

    void obtenerPosts() {
        Databases databases = new Databases(client);
        Handler mainHandler = new Handler(Looper.getMainLooper());
        try {
            databases.listDocuments(
                    getString(R.string.APPWRITE_DATABASE_ID),
                    getString(R.string.APPWRITE_POSTS_COLLECTION_ID),
                    Arrays.asList(Query.Companion.orderDesc("time"), Query.Companion.limit(50)),
                    new CoroutineCallback<DocumentList>((result, error) -> {
                        if (error != null) {
                            Snackbar.make(requireView(), "Error al obtener los posts: " + error.toString(), Snackbar.LENGTH_LONG).show();
                            return;
                        }
                        mainHandler.post(() -> adapter.establecerLista(result));
                    })
            );
        } catch (AppwriteException e) {
            throw new RuntimeException(e);
        }
    }
}
