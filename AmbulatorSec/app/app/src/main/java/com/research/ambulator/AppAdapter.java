package com.research.ambulator;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class AppAdapter extends RecyclerView.Adapter<AppAdapter.VH> {

    public interface OnAppSelected { void onSelected(AppInfo app); }

    private final List<AppInfo> allApps;
    private List<AppInfo> filtered;
    private String selectedPkg;
    private final OnAppSelected listener;

    public AppAdapter(List<AppInfo> apps, String selectedPkg, OnAppSelected listener) {
        this.allApps     = apps;
        this.filtered    = new ArrayList<>(apps);
        this.selectedPkg = selectedPkg;
        this.listener    = listener;
    }

    public void filter(String query) {
        filtered = new ArrayList<>();
        String q = query.toLowerCase().trim();
        for (AppInfo a : allApps) {
            if (a.appName.toLowerCase().contains(q) || a.packageName.toLowerCase().contains(q)) {
                filtered.add(a);
            }
        }
        notifyDataSetChanged();
    }

    public void setSelected(String pkg) {
        this.selectedPkg = pkg;
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        AppInfo app = filtered.get(pos);
        h.icon.setImageDrawable(app.icon);
        h.name.setText(app.appName);
        h.pkg.setText(app.packageName);
        boolean sel = app.packageName.equals(selectedPkg);
        h.check.setVisibility(sel ? View.VISIBLE : View.GONE);
        h.itemView.setAlpha(1f);
        h.itemView.setOnClickListener(v -> {
            listener.onSelected(app);
            setSelected(app.packageName);
        });
    }

    @Override public int getItemCount() { return filtered.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView  name, pkg, check;
        VH(View v) {
            super(v);
            icon  = v.findViewById(R.id.iv_icon);
            name  = v.findViewById(R.id.tv_name);
            pkg   = v.findViewById(R.id.tv_pkg);
            check = v.findViewById(R.id.iv_check);
        }
    }
}
