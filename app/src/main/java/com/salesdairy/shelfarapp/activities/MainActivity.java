package com.salesdairy.shelfarapp.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.salesdairy.shelfarapp.data.AuditRepository;
import com.salesdairy.shelfarapp.data.ShelfRepository;
import com.salesdairy.shelfarapp.data.StoreReferenceRepository;
import com.salesdairy.shelfarapp.databinding.ActivityMainBinding;
import com.salesdairy.shelfarapp.models.Shelf;
import com.salesdairy.shelfarapp.models.ShelfAuditStatus;
import com.salesdairy.shelfarapp.models.StoreReference;
import com.salesdairy.shelfarapp.utils.Constants;
import com.salesdairy.shelfarapp.utils.CrashLogRepository;

import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "ShelfARFlow";

    private ActivityMainBinding binding;
    private StoreReferenceRepository storeReferenceRepository;
    private ShelfRepository shelfRepository;
    private AuditRepository auditRepository;

    private boolean launchedFromSalesDiary;
    private boolean pendingSalesDiaryAutoStart;
    private String externalAuditSessionToken;
    private String externalCallbackScheme;
    private String externalCallbackHost;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        storeReferenceRepository = new StoreReferenceRepository(this);
        shelfRepository = new ShelfRepository(this);
        auditRepository = new AuditRepository(this);
        CrashLogRepository.noteBreadcrumb(this, "MainActivity onCreate");

        readExternalLaunch(getIntent());

        binding.btnOnboardShelf.setOnClickListener(v -> openOnboardingFlow());
        binding.btnAuditShelf.setOnClickListener(v -> startAutomaticAudit());
        binding.btnShelves.setOnClickListener(v -> openShelvesScreen());
    }


    @Override
    public void onBackPressed() {
        if (launchedFromSalesDiary) {
            sendResultBackToSalesDiary("failure", "audit_cancelled");
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        readExternalLaunch(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        CrashLogRepository.noteBreadcrumb(this, "MainActivity onResume");
        CrashLogRepository.CrashInfo crashInfo = CrashLogRepository.consumeLastCrash(this);
        if (crashInfo != null) {
            Log.e(TAG, "Recovered after unexpected close\n" + crashInfo.report);
            Toast.makeText(this, "App recovered after an unexpected close. Check logs.", Toast.LENGTH_LONG).show();
        }
        StoreReference reference = storeReferenceRepository.getActiveStoreReference();
        boolean ready = reference != null && reference.getCloudAnchorId() != null && !reference.getCloudAnchorId().trim().isEmpty();
        if (!ready) {
            binding.tvReferenceStatus.setText("Store reference needed");
            binding.tvReferenceStatusHint.setText("Start onboarding to save the store entry point first.");
        } else {
            List<Shelf> shelves = shelfRepository.getShelvesForStoreReference(reference.getId());
            Map<Integer, ShelfAuditStatus> latestStatusMap = auditRepository.getLatestShelfAuditStatusMap();
            int auditedCount = 0;
            for (Shelf shelf : shelves) {
                ShelfAuditStatus status = latestStatusMap.get(shelf.getId());
                if (status != null && status.isAudited()) {
                    auditedCount++;
                }
            }
            int pendingCount = Math.max(0, shelves.size() - auditedCount);
            binding.tvReferenceStatus.setText(pendingCount > 0
                    ? "Store ready for audit"
                    : "Store mapped and ready");
            binding.tvReferenceStatusHint.setText(shelves.isEmpty()
                    ? "Entry point saved. Keep AR open and add shelves in one walk."
                    : (shelves.size() + " shelves mapped • " + pendingCount + " pending audit"));
        }

        if (pendingSalesDiaryAutoStart) {
            pendingSalesDiaryAutoStart = false;
            binding.btnAuditShelf.post(() -> binding.btnAuditShelf.performClick());
        }
    }

    private void readExternalLaunch(Intent intent) {
        launchedFromSalesDiary = intent != null
                && Constants.SOURCE_APP_SALESDIARY.equalsIgnoreCase(intent.getStringExtra(Constants.EXTRA_SOURCE_APP));
        if (launchedFromSalesDiary) {
            externalAuditSessionToken = intent.getStringExtra(Constants.EXTRA_EXTERNAL_AUDIT_SESSION_TOKEN);
            externalCallbackScheme = intent.getStringExtra(Constants.EXTRA_EXTERNAL_CALLBACK_SCHEME);
            externalCallbackHost = intent.getStringExtra(Constants.EXTRA_EXTERNAL_CALLBACK_HOST);
            pendingSalesDiaryAutoStart = true;
            CrashLogRepository.noteBreadcrumb(this, "MainActivity external launch from SalesDiary");
        } else {
            externalAuditSessionToken = null;
            externalCallbackScheme = null;
            externalCallbackHost = null;
            pendingSalesDiaryAutoStart = false;
        }
    }

    private void startAutomaticAudit() {
        StoreReference reference = storeReferenceRepository.getPreferredStoreReference();
        if (reference == null || reference.getCloudAnchorId() == null || reference.getCloudAnchorId().trim().isEmpty()) {
            Toast.makeText(this, "Save the store reference first.", Toast.LENGTH_LONG).show();
            sendResultBackToSalesDiary("failure", "store_reference_required");
            return;
        }
        List<Shelf> shelves = shelfRepository.getShelvesForStoreReference(reference.getId());
        if (shelves.isEmpty()) {
            Toast.makeText(this, "No shelves are mapped under the active store reference.", Toast.LENGTH_LONG).show();
            sendResultBackToSalesDiary("failure", "no_shelves_mapped");
            return;
        }
        long sessionId = auditRepository.getOrCreateActiveSession(Constants.DEFAULT_OUTLET_ID, reference.getId());
        int nextShelfId = auditRepository.findNextPendingShelfIdForSession(shelves, -1, sessionId);
        if (nextShelfId <= 0) {
            auditRepository.markSessionCompleted(sessionId);
            Toast.makeText(this, "All shelves are already audited for this run.", Toast.LENGTH_LONG).show();
            sendResultBackToSalesDiary("success", "already_completed");
            return;
        }
        try {
            Intent intent = new Intent(this, AuditNavigationActivity.class);
            intent.putExtra(Constants.EXTRA_SHELF_ID, nextShelfId);
            intent.putExtra(Constants.EXTRA_AUDIT_SESSION_ID, sessionId);
            if (launchedFromSalesDiary) {
                intent.putExtra(Constants.EXTRA_SOURCE_APP, Constants.SOURCE_APP_SALESDIARY);
                intent.putExtra(Constants.EXTRA_EXTERNAL_AUDIT_SESSION_TOKEN, externalAuditSessionToken);
                intent.putExtra(Constants.EXTRA_EXTERNAL_CALLBACK_SCHEME, externalCallbackScheme);
                intent.putExtra(Constants.EXTRA_EXTERNAL_CALLBACK_HOST, externalCallbackHost);

                Intent sourceIntent = getIntent();
                if (sourceIntent != null) {
                    intent.putExtra("retailer_id", sourceIntent.getIntExtra("retailer_id", 0));
                    intent.putExtra("territory_id", sourceIntent.getIntExtra("territory_id", 0));
                    intent.putExtra("beatplanid", sourceIntent.getIntExtra("beatplanid", 0));
                    intent.putExtra("beatscheduleid", sourceIntent.getIntExtra("beatscheduleid", 0));
                    intent.putExtra("visit_id", sourceIntent.getIntExtra("visit_id", 0));
                    intent.putExtra("retailer_mid", sourceIntent.getStringExtra("retailer_mid"));
                    intent.putExtra("allowActivity", sourceIntent.getBooleanExtra("allowActivity", false));
                    intent.putExtra("unscheduled_visit", sourceIntent.getBooleanExtra("unscheduled_visit", true));
                }
            }
            Log.d(TAG, "Main menu -> automatic audit clicked");
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open automatic audit", e);
            Toast.makeText(this, "Unable to open audit. Check logs.", Toast.LENGTH_LONG).show();
            sendResultBackToSalesDiary("failure", "unable_to_open_audit");
        }
    }

    private void openOnboardingFlow() {
        StoreReference reference = storeReferenceRepository.getPreferredStoreReference();
        if (reference == null || reference.getCloudAnchorId() == null || reference.getCloudAnchorId().trim().isEmpty()) {
            openScreen(StoreReferenceActivity.class,
                    "Main menu -> open store reference setup from onboarding button",
                    "Unable to open store reference setup. Check logs.");
            return;
        }
        openScreen(OnboardShelfActivity.class,
                "Main menu -> onboard shelves clicked",
                "Unable to open shelf onboarding. Check logs.");
    }

    private void openShelvesScreen() {
        try {
            Intent intent = new Intent(this, ShelfListActivity.class);
            StoreReference reference = storeReferenceRepository.getPreferredStoreReference();
            if (reference != null) {
                intent.putExtra(Constants.EXTRA_STORE_REFERENCE_ID, reference.getId());
            }
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open shelves screen", e);
            Toast.makeText(this, "Unable to open shelves. Check logs.", Toast.LENGTH_LONG).show();
        }
    }

    private void openScreen(Class<?> target, String logMessage, String errorMessage) {
        Log.d(TAG, logMessage);
        try {
            startActivity(new Intent(this, target));
        } catch (Exception e) {
            Log.e(TAG, "Failed to open " + target.getSimpleName(), e);
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
        }
    }

    private void sendResultBackToSalesDiary(String status, String message) {
        if (!launchedFromSalesDiary || TextUtils.isEmpty(externalCallbackScheme) || TextUtils.isEmpty(externalCallbackHost)) {
            return;
        }
        try {
            Uri.Builder builder = new Uri.Builder()
                    .scheme(externalCallbackScheme)
                    .authority(externalCallbackHost)
                    .appendQueryParameter(Constants.EXTRA_EXTERNAL_STATUS, status)
                    .appendQueryParameter("audit_type", "visual_audit");
            if (!TextUtils.isEmpty(externalAuditSessionToken)) {
                builder.appendQueryParameter(Constants.EXTRA_EXTERNAL_AUDIT_SESSION_TOKEN, externalAuditSessionToken);
            }
            if (!TextUtils.isEmpty(message)) {
                builder.appendQueryParameter(Constants.EXTRA_EXTERNAL_RESULT_MESSAGE, message);
            }
            Intent callbackIntent = new Intent(Intent.ACTION_VIEW, builder.build());
            callbackIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(callbackIntent);
            finish();
        } catch (Exception e) {
            Log.e(TAG, "Failed to callback SalesDiary", e);
        }
    }
}
