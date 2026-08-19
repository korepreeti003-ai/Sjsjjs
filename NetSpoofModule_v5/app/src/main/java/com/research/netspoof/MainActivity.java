package com.research.netspoof;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    // Views
    private SwitchCompat swEnabled, swRootBypass, swXposedHide, swPhoneSpoof, swShowMsg;
    private TextView     tvStatus, tvAppName, tvAppPkg, tvCarrierBadge;
    private ImageView    ivAppIcon;
    private View         cardAppEmpty, cardAppSelected;
    private Button       btnSelectApp, btnSave;
    private EditText     etNumber, etImsi, etOperator, etSimOp,
                         etIccid, etCountry, etMccMnc, etAndroidId, etImei,
                         etCustomMsg;

    private SharedPreferences prefs;
    private String selectedPkg = "", selectedName = "";
    private final Random rng = new Random();

    private final ActivityResultLauncher<Intent> appPicker =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                selectedPkg  = result.getData().getStringExtra(AppSelectActivity.EXTRA_PKG);
                selectedName = result.getData().getStringExtra(AppSelectActivity.EXTRA_NAME);
                updateAppCard();
            }
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        prefs = getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE);
        bindViews();
        loadPrefs();
        setupListeners();
        applyCarrier(etNumber.getText().toString(), false);
    }

    private void bindViews() {
        swEnabled     = findViewById(R.id.sw_enabled);
        swRootBypass  = findViewById(R.id.sw_root_bypass);
        swXposedHide  = findViewById(R.id.sw_xposed_hide);
        swPhoneSpoof  = findViewById(R.id.sw_phone_spoof);
        swShowMsg     = findViewById(R.id.sw_show_msg);
        tvStatus      = findViewById(R.id.tv_status);
        tvAppName     = findViewById(R.id.tv_app_name);
        tvAppPkg      = findViewById(R.id.tv_app_pkg);
        tvCarrierBadge= findViewById(R.id.tv_carrier_badge);
        ivAppIcon     = findViewById(R.id.iv_app_icon);
        cardAppEmpty  = findViewById(R.id.card_app_empty);
        cardAppSelected=findViewById(R.id.card_app_selected);
        btnSelectApp  = findViewById(R.id.btn_select_app);
        btnSave       = findViewById(R.id.btn_save);
        etNumber      = findViewById(R.id.et_number);
        etImsi        = findViewById(R.id.et_imsi);
        etOperator    = findViewById(R.id.et_operator);
        etSimOp       = findViewById(R.id.et_sim_op);
        etIccid       = findViewById(R.id.et_iccid);
        etCountry     = findViewById(R.id.et_country);
        etMccMnc      = findViewById(R.id.et_mcc_mnc);
        etAndroidId   = findViewById(R.id.et_android_id);
        etImei        = findViewById(R.id.et_imei);
        etCustomMsg   = findViewById(R.id.et_custom_msg);
    }

    private void loadPrefs() {
        swEnabled    .setChecked(prefs.getBoolean(Prefs.KEY_ENABLED,     true));
        swRootBypass .setChecked(prefs.getBoolean(Prefs.KEY_ROOT_BYPASS, true));
        swXposedHide .setChecked(prefs.getBoolean(Prefs.KEY_XPOSED_HIDE, true));
        swPhoneSpoof .setChecked(prefs.getBoolean(Prefs.KEY_PHONE_SPOOF, true));
        swShowMsg    .setChecked(prefs.getBoolean(Prefs.KEY_SHOW_MSG,    false));

        selectedPkg  = prefs.getString(Prefs.KEY_TARGET_PKG,  "");
        selectedName = prefs.getString(Prefs.KEY_TARGET_NAME, "");

        etNumber   .setText(prefs.getString(Prefs.KEY_NUMBER,     Prefs.DEF_NUMBER));
        etImsi     .setText(prefs.getString(Prefs.KEY_IMSI,       Prefs.DEF_IMSI));
        etOperator .setText(prefs.getString(Prefs.KEY_OPERATOR,   Prefs.DEF_OPERATOR));
        etSimOp    .setText(prefs.getString(Prefs.KEY_SIM_OP,     Prefs.DEF_SIM_OP));
        etIccid    .setText(prefs.getString(Prefs.KEY_ICCID,      Prefs.DEF_ICCID));
        etCountry  .setText(prefs.getString(Prefs.KEY_COUNTRY,    Prefs.DEF_COUNTRY));
        etMccMnc   .setText(prefs.getString(Prefs.KEY_MCC_MNC,    Prefs.DEF_MCC_MNC));
        etAndroidId.setText(prefs.getString(Prefs.KEY_ANDROID_ID, Prefs.DEF_ANDROID_ID));
        etImei     .setText(prefs.getString(Prefs.KEY_IMEI,       Prefs.DEF_IMEI));
        etCustomMsg.setText(prefs.getString(Prefs.KEY_CUSTOM_MSG, Prefs.DEF_CUSTOM_MSG));

        updateAppCard();
        updateStatusBadge();
    }

    private void setupListeners() {
        btnSelectApp.setOnClickListener(v ->
            appPicker.launch(new Intent(this, AppSelectActivity.class)));

        etNumber.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { applyCarrier(s.toString(), true); }
        });

        // Random generators
        findViewById(R.id.btn_rand_number).setOnClickListener(v -> etNumber.setText(randPhone()));
        findViewById(R.id.btn_rand_imei)  .setOnClickListener(v -> etImei.setText(randImei()));
        findViewById(R.id.btn_rand_aid)   .setOnClickListener(v -> etAndroidId.setText(randHex(16)));

        swEnabled.setOnCheckedChangeListener((b, on) -> updateStatusBadge());
        btnSave.setOnClickListener(v -> doSave());
    }

    private void applyCarrier(String raw, boolean toast) {
        if (raw == null || raw.trim().isEmpty()) return;
        CarrierLookup.CarrierInfo info = CarrierLookup.detect(raw.trim());
        if (info == null) return;
        etOperator.setText(info.operator);
        etSimOp   .setText(info.simOp);
        etMccMnc  .setText(info.mccMnc);
        etCountry .setText(info.country);
        etIccid   .setText(genIccid(info.mccMnc));
        etImsi    .setText(genImsi(info.mccMnc));
        if (tvCarrierBadge != null) tvCarrierBadge.setText("📡 " + info.operator);
        if (toast) Toast.makeText(this, "📡 " + info.operator + " detected!", Toast.LENGTH_SHORT).show();
    }

    private void doSave() {
        if (selectedPkg.isEmpty()) {
            Toast.makeText(this, "⚠ Pehle target app select karo!", Toast.LENGTH_SHORT).show();
            return;
        }
        applyCarrier(etNumber.getText().toString().trim(), false);

        prefs.edit()
            .putBoolean(Prefs.KEY_ENABLED,      swEnabled.isChecked())
            .putBoolean(Prefs.KEY_ROOT_BYPASS,  swRootBypass.isChecked())
            .putBoolean(Prefs.KEY_XPOSED_HIDE,  swXposedHide.isChecked())
            .putBoolean(Prefs.KEY_PHONE_SPOOF,  swPhoneSpoof.isChecked())
            .putBoolean(Prefs.KEY_SHOW_MSG,     swShowMsg.isChecked())
            .putString(Prefs.KEY_TARGET_PKG,    selectedPkg)
            .putString(Prefs.KEY_TARGET_NAME,   selectedName)
            .putString(Prefs.KEY_NUMBER,        etNumber.getText().toString().trim())
            .putString(Prefs.KEY_IMSI,          etImsi.getText().toString().trim())
            .putString(Prefs.KEY_OPERATOR,      etOperator.getText().toString().trim())
            .putString(Prefs.KEY_SIM_OP,        etSimOp.getText().toString().trim())
            .putString(Prefs.KEY_ICCID,         etIccid.getText().toString().trim())
            .putString(Prefs.KEY_COUNTRY,       etCountry.getText().toString().trim())
            .putString(Prefs.KEY_MCC_MNC,       etMccMnc.getText().toString().trim())
            .putString(Prefs.KEY_ANDROID_ID,    etAndroidId.getText().toString().trim())
            .putString(Prefs.KEY_IMEI,          etImei.getText().toString().trim())
            .putString(Prefs.KEY_CUSTOM_MSG,    etCustomMsg.getText().toString().trim())
            .apply();

        btnSave.setText("✓ Saved!");
        btnSave.postDelayed(() -> btnSave.setText("💾 Save & Apply"), 1800);
        Toast.makeText(this,
            "✅ Config saved!\nTarget app force-stop karo phir reopen karo.",
            Toast.LENGTH_LONG).show();
        updateStatusBadge();
    }

    // ── Helpers ────────────────────────────────────────────────────────────
    private String genImsi(String mccMnc) {
        String pfx = (mccMnc != null && mccMnc.length() >= 5) ? mccMnc : "40410";
        StringBuilder sb = new StringBuilder(pfx);
        for (int i = 0; i < 15 - pfx.length(); i++) sb.append(rng.nextInt(10));
        return sb.toString();
    }

    private String genIccid(String mccMnc) {
        String mcc = (mccMnc != null && mccMnc.length() >= 3) ? mccMnc.substring(0, 3) : "404";
        String itu;
        switch (mcc) {
            case "404": case "405": itu = "91";  break;
            case "310": case "311": itu = "01";  break;
            case "234": case "235": itu = "44";  break;
            case "424":             itu = "971"; break;
            case "420":             itu = "966"; break;
            case "410":             itu = "92";  break;
            default:                itu = "91";  break;
        }
        String base = "89" + itu;
        StringBuilder sb = new StringBuilder(base);
        for (int i = 0; i < 20 - base.length(); i++) sb.append(rng.nextInt(10));
        return sb.toString();
    }

    private String randPhone() {
        int[] p = {7, 8, 9};
        return "+91" + p[rng.nextInt(3)] + String.format("%09d", rng.nextInt(1_000_000_000));
    }

    private String randImei() {
        StringBuilder sb = new StringBuilder("35");
        for (int i = 0; i < 13; i++) sb.append(rng.nextInt(10));
        return sb.toString();
    }

    private String randHex(int len) {
        String h = "0123456789abcdef";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) sb.append(h.charAt(rng.nextInt(16)));
        return sb.toString();
    }

    private void updateAppCard() {
        if (selectedPkg.isEmpty()) {
            cardAppEmpty   .setVisibility(View.VISIBLE);
            cardAppSelected.setVisibility(View.GONE);
        } else {
            cardAppEmpty   .setVisibility(View.GONE);
            cardAppSelected.setVisibility(View.VISIBLE);
            tvAppName.setText(selectedName.isEmpty() ? selectedPkg : selectedName);
            tvAppPkg .setText(selectedPkg);
            try {
                Drawable icon = getPackageManager().getApplicationIcon(selectedPkg);
                ivAppIcon.setImageDrawable(icon);
            } catch (PackageManager.NameNotFoundException e) {
                ivAppIcon.setImageResource(android.R.drawable.sym_def_app_icon);
            }
        }
    }

    private void updateStatusBadge() {
        boolean on = swEnabled.isChecked() && !selectedPkg.isEmpty();
        tvStatus.setText(on ? "● ACTIVE" : "○ INACTIVE");
        tvStatus.setTextColor(getColor(on ? R.color.green_active : R.color.text_secondary));
    }
}
