package com.example.socialapp;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
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

    public HomeFragment() {
        // Constructor vacío requerido
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Puedes inicializar argumentos si es necesario
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflar el layout para este fragmento
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Obtener referencias de los elementos del header del NavigationView
        NavigationView navigationView = view.getRootView().findViewById(R.id.nav_view);
        View header = navigationView.getHeaderView(0);
        photoImageView = header.findViewById(R.id.imageView);
        displayNameTextView = header.findViewById(R.id.displayNameTextView);
        emailTextView = header.findViewById(R.id.emailTextView);

        navController = Navigation.findNavController(view);
        appViewModel = new ViewModelProvider(requireActivity()).get(AppViewModel.class);

        // Inicializar el cliente de Appwrite
        client = new Client(requireContext()).setProject(getString(R.string.APPWRITE_PROJECT_ID));
        account = new Account(client);
        Handler mainHandler = new Handler(Looper.getMainLooper());

        // Obtener información del usuario
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

                    // Obtener la imagen de perfil desde la base de datos de usuarios
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
                                    String imageUrl = userResult.getData().get("profilePhotoURL") != null ?
                                            userResult.getData().get("profilePhotoURL").toString() : "";

                                    if (!imageUrl.isEmpty()) {
                                        Glide.with(requireView()).load(imageUrl).circleCrop().into(photoImageView);
                                    } else {
                                        Glide.with(requireView()).load(R.drawable.user).into(photoImageView);
                                    }
                                })
                        );
                    } catch (AppwriteException e) {
                        throw new RuntimeException(e);
                    }
                    obtenerPosts();
                });
            }));
        } catch (AppwriteException e) {
            throw new RuntimeException(e);
        }

        // Navegar a la creación de un nuevo post
        view.findViewById(R.id.gotoNewPostFragmentButton).setOnClickListener(v ->
                navController.navigate(R.id.newPostFragment));

        // Configurar el RecyclerView
        RecyclerView postsRecyclerView = view.findViewById(R.id.postsRecyclerView);
        adapter = new PostsAdapter();
        postsRecyclerView.setAdapter(adapter);
    }

    // ViewHolder para cada post
    class PostViewHolder extends RecyclerView.ViewHolder {
        ImageView authorPhotoImageView, likeImageView, mediaImageView, deletePostButton;
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
        }
    }

    // Adaptador para el RecyclerView
    class PostsAdapter extends RecyclerView.Adapter<PostViewHolder> {
        // Convertimos la lista de documentos en una lista modificable de Map<String, Object>
        private List<Map<String, Object>> lista = new ArrayList<>();

        // Método para establecer la lista desde el DocumentList obtenido de Appwrite
        public void establecerLista(DocumentList<Map<String, Object>> documentList) {
            lista.clear();
            for (Document<Map<String, Object>> document : documentList.getDocuments()) {
                Map<String, Object> data = document.getData();
                if (data != null) {
                    data.put("$id", document.getId()); // Añadir ID al mapa de datos
                    lista.add(data);
                }
            }
            notifyDataSetChanged();
        }


        @NonNull
        @Override
        public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View itemView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.viewholder_post, parent, false);
            return new PostViewHolder(itemView);
        }

        @Override
        public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
            Map<String, Object> post = lista.get(position);
            String postId = post.get("$id").toString();
            String postAuthorId = post.get("uid").toString();

            // Obtener la foto de perfil desde la colección de usuarios
            Databases databases = new Databases(client);
            try {
                databases.getDocument(
                        getString(R.string.APPWRITE_DATABASE_ID),
                        getString(R.string.APPWRITE_USERS_COLLECTION_ID), // Colección de usuarios
                        postAuthorId,
                        new CoroutineCallback<>((result, error) -> {
                            if (error != null) {
                                error.printStackTrace();
                                return;
                            }
                            String imageUrl = result.getData().get("profilePhotoURL") != null ? result.getData().get("profilePhotoURL").toString() : "";
                            if (!imageUrl.isEmpty()) {
                                holder.authorPhotoImageView.post(() ->
                                        Glide.with(holder.itemView.getContext()).load(imageUrl).circleCrop().into(holder.authorPhotoImageView)
                                );
                            } else {
                                holder.authorPhotoImageView.post(() ->
                                        holder.authorPhotoImageView.setImageResource(R.drawable.user)
                                );
                            }

                        })
                );
            } catch (AppwriteException e) {
                throw new RuntimeException(e);
            }

            holder.authorTextView.setText(post.get("author").toString());
            holder.contentTextView.setText(post.get("content").toString());

            // Formateo de fecha y hora
            SimpleDateFormat formatear = new SimpleDateFormat("dd/MM/yyyy HH:mm");
            Calendar calendar = Calendar.getInstance();
            if (post.get("time") != null)
                calendar.setTimeInMillis((long) post.get("time"));
            else
                calendar.setTimeInMillis(0);
            holder.timeTextView.setText(formatear.format(calendar.getTime()));

            // Gestión de likes
            List<String> likes = (List<String>) post.get("likes");
            if (likes.contains(userId))
                holder.likeImageView.setImageResource(R.drawable.like_on);
            else
                holder.likeImageView.setImageResource(R.drawable.like_off);
            holder.numLikesTextView.setText(String.valueOf(likes.size()));

            holder.likeImageView.setOnClickListener(view -> {
                Databases databases2 = new Databases(client);
                Handler mainHandler = new Handler(Looper.getMainLooper());
                List<String> nuevosLikes = new ArrayList<>(likes);
                if (nuevosLikes.contains(userId))
                    nuevosLikes.remove(userId);
                else
                    nuevosLikes.add(userId);
                Map<String, Object> data = new HashMap<>();
                data.put("likes", nuevosLikes);
                try {
                    databases2.updateDocument(
                            getString(R.string.APPWRITE_DATABASE_ID),
                            getString(R.string.APPWRITE_POSTS_COLLECTION_ID),
                            postId,
                            data,
                            new ArrayList<>(),
                            new CoroutineCallback<>((result2, error2) -> {
                                if (error2 != null) {
                                    error2.printStackTrace();
                                    return;
                                }
                                System.out.println("Likes actualizados: " + result2.toString());
                                mainHandler.post(() -> obtenerPosts());
                            })
                    );
                } catch (AppwriteException e) {
                    throw new RuntimeException(e);
                }
            });

            // Miniatura de media
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

            // Botón de eliminar: visible solo si el usuario actual es el autor
            if (postAuthorId.equals(userId)) {
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

    // Método para obtener los posts desde Appwrite
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
                                    // Elimina el post de la lista del adaptador y notifica el cambio
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
