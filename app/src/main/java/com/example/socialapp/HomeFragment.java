package com.example.socialapp;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.appwrite.Client;
import io.appwrite.Query;
import io.appwrite.coroutines.CoroutineCallback;
import io.appwrite.exceptions.AppwriteException;
import io.appwrite.models.Document;
import io.appwrite.models.DocumentList;
import io.appwrite.services.Account;
import io.appwrite.services.Databases;

public class HomeFragment extends Fragment {

    private NavController navController;
    AppViewModel appViewModel;
    PostsAdapter adapter;
    private String userId;
    private ImageView photoImageView;
    private TextView displayNameTextView, emailTextView;
    private Client client;
    private Account account;

    public HomeFragment() { }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Header del NavigationView
        NavigationView navigationView = view.getRootView().findViewById(R.id.nav_view);
        View header = navigationView.getHeaderView(0);
        photoImageView = header.findViewById(R.id.imageView);
        displayNameTextView = header.findViewById(R.id.displayNameTextView);
        emailTextView = header.findViewById(R.id.emailTextView);

        navController = Navigation.findNavController(view);
        appViewModel = new ViewModelProvider(requireActivity()).get(AppViewModel.class);

        // Observar cambios en el LiveData para actualizar el header
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

                    // Cargar la foto de perfil del usuario actual desde la colección de usuarios
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
        adapter = new PostsAdapter();
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
                    new CoroutineCallback<>((result, error) -> {
                        if (error != null) {
                            Snackbar.make(requireView(), "Error al obtener los posts: " + error.toString(), Snackbar.LENGTH_LONG).show();
                            return;
                        }
                        System.out.println(result.toString());
                        mainHandler.post(() -> adapter.establecerLista(result));
                    })
            );
        } catch (AppwriteException e) {
            throw new RuntimeException(e);
        }
    }

    // ViewHolder para cada post
    class PostViewHolder extends RecyclerView.ViewHolder {
        ImageView authorPhotoImageView, likeImageView, mediaImageView, deletePostButton, compartirPostButton;
        TextView authorTextView, contentTextView, numLikesTextView, timeTextView;

        PostViewHolder(@NonNull View itemView) {
            super(itemView);
            authorPhotoImageView = itemView.findViewById(R.id.authorPhotoImageView);
            likeImageView = itemView.findViewById(R.id.likeImageView);
            mediaImageView = itemView.findViewById(R.id.mediaImage);
            authorTextView = itemView.findViewById(R.id.authorTextView);
            contentTextView = itemView.findViewById(R.id.contentTextView);
            numLikesTextView = itemView.findViewById(R.id.numLikesTextView);
            timeTextView = itemView.findViewById(R.id.timeTextView);
            deletePostButton = itemView.findViewById(R.id.deletePostButton);
            compartirPostButton = itemView.findViewById(R.id.compartirPostButton);

        }
    }

    // Adaptador para el RecyclerView
    class PostsAdapter extends RecyclerView.Adapter<HomeFragment.PostViewHolder> {
        private List<Map<String, Object>> lista = new ArrayList<>();

        public void establecerLista(DocumentList<Map<String, Object>> documentList) {
            lista.clear();
            for (Document<Map<String, Object>> document : documentList.getDocuments()) {
                Map<String, Object> data = document.getData();
                if (data != null) {
                    data.put("$id", document.getId());
                    lista.add(data);
                }
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public HomeFragment.PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View itemView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.viewholder_post, parent, false);
            return new HomeFragment.PostViewHolder(itemView);
        }

        @Override
        public void onBindViewHolder(@NonNull HomeFragment.PostViewHolder holder, int position) {
            Map<String, Object> post = lista.get(position);
            String postId = post.get("$id").toString();
            String postAuthorId = post.get("uid").toString();

            // Si el post es del usuario actual, usar el valor del ViewModel
            if (postAuthorId.equals(HomeFragment.this.userId)) {
                String profileUrl = appViewModel.profilePhotoUrl.getValue();
                if (profileUrl != null && !profileUrl.isEmpty()) {
                    Glide.with(holder.itemView.getContext())
                            .load(profileUrl)
                            .circleCrop()
                            .into(holder.authorPhotoImageView);
                } else {
                    holder.authorPhotoImageView.setImageResource(R.drawable.user);
                }
            } else {
                // Para otros usuarios, consultar la colección de usuarios
                Databases databases = new Databases(HomeFragment.this.client);
                try {
                    databases.getDocument(
                            HomeFragment.this.getString(R.string.APPWRITE_DATABASE_ID),
                            HomeFragment.this.getString(R.string.APPWRITE_USERS_COLLECTION_ID),
                            postAuthorId,
                            new CoroutineCallback<>((result, error) -> {
                                if (error != null) {
                                    error.printStackTrace();
                                    return;
                                }
                                String imageUrl = result.getData().get("profilePhotoURL") != null ?
                                        result.getData().get("profilePhotoURL").toString() : "";
                                holder.authorPhotoImageView.post(() -> {
                                    if (!imageUrl.isEmpty()) {
                                        Glide.with(holder.itemView.getContext())
                                                .load(imageUrl)
                                                .circleCrop()
                                                .into(holder.authorPhotoImageView);
                                    } else {
                                        holder.authorPhotoImageView.setImageResource(R.drawable.user);
                                    }
                                });
                            })
                    );
                } catch (AppwriteException e) {
                    throw new RuntimeException(e);
                }
            }

            holder.authorTextView.setText(post.get("author").toString());
            holder.contentTextView.setText(post.get("content").toString());

            SimpleDateFormat formatear = new SimpleDateFormat("dd/MM/yyyy HH:mm");
            Calendar calendar = Calendar.getInstance();
            if (post.get("time") != null)
                calendar.setTimeInMillis((long) post.get("time"));
            else
                calendar.setTimeInMillis(0);
            holder.timeTextView.setText(formatear.format(calendar.getTime()));

            // Gestión de likes
            List<String> likes = (List<String>) post.get("likes");
            if (likes.contains(HomeFragment.this.userId))
                holder.likeImageView.setImageResource(R.drawable.like_on);
            else
                holder.likeImageView.setImageResource(R.drawable.like_off);
            holder.numLikesTextView.setText(String.valueOf(likes.size()));

            holder.likeImageView.setOnClickListener(view -> {
                Databases databases2 = new Databases(HomeFragment.this.client);
                Handler mainHandler = new Handler(Looper.getMainLooper());
                List<String> nuevosLikes = new ArrayList<>(likes);
                if (nuevosLikes.contains(HomeFragment.this.userId))
                    nuevosLikes.remove(HomeFragment.this.userId);
                else
                    nuevosLikes.add(HomeFragment.this.userId);
                Map<String, Object> data = new HashMap<>();
                data.put("likes", nuevosLikes);
                try {
                    databases2.updateDocument(
                            HomeFragment.this.getString(R.string.APPWRITE_DATABASE_ID),
                            HomeFragment.this.getString(R.string.APPWRITE_POSTS_COLLECTION_ID),
                            postId,
                            data,
                            new ArrayList<>(),
                            new CoroutineCallback<>((result2, error2) -> {
                                if (error2 != null) {
                                    error2.printStackTrace();
                                    return;
                                }
                                Handler handler = new Handler(Looper.getMainLooper());
                                handler.post(() -> HomeFragment.this.obtenerPosts());
                            })
                    );
                } catch (AppwriteException e) {
                    throw new RuntimeException(e);
                }
            });

            // Agregar funcionalidad para compartir el post
            holder.compartirPostButton.setOnClickListener(v -> {
                // Construir la URI del recurso compartir.png
                Uri shareUri = Uri.parse("android.resource://" + v.getContext().getPackageName() + "/" + R.drawable.compartir);
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("image/png");
                shareIntent.putExtra(Intent.EXTRA_STREAM, shareUri);
                shareIntent.putExtra(Intent.EXTRA_TEXT, "¡Mira este post!");
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                v.getContext().startActivity(Intent.createChooser(shareIntent, "Compartir post"));
            });

            if (post.get("mediaUrl") != null) {
                holder.mediaImageView.setVisibility(View.VISIBLE);
                if ("audio".equals(post.get("mediaType").toString())) {
                    Glide.with(holder.itemView.getContext())
                            .load(R.drawable.audio)
                            .centerCrop()
                            .into(holder.mediaImageView);
                } else {
                    Glide.with(holder.itemView.getContext())
                            .load(post.get("mediaUrl").toString())
                            .centerCrop()
                            .into(holder.mediaImageView);
                }
                holder.mediaImageView.setOnClickListener(view -> {
                    appViewModel.postSeleccionado.setValue(post);
                    navController.navigate(R.id.mediaFragment);
                });
            } else {
                holder.mediaImageView.setVisibility(View.GONE);
            }

            if (postAuthorId.equals(HomeFragment.this.userId)) {
                holder.deletePostButton.setVisibility(View.VISIBLE);
                holder.deletePostButton.setImageResource(R.drawable.basura);
                holder.deletePostButton.setOnClickListener(view -> eliminarPost(postId, position));
            } else {
                holder.deletePostButton.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return lista.size();
        }
    }

    void eliminarPost(String postId, int position) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Eliminar Post")
                .setMessage("¿Estás seguro de que deseas eliminar este post?")
                .setPositiveButton("Sí", (dialog, which) -> {
                    Databases databases = new Databases(client);
                    Handler mainHandler = new Handler(Looper.getMainLooper());
                    databases.deleteDocument(
                            getString(R.string.APPWRITE_DATABASE_ID),
                            getString(R.string.APPWRITE_POSTS_COLLECTION_ID),
                            postId,
                            new CoroutineCallback<>((result, error) -> {
                                if (error != null) {
                                    error.printStackTrace();
                                    return;
                                }
                                mainHandler.post(() -> {
                                    adapter.lista.remove(position);
                                    adapter.notifyItemRemoved(position);
                                    adapter.notifyItemRangeChanged(position, adapter.lista.size());
                                    Snackbar.make(requireView(), "Post eliminado", Snackbar.LENGTH_SHORT).show();
                                });
                            })
                    );
                })
                .setNegativeButton("No", null)
                .show();
    }
}
