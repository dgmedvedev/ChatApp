package com.demo.chatapp.screens.image;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;

import com.demo.chatapp.R;
import com.squareup.picasso.Picasso;

public class ImageActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image);
        ImageView imageViewMainImage = findViewById(R.id.imageViewImage);

        Intent intent = getIntent();
        String uri = intent.getStringExtra("uriImage");
        if (uri != null && !uri.isEmpty()) {
            Picasso.get().load(uri).into(imageViewMainImage);
        }
    }
}