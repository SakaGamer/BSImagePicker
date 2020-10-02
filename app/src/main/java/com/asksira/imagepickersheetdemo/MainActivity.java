package com.asksira.imagepickersheetdemo;

import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.asksira.bsimagepicker.ImagePicker;
import com.bumptech.glide.Glide;

import java.util.List;

public class MainActivity extends AppCompatActivity implements ImagePicker.ImageListener {

    private ImageView ivImage1, ivImage2, ivImage3, ivImage4, ivImage5, ivImage6;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ivImage1 = findViewById(R.id.iv_image1);
        ivImage2 = findViewById(R.id.iv_image2);
        ivImage3 = findViewById(R.id.iv_image3);
        ivImage4 = findViewById(R.id.iv_image4);
        ivImage5 = findViewById(R.id.iv_image5);
        ivImage6 = findViewById(R.id.iv_image6);
        findViewById(R.id.tv_single_selection).setOnClickListener(v -> {
            ImagePicker pickerDialog = new ImagePicker.Builder("com.asksira.imagepickersheetdemo.fileprovider")
                    .build();
            pickerDialog.show(getSupportFragmentManager(), "picker");
        });
        findViewById(R.id.tv_multi_selection).setOnClickListener(v -> {
            ImagePicker pickerDialog = new ImagePicker.Builder("com.asksira.imagepickersheetdemo.fileprovider")
                    .setMaximumDisplayingImages(Integer.MAX_VALUE)
                    .isMultiSelect()
                    .setMinimumMultiSelectCount(3)
                    .build();
            pickerDialog.show(getSupportFragmentManager(), "picker");
        });
    }

    @Override
    public void onSingleSelect(Uri uri) {
        Glide.with(MainActivity.this).load(uri).into(ivImage2);
    }

    @Override
    public void onMultipleSelect(List<? extends Uri> uriList) {
        for (int i = 0; i < uriList.size(); i++) {
            if (i >= 6) return;
            ImageView iv;
            switch (i) {
                case 0:
                    iv = ivImage1;
                    break;
                case 1:
                    iv = ivImage2;
                    break;
                case 2:
                    iv = ivImage3;
                    break;
                case 3:
                    iv = ivImage4;
                    break;
                case 4:
                    iv = ivImage5;
                    break;
                case 5:
                default:
                    iv = ivImage6;
            }
            Glide.with(this).load(uriList.get(i)).into(iv);
        }
    }

    @Override
    public void onCancelled(boolean isMultiSelecting) {
        Toast.makeText(this, "Selection is cancelled, Multi-selection is " + isMultiSelecting, Toast.LENGTH_SHORT).show();
    }
}
