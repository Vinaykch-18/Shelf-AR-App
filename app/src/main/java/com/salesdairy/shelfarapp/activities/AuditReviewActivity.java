package com.salesdairy.shelfarapp.activities;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.salesdairy.shelfarapp.databinding.ActivityAuditReviewBinding;
import com.salesdairy.shelfarapp.data.AuditRepository;
import com.salesdairy.shelfarapp.data.ShelfRepository;
import com.salesdairy.shelfarapp.models.Shelf;
import com.salesdairy.shelfarapp.models.ShelfAuditStatus;
import com.salesdairy.shelfarapp.utils.Constants;
import com.salesdairy.shelfarapp.utils.ImageUtils;

import java.util.List;

public class AuditReviewActivity extends AppCompatActivity {

    private ActivityAuditReviewBinding binding;
    private ShelfRepository shelfRepository;
    private AuditRepository auditRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAuditReviewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        shelfRepository = new ShelfRepository(this);
        auditRepository = new AuditRepository(this);

        binding.btnBack.setOnClickListener(v -> finish());

        int shelfId = getIntent().getIntExtra(Constants.EXTRA_SHELF_ID, -1);
        Shelf shelf = shelfRepository.getShelfById(shelfId);
        if (shelf == null) {
            Toast.makeText(this, "Shelf not found.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        ShelfAuditStatus status = auditRepository.getLatestShelfAuditStatus(shelfId);
        List<String> auditImages = auditRepository.getLatestAuditImagesForShelf(shelfId);
        boolean audited = status != null && status.isAudited();

        binding.tvShelfName.setText(TextUtils.isEmpty(shelf.getShelfName()) ? ("Shelf " + shelf.getId()) : shelf.getShelfName());
        binding.tvAuditStatus.setText(audited
                ? (TextUtils.isEmpty(status.getAuditedAt()) ? "Audited" : ("Audited on " + status.getAuditedAt()))
                : "Onboarded image only");

        loadImage(binding.ivShelfImage, shelf.getImagePath(), 960, 720);
        if (audited && !auditImages.isEmpty() && !TextUtils.isEmpty(auditImages.get(0))) {
            binding.layoutAuditCard.setVisibility(View.VISIBLE);
            binding.tvAuditHint.setVisibility(View.GONE);
            loadImage(binding.ivAuditImage, auditImages.get(0), 960, 720);
        } else {
            binding.layoutAuditCard.setVisibility(View.GONE);
        }
    }

    private void loadImage(ImageView imageView, String path, int reqWidth, int reqHeight) {
        Bitmap bitmap = ImageUtils.decodeSampledBitmap(path, reqWidth, reqHeight);
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
        } else {
            imageView.setImageResource(android.R.drawable.ic_menu_gallery);
        }
    }
}
