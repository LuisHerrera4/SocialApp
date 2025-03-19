package com.example.socialapp;



import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Hashtags {
    public static List<String> extractHashtags(String text) {
        List<String> hashtags = new ArrayList<>();
        Pattern pattern = Pattern.compile("#(\\w+)");
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            hashtags.add("#" + matcher.group(1));
        }
        return hashtags;
    }
}
