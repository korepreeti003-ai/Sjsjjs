package com.research.netspoof;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppSelectActivity extends AppCompatActivity {

    public static final String EXTRA_PKG  = "pkg";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_SEL  = "selected_pkg";

    private AppAdapter   adapter;
    private FrameLayout  flLoading;
    private RecyclerView rv;
    private LinearLayout llEmpty;
    private TextView     tvAppCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_select);

        // Hide action bar — we use our own custom toolbar
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        // ── Wire views ──────────────────────────────────────────
        flLoading  = findViewById(R.id.fl_loading);
        rv         = findViewById(R.id.rv_apps);
        llEmpty    = findViewById(R.id.ll_empty);
        tvAppCount = findViewById(R.id.tv_app_count);
        EditText search  = findViewById(R.id.et_search);
        ImageButton btnBack = findViewById(R.id.btn_back);

        // Back button
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        String currentPkg = getIntent().getStringExtra(EXTRA_SEL);
        if (currentPkg == null) currentPkg = "";

        rv.setLayoutManager(new LinearLayoutManager(this));

        // ── Load apps off main thread ────────────────────────────
        final String finalCurrentPkg = currentPkg;
        ExecutorService exec = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        exec.execute(() -> {
            List<AppInfo> apps = loadApps();
            handler.post(() -> {
                flLoading.setVisibility(View.GONE);

                if (apps.isEmpty()) {
                    llEmpty.setVisibility(View.VISIBLE);
                    rv.setVisibility(View.GONE);
                } else {
                    llEmpty.setVisibility(View.GONE);
                    rv.setVisibility(View.VISIBLE);
                }

                if (tvAppCount != null) tvAppCount.setText(apps.size() + " apps");

                adapter = new AppAdapter(apps, finalCurrentPkg, app -> {
                    Intent result = new Intent();
                    result.putExtra(EXTRA_PKG,  app.packageName);
                    result.putExtra(EXTRA_NAME, app.appName);
                    setResult(Activity.RESULT_OK, result);
                    finish();
                });
                rv.setAdapter(adapter);
            });
        });

        // ── Search filter ────────────────────────────────────────
        if (search != null) {
            search.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void afterTextChanged(Editable s) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                    if (adapter != null) {
                        adapter.filter(s.toString());
                        boolean empty = adapter.getItemCount() == 0;
                        llEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                        rv.setVisibility(empty ? View.GONE : View.VISIBLE);
                    }
                }
            });
        }
    }

    // ── Load all launchable non-system apps ──────────────────────
    private List<AppInfo> loadApps() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> installed =
                pm.getInstalledApplications(PackageManager.GET_META_DATA);
        List<AppInfo> result = new ArrayList<>();

        for (ApplicationInfo ai : installed) {
            // Must have a launcher icon (i.e. launchable game/app)
            if (pm.getLaunchIntentForPackage(ai.packageName) == null) continue;
            // Skip ourselves
            if (ai.packageName.equals(getPackageName())) continue;

            try {
                String name = pm.getApplicationLabel(ai).toString();
                result.add(new AppInfo(ai.packageName, name, pm.getApplicationIcon(ai)));
            } catch (Exception ignored) {}
        }

        result.sort(Comparator.comparing(a -> a.appName.toLowerCase()));
        return result;
    }
}
