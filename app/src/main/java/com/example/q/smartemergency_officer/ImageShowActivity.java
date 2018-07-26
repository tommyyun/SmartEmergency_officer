package com.example.q.smartemergency_officer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.widget.ImageView;

import com.bumptech.glide.Glide;

public class ImageShowActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.image_show_layout);
        ImageView image =findViewById(R.id.image);
        Point point = new Point();
        getWindowManager().getDefaultDisplay().getSize(point);
        image.getLayoutParams().width = point.x;
        image.getLayoutParams().height = point.y;
        Glide.with(this).load(MainActivity.bitmaps.get(getIntent().getIntExtra("data", 0))).into(image);
    }
}
