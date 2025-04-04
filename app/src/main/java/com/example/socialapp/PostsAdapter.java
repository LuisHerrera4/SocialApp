package com.example.socialapp;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.TextPaint;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import io.appwrite.Client;
import io.appwrite.coroutines.CoroutineCallback;
import io.appwrite.exceptions.AppwriteException;
import io.appwrite.models.Document;
import io.appwrite.services.Databases;

public class PostsAdapter extends RecyclerView.Adapter<PostsAdapter.PostViewHolder> {

    private final List<Map<String, Object>> lista = new ArrayList<>();
    private final Client client;
    private final String userId;
    private final AppViewModel appViewModel;
    private final NavControllerProvider navProvider;
    private final OnPostsUpdatedListener postsUpdatedListener;

    public interface NavControllerProvider {
        void navigate(int resId, Bundle bundle);
    }

    public interface OnPostsUpdatedListener {
        void actualizarPosts();
    }

    public PostsAdapter(Client client, String userId, AppViewModel appViewModel, NavControllerProvider navProvider, OnPostsUpdatedListener listener) {
        this.client = client;
        this.userId = userId;
        this.appViewModel = appViewModel;
        this.navProvider = navProvider;
        this.postsUpdatedListener = listener;
    }

    public void establecerLista(List<Document<Map<String, Object>>> documentList) {
        lista.clear();
        for (Document<Map<String, Object>> document : documentList) {
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
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.viewholder_post, parent, false);
        return new PostViewHolder(itemView);
    }

    public void onBindViewHolder(@NonNull final PostViewHolder holder, final int position) {
        final Map<String, Object> post = lista.get(position);
        final String postId = post.get("$id").toString();
        final String postAuthorId = post.get("uid").toString();
        final Context context = holder.itemView.getContext();

        holder.authorTextView.setText(post.get("author").toString());
        holder.contentTextView.setText(post.get("content").toString());

        SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        Calendar calendar = Calendar.getInstance();
        if (post.get("time") != null) {
            try {
                long time = Long.parseLong(post.get("time").toString());
                calendar.setTimeInMillis(time);
            } catch (NumberFormatException e) {
                calendar.setTimeInMillis(0);
            }
        }
        holder.timeTextView.setText(formatter.format(calendar.getTime()));

        if (post.get("mediaUrl") != null && !post.get("mediaUrl").toString().isEmpty()) {
            holder.mediaImageView.setVisibility(View.VISIBLE);
            Glide.with(context)
                    .load(post.get("mediaUrl").toString())
                    .centerCrop()
                    .into(holder.mediaImageView);
        } else {
            holder.mediaImageView.setVisibility(View.GONE);
        }

        // Likes
        List<String> likes = (List<String>) post.get("likes");
        if (likes == null) likes = new ArrayList<>();

        if (likes.contains(userId)) {
            holder.likeImageView.setImageResource(R.drawable.like_on);
        } else {
            holder.likeImageView.setImageResource(R.drawable.like_off);
        }
        holder.numLikesTextView.setText(String.valueOf(likes.size()));

        List<String> finalLikes = likes;
        holder.likeImageView.setOnClickListener(view -> {
            Databases databases = new Databases(client);
            Handler mainHandler = new Handler(Looper.getMainLooper());

            List<String> nuevosLikes = new ArrayList<>(finalLikes);
            if (nuevosLikes.contains(userId)) {
                nuevosLikes.remove(userId);
            } else {
                nuevosLikes.add(userId);
            }

            Map<String, Object> data = new HashMap<>();
            data.put("likes", nuevosLikes);

            try {
                databases.updateDocument(
                        context.getString(R.string.APPWRITE_DATABASE_ID),
                        context.getString(R.string.APPWRITE_POSTS_COLLECTION_ID),
                        postId,
                        data,
                        new ArrayList<>(),
                        new CoroutineCallback<Document>((result2, error2) -> {
                            if (error2 != null) {
                                error2.printStackTrace();
                                return;
                            }
                            mainHandler.post(() -> postsUpdatedListener.actualizarPosts());
                        })
                );
            } catch (AppwriteException e) {
                e.printStackTrace();
            }
        });

        // Manejo de Hashtags
        String mensaje = post.get("content") != null ? post.get("content").toString() : "";
        holder.contentTextView.setText(mensaje);

        List<String> hashtags = new ArrayList<>();
        StringBuilder mensajeSinHashtags = new StringBuilder();

        // Separar las palabras del mensaje
        String[] palabras = mensaje.split(" ");
        for (String palabra : palabras) {
            if (palabra.startsWith("#")) {
                hashtags.add(palabra);
            } else {
                mensajeSinHashtags.append(palabra).append(" ");
            }
        }

        String mensajeFinal = mensajeSinHashtags.toString().trim();
        holder.contentTextView.setText(mensajeFinal.isEmpty() ? mensaje : mensajeFinal);

        if (!hashtags.isEmpty()) {
            String hashtagsText = String.join(" ", hashtags);
            SpannableString spannable = new SpannableString(hashtagsText);

            for (final String tag : hashtags) {
                int start = hashtagsText.indexOf(tag);
                int end = start + tag.length();

                ClickableSpan clickableSpan = new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View widget) {
                        Bundle bundle = new Bundle();
                        bundle.putString("hashtag", tag);
                        navProvider.navigate(R.id.hashtagsFragment, bundle);
                    }

                    @Override
                    public void updateDrawState(@NonNull TextPaint ds) {
                        ds.setColor(Color.BLUE);
                        ds.setUnderlineText(false);
                    }
                };
                spannable.setSpan(clickableSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            holder.hashtagsTextView.setText(spannable);
            holder.hashtagsTextView.setMovementMethod(LinkMovementMethod.getInstance());
            holder.hashtagsTextView.setVisibility(View.VISIBLE);
        } else {
            holder.hashtagsTextView.setVisibility(View.GONE);
        }

        holder.compartirPostButton.setOnClickListener(v -> {
            // Obtén el contenido del post (puedes personalizarlo según lo que necesites compartir)
            String contenido = post.get("content").toString();
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, contenido);
            context.startActivity(Intent.createChooser(shareIntent, "Compartir post"));
        });


