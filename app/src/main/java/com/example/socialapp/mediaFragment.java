package com.example.socialapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;

public class mediaFragment extends Fragment
{
    VideoView videoView;
    ImageView imageView;
    public AppViewModel appViewModel;
    public mediaFragment() {
// Required empty public constructor
    }
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup
            container, Bundle savedInstanceState)
    {
// Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_media, container, false);
    }
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle
            savedInstanceState)
    {
        super.onViewCreated(view, savedInstanceState);
        appViewModel = new
                ViewModelProvider(requireActivity()).get(AppViewModel.class);
        imageView = view.findViewById(R.id.imageView);
        videoView = view.findViewById(R.id.videoView);
        appViewModel.postSeleccionado.observe(getViewLifecycleOwner(), post
                -> {
            String mediaType = post.get("mediaType").toString();
            String mediaUrl = post.get("mediaUrl").toString();
            if ("video".equals(mediaType) ||
                    "audio".equals(mediaType)) {
                MediaController mc = new MediaController(requireContext());
                mc.setAnchorView(videoView);
                videoView.setMediaController(mc);
                videoView.setVideoPath(post.get("mediaUrl").toString());
                videoView.start();
            } else if ("image".equals(mediaType)) {
                Glide.with(requireView()).load(mediaUrl).into(imageView);
            }
        });
    }
}