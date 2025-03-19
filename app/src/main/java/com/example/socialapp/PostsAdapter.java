package com.example.socialapp;

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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
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
import io.appwrite.Query;
import io.appwrite.coroutines.CoroutineCallback;
import io.appwrite.exceptions.AppwriteException;
import io.appwrite.models.Document;
import io.appwrite.models.DocumentList;
import io.appwrite.services.Databases;

public class PostsAdapter extends RecyclerView.Adapter<PostsAdapter.PostViewHolder> {

    // Lista de posts: cada post es un Map con los datos del documento
    public List<Map<String, Object>> lista = new ArrayList<>();
    private Client client;
    private String userId;
    private AppViewModel appViewModel;
    private NavControllerProvider navProvider;

    // Interfaz para navegación (por ejemplo, al pulsar sobre un hashtag)
    public interface NavControllerProvider {
        void navigate(int resId, Bundle bundle);
    }

    // Constructor: se inyectan Client, userId, AppViewModel y un NavControllerProvider
    public PostsAdapter(Client client, String userId, AppViewModel appViewModel, NavControllerProvider navProvider) {
        this.client = client;
        this.userId = userId;
        this.appViewModel = appViewModel;
        this.navProvider = navProvider;
    }

    // Método para actualizar la lista con el DocumentList obtenido de Appwrite
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

        // Cargar imagen del autor
        if (postAuthorId.equals(userId)) {
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
            // Para otros usuarios, se consulta la base de datos para obtener su foto de perfil
            Databases databases = new Databases(client);
            try {
                databases.getDocument(
                        holder.itemView.getContext().getString(R.string.APPWRITE_DATABASE_ID),
                        holder.itemView.getContext().getString(R.string.APPWRITE_USERS_COLLECTION_ID),
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

        // Establecer datos del post
        holder.authorTextView.setText(post.get("author").toString());
        holder.contentTextView.setText(post.get("content").toString());
        SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        Calendar calendar = Calendar.getInstance();
        if (post.get("time") != null)
            calendar.setTimeInMillis((long) post.get("time"));
        else
            calendar.setTimeInMillis(0);
        holder.timeTextView.setText(formatter.format(calendar.getTime()));

        // Mostrar u ocultar el ImageView de media según si existe "mediaUrl"
        if (post.get("mediaUrl") != null && !post.get("mediaUrl").toString().isEmpty()) {
            holder.mediaImageView.setVisibility(View.VISIBLE);
            Glide.with(holder.itemView.getContext())
                    .load(post.get("mediaUrl").toString())
                    .centerCrop()
                    .into(holder.mediaImageView);
        } else {
            holder.mediaImageView.setVisibility(View.GONE);
        }

        // Procesar y mostrar hashtags en el TextView correspondiente
        List<String> hashtags = (List<String>) post.get("hashtags");
        if (hashtags != null && !hashtags.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String tag : hashtags) {
                sb.append(tag).append(" ");
            }
            String hashtagsText = sb.toString().trim();
            SpannableString spannable = new SpannableString(hashtagsText);
            Pattern pattern = Pattern.compile("#(\\w+)");
            Matcher matcher = pattern.matcher(hashtagsText);
            while (matcher.find()) {
                final String tag = matcher.group();
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
                spannable.setSpan(clickableSpan, matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            holder.hashtagsTextView.setText(spannable);
            holder.hashtagsTextView.setMovementMethod(LinkMovementMethod.getInstance());
            holder.hashtagsTextView.setVisibility(View.VISIBLE);
        } else {
            holder.hashtagsTextView.setVisibility(View.GONE);
        }

        // Gestión de likes (simplificado)
        List<String> likes = (List<String>) post.get("likes");
        if (likes.contains(userId))
            holder.likeImageView.setImageResource(R.drawable.like_on);
        else
            holder.likeImageView.setImageResource(R.drawable.like_off);
        holder.numLikesTextView.setText(String.valueOf(likes.size()));

        holder.likeImageView.setOnClickListener(v -> {
            Databases databases2 = new Databases(client);
            Handler handler = new Handler(Looper.getMainLooper());
            List<String> nuevosLikes = new ArrayList<>(likes);
            if (nuevosLikes.contains(userId))
                nuevosLikes.remove(userId);
            else
                nuevosLikes.add(userId);
            Map<String, Object> data = new HashMap<>();
            data.put("likes", nuevosLikes);
            try {
                databases2.updateDocument(
                        holder.itemView.getContext().getString(R.string.APPWRITE_DATABASE_ID),
                        holder.itemView.getContext().getString(R.string.APPWRITE_POSTS_COLLECTION_ID),
                        postId,
                        data,
                        new ArrayList<>(),
                        new CoroutineCallback<>((result2, error2) -> {
                            if (error2 != null) {
                                error2.printStackTrace();
                                return;
                            }
                            handler.post(() -> {
                                // Puedes actualizar el UI o recargar el item si lo deseas\n                                Toast.makeText(holder.itemView.getContext(), \"Likes actualizados\", Toast.LENGTH_SHORT).show();
                            });
                        })
                );
            } catch (AppwriteException e) {
                throw new RuntimeException(e);
            }
        });

        // Función de compartir el post mediante un Intent
        holder.compartirPostButton.setOnClickListener(v -> {
            Uri shareUri = Uri.parse("android.resource://" + v.getContext().getPackageName() + "/" + R.drawable.compartir);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/png");
            shareIntent.putExtra(Intent.EXTRA_STREAM, shareUri);
            shareIntent.putExtra(Intent.EXTRA_TEXT, "¡Mira este post!");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            v.getContext().startActivity(Intent.createChooser(shareIntent, "Compartir post"));
        });

        // Mostrar el botón de eliminar solo si el post pertenece al usuario actual
        if (postAuthorId.equals(userId)) {
            holder.deletePostButton.setVisibility(View.VISIBLE);
            holder.deletePostButton.setOnClickListener(v -> {
            });
        } else {
            holder.deletePostButton.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return lista.size();
    }

    // Clase interna PostViewHolder: contiene todas las vistas de cada item
    public static class PostViewHolder extends RecyclerView.ViewHolder {
        ImageView authorPhotoImageView, likeImageView, mediaImageView, deletePostButton, compartirPostButton;
        TextView authorTextView, contentTextView, numLikesTextView, timeTextView, hashtagsTextView;

        public PostViewHolder(@NonNull View itemView) {
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
            hashtagsTextView = itemView.findViewById(R.id.hashtagsTextView);
        }
    }
}
