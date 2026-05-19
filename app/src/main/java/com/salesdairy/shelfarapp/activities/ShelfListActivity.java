package com.salesdairy.shelfarapp.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.salesdairy.shelfarapp.R;
import com.salesdairy.shelfarapp.data.AuditRepository;
import com.salesdairy.shelfarapp.data.ShelfRepository;
import com.salesdairy.shelfarapp.data.StoreReferenceRepository;
import com.salesdairy.shelfarapp.databinding.ActivityShelfListBinding;
import com.salesdairy.shelfarapp.databinding.ItemShelfRowBinding;
import com.salesdairy.shelfarapp.models.Shelf;
import com.salesdairy.shelfarapp.models.ShelfAuditStatus;
import com.salesdairy.shelfarapp.models.StoreReference;
import com.salesdairy.shelfarapp.utils.Constants;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ShelfListActivity extends AppCompatActivity {

    private ActivityShelfListBinding binding;
    private ShelfRepository shelfRepository;
    private AuditRepository auditRepository;
    private StoreReferenceRepository storeReferenceRepository;
    private int storeReferenceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityShelfListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        shelfRepository = new ShelfRepository(this);
        auditRepository = new AuditRepository(this);
        storeReferenceRepository = new StoreReferenceRepository(this);

        storeReferenceId = getIntent().getIntExtra(Constants.EXTRA_STORE_REFERENCE_ID, -1);
        if (storeReferenceId <= 0) {
            StoreReference reference = storeReferenceRepository.getPreferredStoreReference();
            if (reference != null) {
                storeReferenceId = reference.getId();
            }
        }

        binding.btnBack.setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderShelves();
    }

    private void renderShelves() {
        binding.layoutShelfRows.removeAllViews();
        if (storeReferenceId <= 0) {
            binding.tvHeaderStatus.setText("Save the store start view first");
            binding.tvHeaderHint.setText("Onboard the store once, then shelves appear here for viewing.");
            binding.tvEmpty.setVisibility(View.VISIBLE);
            return;
        }

        List<Shelf> shelves = shelfRepository.getShelvesForStoreReference(storeReferenceId);
        Map<Integer, ShelfAuditStatus> latestStatusMap = auditRepository.getLatestShelfAuditStatusMap();
        int auditedCount = 0;
        for (Shelf shelf : shelves) {
            ShelfAuditStatus status = latestStatusMap.get(shelf.getId());
            if (status != null && status.isAudited()) {
                auditedCount++;
            }
        }
        int pendingCount = Math.max(0, shelves.size() - auditedCount);
        binding.tvHeaderStatus.setText(String.format(Locale.US, "%d shelves mapped", shelves.size()));
        binding.tvHeaderHint.setText(String.format(Locale.US, "%d pending audit • %d audited", pendingCount, auditedCount));

        if (shelves.isEmpty()) {
            binding.tvEmpty.setVisibility(View.VISIBLE);
            return;
        }
        binding.tvEmpty.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (Shelf shelf : shelves) {
            ItemShelfRowBinding row = ItemShelfRowBinding.inflate(inflater, binding.layoutShelfRows, false);
            ShelfAuditStatus status = latestStatusMap.get(shelf.getId());
            boolean audited = status != null && status.isAudited();

            row.tvShelfName.setText(TextUtils.isEmpty(shelf.getShelfName()) ? ("Shelf " + shelf.getId()) : shelf.getShelfName());
            row.tvShelfMeta.setText(buildMetaLine(shelf, status));
            row.tvShelfState.setText(audited ? "Audited" : "Onboarded");
            row.tvShelfState.setBackgroundResource(audited ? R.drawable.bg_step_chip_active : R.drawable.bg_chip_dark);
            row.btnOpen.setText("View");
            row.getRoot().setOnClickListener(v -> openShelf(shelf));
            row.btnOpen.setOnClickListener(v -> openShelf(shelf));
            binding.layoutShelfRows.addView(row.getRoot());
        }
    }

    private String buildMetaLine(Shelf shelf, ShelfAuditStatus status) {
        String order = shelf.getRouteOrder() > 0 ? ("Shelf " + shelf.getRouteOrder()) : "Mapped shelf";
        if (status != null && status.isAudited()) {
            return order + " • Audited" + (TextUtils.isEmpty(status.getAuditedAt()) ? "" : (" on " + status.getAuditedAt()));
        }
        return order + " • Onboarded image only";
    }

    private void openShelf(Shelf shelf) {
        if (shelf == null) {
            return;
        }
        Intent intent = new Intent(this, AuditReviewActivity.class);
        intent.putExtra(Constants.EXTRA_SHELF_ID, shelf.getId());
        startActivity(intent);
    }
}