        // Eliminar Post
        if (postAuthorId.equals(userId)) {
            holder.deletePostButton.setVisibility(View.VISIBLE);
            holder.deletePostButton.setOnClickListener(v -> eliminarPost(postId, position, context));
        } else {
            holder.deletePostButton.setVisibility(View.GONE);
        }

        holder.retweetPostButton.setOnClickListener(v -> {
            // Si el post ya es repost (tiene el campo "retweet"), mostramos un mensaje o evitamos el repost
            if(post.containsKey("retweet")) {
                Toast.makeText(context, "Este post ya es un repost", Toast.LENGTH_SHORT).show();
            } else {
                realizarRetweet(post, context);
            }
        });


        // Obtén el valor de isRepost (si no existe, se considera false)
        boolean isRepost = false;
        if(post.containsKey("isRepost")) {
            Object flag = post.get("isRepost");
            if(flag instanceof Boolean) {
                isRepost = (Boolean) flag;
            }
        }

// Si es repost, actualiza el texto del autor (o muestra un ícono, según prefieras)
        if(isRepost) {
            // Si en el documento ya se guardó "author" como "Retweet de ...", quizá aquí solo necesites destacar
            holder.authorTextView.setText(post.get("author").toString());
            // Opcional: podrías cambiar el color o agregar un prefijo
             holder.authorTextView.setText("Retweet de " + post.get("author").toString());
        } else {
            holder.authorTextView.setText(post.get("author").toString());
        }

    }


    @Override
    public int getItemCount() {
        return lista.size();
    }

    public static class PostViewHolder extends RecyclerView.ViewHolder {
        ImageView likeImageView, mediaImageView, deletePostButton, compartirPostButton, retweetPostButton;
        TextView authorTextView, contentTextView, timeTextView, numLikesTextView, hashtagsTextView;
        public PostViewHolder(@NonNull View itemView) {
            super(itemView);
            likeImageView = itemView.findViewById(R.id.likeImageView);
            numLikesTextView = itemView.findViewById(R.id.numLikesTextView);
            mediaImageView = itemView.findViewById(R.id.mediaImage);
            authorTextView = itemView.findViewById(R.id.authorTextView);
            contentTextView = itemView.findViewById(R.id.contentTextView);
            timeTextView = itemView.findViewById(R.id.timeTextView);
            deletePostButton = itemView.findViewById(R.id.deletePostButton);
            hashtagsTextView = itemView.findViewById(R.id.hashtagsTextView);
            compartirPostButton = itemView.findViewById(R.id.compartirPostButton);
            retweetPostButton = itemView.findViewById(R.id.retweetPostButton);


        }
    }

    private void realizarRetweet(Map<String, Object> post, Context context) {
        // Si el post tiene el campo "retweet", se toma ese valor como ID original; si no, se toma el id actual.
        String postIdOriginal = post.containsKey("retweet") ? post.get("retweet").toString() : post.get("$id").toString();
        String postAuthorOriginal = post.get("author").toString();
        String contenidoOriginal = post.get("content").toString();

        Databases databases = new Databases(client);
        Handler mainHandler = new Handler(Looper.getMainLooper());

        // Crear un nuevo post como retweet
        Map<String, Object> nuevoPost = new HashMap<>();
        nuevoPost.put("uid", userId);
        // Es recomendable que el campo "author" ya incluya la indicación de retweet,
        // o bien puedes hacerlo aquí:
        nuevoPost.put("author", "Retweet de " + postAuthorOriginal);
        nuevoPost.put("content", contenidoOriginal);
        nuevoPost.put("retweet", postIdOriginal);
        nuevoPost.put("time", System.currentTimeMillis());
        nuevoPost.put("isRepost", true);

        try {
            databases.createDocument(
                    context.getString(R.string.APPWRITE_DATABASE_ID),
                    context.getString(R.string.APPWRITE_POSTS_COLLECTION_ID),
                    null,
                    nuevoPost,
                    new CoroutineCallback<Document>((result, error) -> {
                        if (error != null) {
                            error.printStackTrace();
                            return;
                        }
                        mainHandler.post(() -> {
                            Toast.makeText(context, "Retweet realizado", Toast.LENGTH_SHORT).show();
                            postsUpdatedListener.actualizarPosts();
                        });
                    })
            );
        } catch (AppwriteException e) {
            e.printStackTrace();
        }
    }


    void eliminarPost(String postId, int position, Context context) {
        new AlertDialog.Builder(context)
                .setTitle("Eliminar Post")
                .setMessage("¿Estás seguro?")
                .setPositiveButton("Sí", (dialog, which) -> {
                    Databases databases = new Databases(client);
                    Handler mainHandler = new Handler(Looper.getMainLooper());

                    databases.deleteDocument(
                            context.getString(R.string.APPWRITE_DATABASE_ID),
                            context.getString(R.string.APPWRITE_POSTS_COLLECTION_ID),
                            postId,
                            new CoroutineCallback<>((result, error) -> {
                                if (error != null) {
                                    error.printStackTrace();
                                    return;
                                }
                                mainHandler.post(() -> {
                                    lista.remove(position);
                                    notifyItemRemoved(position);
                                    notifyItemRangeChanged(position, lista.size());
                                    Toast.makeText(context, "Post eliminado", Toast.LENGTH_SHORT).show();
                                    postsUpdatedListener.actualizarPosts();
                                });
                            })
                    );
                })
                .setNegativeButton("No", null)
                .show();
    }


}
