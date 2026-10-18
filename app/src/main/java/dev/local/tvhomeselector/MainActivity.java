package dev.local.tvhomeselector;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Public-API launcher tools. This screen does not execute shell commands or change defaults. */
public final class MainActivity extends Activity {
    private LinearLayout content;
    private TextView status;
    private TextView notice;
    private SharedPreferences preferences;
    private final List<ResolveInfo> launchers = new ArrayList<>();
    private final Set<String> componentNames = new HashSet<>();
    private String resolved;
    private String discoveryError;
    private String focusedAction;

    private Intent homeIntent() {
        return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addCategory(Intent.CATEGORY_DEFAULT);
    }

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        preferences = getSharedPreferences("selection", MODE_PRIVATE);
        if (savedState != null) focusedAction = savedState.getString("focus");
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        View focused = getCurrentFocus();
        if (focused != null && focused.getTag() instanceof String) {
            state.putString("focus", (String) focused.getTag());
        }
        super.onSaveInstanceState(state);
    }

    @Override public void onResume() {
        super.onResume();
        refresh(); // Always requery after Settings, cancellation, app removal, or launch-once.
    }

    private String name(ResolveInfo info) {
        ActivityInfo activity = info.activityInfo;
        return new ComponentName(activity.packageName, activity.name).flattenToString();
    }

    private boolean accessible(ActivityInfo activity) {
        if (activity == null || !activity.exported || !activity.enabled
                || !activity.applicationInfo.enabled) return false;
        int enabled = getPackageManager().getComponentEnabledSetting(
                new ComponentName(activity.packageName, activity.name));
        if (enabled == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                || enabled == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                || enabled == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED) return false;
        return activity.permission == null || checkSelfPermission(activity.permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void discover() {
        launchers.clear();
        componentNames.clear();
        discoveryError = null;
        resolved = null;
        PackageManager pm = getPackageManager();
        try {
            for (ResolveInfo info : pm.queryIntentActivities(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)) {
                if (accessible(info.activityInfo) && componentNames.add(name(info))) launchers.add(info);
            }
            Collections.sort(launchers, new ResolveInfo.DisplayNameComparator(pm));
            ResolveInfo resolution = pm.resolveActivity(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY);
            if (resolution != null && resolution.activityInfo != null) resolved = name(resolution);
        } catch (RuntimeException error) {
            discoveryError = error.getClass().getSimpleName() + ": " + error.getMessage();
        }
    }

    private void refresh() {
        View focus = getCurrentFocus();
        if (focus != null && focus.getTag() instanceof String) focusedAction = (String) focus.getTag();
        discover();
        ScrollView scroll = new ScrollView(this);
        // Explicit opaque backgrounds avoid stale scrolled text on this Sony's renderer.
        scroll.setBackgroundColor(0xFF101827);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setBackgroundColor(0xFF101827);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(38), dp(26), dp(38), dp(32));
        scroll.addView(content);
        setContentView(scroll);
        text(content, "Home Selector", 28, Color.WHITE);
        text(content, "Choose through supported Android screens. Use Launch once to test a launcher.", 17, 0xFFCCD7E7);
        status = text(content, "", 16, 0xFF9DE6FF);
        notice = text(content, "", 16, 0xFFFFD89D);
        showResolution();
        String desired = preferences.getString("desired", null);
        notice.setText(discoveryError != null ? "Discovery incomplete: " + discoveryError
                : HomePolicy.verification(desired, resolved, componentNames));
        LinearLayout toolbar = row(content);
        button(toolbar, "Refresh", "refresh", this::refresh);
        button(toolbar, "System Settings", "settings", () -> open(new Intent(Settings.ACTION_SETTINGS)));
        button(toolbar, "Resolve HOME", "resolve", () -> new AlertDialog.Builder(this)
                .setTitle("Open ordinary HOME resolution?")
                .setMessage("This opens the currently resolved Home or Android's HOME chooser. It does not clear a default. If a real system chooser offers Always, that choice may persist; a one-time chooser is not proof of a default change.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Open HOME", (dialog, which) -> open(homeIntent())).show());
        text(content, "Resolved HOME is an observation, not proof of a saved preference or physical remote behavior. System fallback activities may appear below; a HOME filter alone does not establish that an app is a usable launcher.", 15, 0xFFCCD7E7);
        if (launchers.isEmpty()) text(content, "No accessible HOME activities found. Discovery may be restricted on this firmware.", 18, Color.WHITE);
        for (ResolveInfo info : launchers) launcherCard(info);
        content.post(() -> {
            View requested = focusedAction == null ? null : content.findViewWithTag(focusedAction);
            if (requested != null) requested.requestFocus();
            else {
                View first = content.findViewWithTag("refresh");
                if (first != null) first.requestFocus();
            }
        });
    }

    private void showResolution() {
        String description = HomePolicy.isCandidate(resolved, componentNames)
                ? "Currently resolves HOME: " + resolved
                : "HOME resolution is a chooser/fallback/unrecognized activity: " + (resolved == null ? "none observable" : resolved);
        status.setText(description + "\n" + launchers.size() + " enabled, exported, accessible HOME candidate(s), for this app's Android user.");
    }

    private void launcherCard(ResolveInfo info) {
        String component = name(info);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        card.setBackgroundColor(0xFF192638);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.topMargin = dp(14);
        content.addView(card, cardParams);
        LinearLayout title = row(card);
        ImageView icon = new ImageView(this);
        try { icon.setImageDrawable(info.loadIcon(getPackageManager())); }
        catch (RuntimeException error) { icon.setImageResource(R.drawable.ic_home); }
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        title.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));
        CharSequence label;
        try { label = info.loadLabel(getPackageManager()); }
        catch (RuntimeException error) { label = info.activityInfo.packageName; }
        text(title, String.valueOf(label), 21, Color.WHITE);
        text(card, component, 15, 0xFFCCD7E7);
        if (component.equals(resolved)) text(card, "Matches current HOME resolution", 15, 0xFF9DE6FF);
        String className = info.activityInfo.name.toLowerCase(java.util.Locale.ROOT);
        boolean fallbackLike = className.contains("fallback") || className.contains("resolver");
        if (fallbackLike) {
            text(card, "Possible system fallback/resolver. Selection and launch are withheld; inspect this component before treating it as a launcher.", 16, 0xFFFFD89D);
            return;
        }
        LinearLayout actions = row(card);
        final String titleText = String.valueOf(label);
        button(actions, "Choose…", component + ":choose", () -> confirmSelection(component, titleText));
        button(actions, "Launch once", component + ":launch", () -> launchOnce(component));
        button(actions, "App details", component + ":details", () -> open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:" + info.activityInfo.packageName))));
    }

    private void confirmSelection(String component, String label) {
        new AlertDialog.Builder(this).setTitle("Choose " + label + "?")
                .setMessage("This app cannot silently set another app as Home. Continue to inspect supported system screens and choose this launcher there if offered. Opening a screen does not change the default.\n\n" + component)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", (dialog, which) -> {
                    preferences.edit().putString("desired", component).apply();
                    selectionOptions(component);
                }).show();
    }

    private void selectionOptions(String component) {
        discover();
        if (!componentNames.contains(component)) {
            message("The requested launcher was removed, disabled, or became inaccessible. Refresh and choose again.");
            return;
        }
        Intent[] actions = {new Intent(Settings.ACTION_HOME_SETTINGS),
                new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)};
        List<Intent> available = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        String[] actionLabels = {"Home Settings", "Default Apps Settings"};
        for (int index = 0; index < actions.length; index++) {
            ResolveInfo handler = getPackageManager().resolveActivity(actions[index], PackageManager.MATCH_DEFAULT_ONLY);
            if (handler != null && accessible(handler.activityInfo)) {
                available.add(actions[index]);
                labels.add(actionLabels[index] + "\n" + name(handler));
            }
        }
        if (available.isEmpty()) {
            message("No accessible Home/default-app Settings handler. Use the TV's app details to clear defaults only if offered, then try ordinary HOME resolution. An ADB-assisted switch requires separately verified recovery. This app cannot perform it.");
            return;
        }
        new AlertDialog.Builder(this).setTitle("Available handlers; functionality unverified")
                .setItems(labels.toArray(new String[0]), (dialog, which) -> open(available.get(which)))
                .setNegativeButton("Cancel", null).show();
    }

    private void launchOnce(String component) {
        discover();
        if (!componentNames.contains(component)) { message("Launcher is no longer accessible. Refresh the list."); return; }
        ComponentName target = ComponentName.unflattenFromString(component);
        if (target == null) { message("Invalid component; no activity launched."); return; }
        open(homeIntent().setComponent(target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        // No desired-state write and no default-change claim for explicit launches.
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
            notice.setText("Activity requested. No default change is verified. Return here to recheck HOME resolution.");
        } catch (ActivityNotFoundException | SecurityException error) {
            message("Cannot open this activity: " + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private void message(String text) {
        notice.setText(text);
        new AlertDialog.Builder(this).setTitle("Home Selector").setMessage(text).setPositiveButton("OK", null).show();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private LinearLayout row(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    private TextView text(LinearLayout parent, String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setPadding(dp(6), dp(5), dp(6), dp(5));
        view.setFocusable(false);
        parent.addView(view, new LinearLayout.LayoutParams(-2, -2));
        return view;
    }

    private void button(LinearLayout parent, String value, String tag, Runnable action) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(16);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setTag(tag);
        button.setMinHeight(dp(48));
        button.setFocusable(true);
        button.setBackgroundResource(R.drawable.focus_button);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(4), dp(8), dp(4), dp(8));
        parent.addView(button, params);
        button.setOnClickListener(view -> action.run());
    }
}
