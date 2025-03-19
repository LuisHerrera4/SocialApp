package com.example.socialapp;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.util.Arrays;

import io.appwrite.Client;
import io.appwrite.Query;
import io.appwrite.coroutines.CoroutineCallback;
import io.appwrite.exceptions.AppwriteException;
import io.appwrite.models.DocumentList;
import io.appwrite.services.Databases;

public class HashtagsFragment extends Fragment {
    private String hashtag;
    private Client client;
    private RecyclerView recyclerView;
    private PostsAdapter adapter;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private NavController navController;
    private AppViewModel appViewModel;
    private TextView selectedHashtagTextView;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_hashtags, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.postsRecyclerView);
        selectedHashtagTextView = view.findViewById(R.id.selectedHashtagTextView);
        navController = Navigation.findNavController(view);
        client = new Client(requireContext()).setProject(getString(R.string.APPWRITE_PROJECT_ID));
        appViewModel = new ViewModelProvider(requireActivity()).get(AppViewModel.class);

        // Configurar RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new PostsAdapter(client, "", appViewModel,
                (resId, bundle) -> navController.navigate(resId, bundle),
                () -> {} // Se pasa una función vacía para evitar errores
        );
        recyclerView.setAdapter(adapter);

        // Obtener el hashtag del argumento
        if (getArguments() != null) {
            hashtag = getArguments().getString("hashtag", "");
        }

        if (hashtag.isEmpty()) {
            Toast.makeText(getContext(), "No se recibió hashtag", Toast.LENGTH_SHORT).show();
            return;
        }

        selectedHashtagTextView.setText(hashtag);

        obtenerPostsPorHashtag();
    }

    private void obtenerPostsPorHashtag() {
        Databases databases = new Databases(client);

        try {
            databases.listDocuments(
                    getString(R.string.APPWRITE_DATABASE_ID),
                    getString(R.string.APPWRITE_POSTS_COLLECTION_ID),
                    Arrays.asList(Query.Companion.equal("hashtags", hashtag)),
                    new CoroutineCallback<DocumentList>((result, error) -> {
                        if (error != null) {
                            mainHandler.post(() -> {
                                Snackbar.make(requireView(), "Error al obtener posts: " + error.getMessage(), Snackbar.LENGTH_LONG).show();
                            });
                            return;
                        }

                        if (result != null && requireActivity() != null) {
                            mainHandler.post(() -> adapter.establecerLista(result.getDocuments()));
                        }
                    })
            );
        } catch (AppwriteException e) {
            throw new RuntimeException(e);
        }
    }
}