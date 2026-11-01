package tv.mix.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final String[] DEFAULT_FAVORITES = {
        "com.netflix.ninja", "com.disney.disneyplus",
        "com.google.android.youtube.tv", "com.apple.atve.androidtv.appletv"
    };
    private static final int[] THEMES_TOP = {0xFF0D1729, 0xFF141316, 0xFF1E1930};
    private static final int[] THEMES_BOTTOM = {0xFF060910, 0xFF090909, 0xFF080913};
    private static final int[] THEMES_ACCENT = {0xFF8DB6EC, 0xFFE9E4D7, 0xFFBEAAFF};
    private static final String[] THEME_NAMES = {"MIDNIGHT", "GRAPHITE", "AURORA"};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private PackageManager packageManager;
    private final Map<String, AppEntry> installed = new LinkedHashMap<>();
    private final List<String> favoriteIds = new ArrayList<>();
    private TextView focusTitle;
    private TextView focusDescription;
    private TextView clock;
    private int theme;
    private String lastFocusedPackage = "";
    private final Runnable clockRefresh = new Runnable() {
        @Override public void run() {
            if (clock != null) {
                clock.setText(new SimpleDateFormat("EEE, d MMM   HH:mm", Locale.getDefault()).format(new Date()));
            }
            handler.postDelayed(this, 30000L);
        }
    };

    private static final class AppEntry {
        final String packageName;
        final String className;
        final String label;
        final Drawable icon;
        AppEntry(String pkg, String cls, String name, Drawable image) {
            packageName = pkg; className = cls; label = name; icon = image;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        prefs = getSharedPreferences("mixtv", MODE_PRIVATE);
        packageManager = getPackageManager();
        theme = prefs.getInt("theme", 0);
        loadApps();
        loadFavorites();
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (prefs != null) {
            loadApps();
            loadFavorites();
            render();
        }
        handler.removeCallbacks(clockRefresh);
        handler.post(clockRefresh);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(clockRefresh);
        super.onPause();
    }

    private int dp(float value) {
        return (int) (getResources().getDisplayMetrics().density * value + .5f);
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), 0x32FFFFFF);
        return d;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(color);
        v.setTextSize(size);
        if (bold) v.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        return v;
    }

    private LinearLayout vertical() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }

    private LinearLayout horizontal() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    private void loadApps() {
        installed.clear();
        collectApps(Intent.CATEGORY_LEANBACK_LAUNCHER);
        collectApps(Intent.CATEGORY_LAUNCHER);
        List<AppEntry> sorted = new ArrayList<>(installed.values());
        Collections.sort(sorted, Comparator.comparing(a -> a.label.toLowerCase(Locale.getDefault())));
        installed.clear();
        for (AppEntry app : sorted) installed.put(app.packageName, app);
    }

    private void collectApps(String category) {
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(category);
        List<ResolveInfo> results = packageManager.queryIntentActivities(query, 0);
        for (ResolveInfo info : results) {
            if (info.activityInfo == null) continue;
            String pkg = info.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || installed.containsKey(pkg)) continue;
            CharSequence title = info.loadLabel(packageManager);
            try {
                installed.put(pkg, new AppEntry(pkg, info.activityInfo.name,
                        title == null ? pkg : title.toString(), info.loadIcon(packageManager)));
            } catch (RuntimeException ignored) { }
        }
    }

    private void loadFavorites() {
        favoriteIds.clear();
        if (!prefs.contains("favorites")) {
            for (String p : DEFAULT_FAVORITES) {
                if (installed.containsKey(p) && !favoriteIds.contains(p)) favoriteIds.add(p);
            }
            for (String p : installed.keySet()) {
                if (favoriteIds.size() >= 5) break;
                if (!favoriteIds.contains(p)) favoriteIds.add(p);
            }
            saveFavorites();
        } else {
            String raw = prefs.getString("favorites", "");
            for (String p : raw.split(",")) {
                if (!p.isEmpty() && !favoriteIds.contains(p)) favoriteIds.add(p);
            }
        }
    }

    private void saveFavorites() {
        prefs.edit().putString("favorites", android.text.TextUtils.join(",", favoriteIds)).apply();
    }

    private void render() {
        theme = Math.floorMod(prefs.getInt("theme", 0), THEMES_TOP.length);
        LinearLayout main = vertical();
        main.setPadding(dp(42), dp(23), dp(42), dp(36));
        main.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{THEMES_TOP[theme], THEMES_BOTTOM[theme]}));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(main);
        setContentView(scroll);

        LinearLayout head = horizontal();
        TextView brand = text("◈   MIX TV", 21, Color.WHITE, true);
        head.addView(brand, new LinearLayout.LayoutParams(0, dp(44), 1));
        brand.setGravity(Gravity.CENTER_VERTICAL);
        clock = text("", 14, 0xFFB8C7D9, false);
        head.addView(clock);
        main.addView(head);

        LinearLayout hero = vertical();
        hero.setPadding(dp(4), dp(39), dp(4), dp(27));
        TextView kicker = text("YOUR SPACE  /  " + THEME_NAMES[theme], 12, THEMES_ACCENT[theme], true);
        kicker.setLetterSpacing(.13f);
        hero.addView(kicker);
        focusTitle = text(greeting(), 40, 0xFFFBFCFF, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dp(7);
        hero.addView(focusTitle, titleParams);
        focusDescription = text("Everything you love, one simple screen.", 15, 0xFF9BAABC, false);
        LinearLayout.LayoutParams sub = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sub.topMargin = dp(7);
        hero.addView(focusDescription, sub);
        main.addView(hero);

        main.addView(sectionHeader("FAVORITES", "LONG PRESS OK TO ORGANIZE"));
        LinearLayout dock = horizontal();
        dock.setPadding(dp(13), dp(12), dp(13), dp(12));
        dock.setBackground(rounded(0x243E516C, 24));
        LinearLayout.LayoutParams dockParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dockParams.topMargin = dp(10);
        main.addView(dock, dockParams);

        View preferredFocus = null;
        View initialFocus = null;
        for (String pkg : favoriteIds) {
            AppEntry app = installed.get(pkg);
            if (app == null) continue;
            View tile = appTile(app, true);
            dock.addView(tile);
            if (initialFocus == null) initialFocus = tile;
            if (pkg.equals(lastFocusedPackage)) preferredFocus = tile;
        }
        dock.addView(actionTile("+", "ADD", () -> showAppPicker(), true));

        LinearLayout quick = horizontal();
        LinearLayout.LayoutParams quickParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        quickParams.topMargin = dp(17);
        main.addView(quick, quickParams);
        quick.addView(actionButton("▣  INPUTS", this::showInputs));
        quick.addView(actionButton("⚙  SETTINGS", this::openSettings));
        quick.addView(actionButton("◐  THEME", () -> {
            prefs.edit().putInt("theme", (theme + 1) % THEMES_TOP.length).apply();
            render();
        }));
        quick.addView(actionButton("⌂  HOME DEFAULT", this::showHomeChooser));

        LinearLayout.LayoutParams allTitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        allTitleParams.topMargin = dp(24);
        main.addView(sectionHeader("ALL APPS", "PRESS OK TO OPEN  •  LONG PRESS TO PIN"), allTitleParams);
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(6);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        grid.setUseDefaultMargins(false);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gridParams.topMargin = dp(12);
        main.addView(grid, gridParams);
        for (AppEntry app : installed.values()) {
            View tile = appTile(app, false);
            GridLayout.LayoutParams cell = new GridLayout.LayoutParams();
            cell.width = dp(132);
            cell.height = dp(112);
            cell.setMargins(0, 0, dp(12), dp(13));
            grid.addView(tile, cell);
            if (app.packageName.equals(lastFocusedPackage) && preferredFocus == null) preferredFocus = tile;
        }
        if (installed.isEmpty()) main.addView(text("No launchable apps found. Check installed TV apps.", 18, Color.WHITE, false));
        TextView foot = text("MIX TV 1.0  •  OFFLINE  •  NO SYSTEM CHANGES", 11, 0xFF8193AA, false);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fp.topMargin = dp(16);
        main.addView(foot, fp);

        View target = preferredFocus == null ? initialFocus : preferredFocus;
        if (target != null) target.requestFocus();
        else if (dock.getChildCount() > 0) dock.getChildAt(0).requestFocus();
        clockRefresh.run();
    }

    private String greeting() {
        int hour = Integer.parseInt(new SimpleDateFormat("H", Locale.US).format(new Date()));
        return hour < 11 ? "Good morning." : hour < 17 ? "Good afternoon." : "Good evening.";
    }

    private LinearLayout sectionHeader(String title, String note) {
        LinearLayout row = horizontal();
        TextView left = text(title, 14, 0xFFE3ECF7, true);
        left.setLetterSpacing(.07f);
        row.addView(left, new LinearLayout.LayoutParams(0, dp(25), 1));
        left.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(text(note, 10, 0xFF8496AB, false));
        return row;
    }

    private View appTile(AppEntry app, boolean dockTile) {
        LinearLayout tile = vertical();
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(8), dp(8), dp(8), dp(7));
        tile.setBackground(rounded(0x19FFFFFF, 18));
        tile.setFocusable(true);
        tile.setClickable(true);
        LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(dp(112), dp(100));
        if (dockTile) {
            tileParams.width = dp(118);
            tileParams.height = dp(112);
            tileParams.rightMargin = dp(11);
        }
        ImageView image = new ImageView(this);
        image.setImageDrawable(app.icon);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        tile.addView(image, new LinearLayout.LayoutParams(dp(52), dp(52)));
        TextView label = text(app.label, 11, 0xFFE9F0FA, true);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelParam = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(31));
        labelParam.topMargin = dp(3);
        tile.addView(label, labelParam);
        tile.setOnClickListener(v -> openApp(app));
        tile.setOnLongClickListener(v -> { showAppActions(app); return true; });
        decorateFocus(tile, app.label, "Press OK to open  ·  Hold OK to organize",
                app.packageName);
        if (dockTile) tile.setLayoutParams(tileParams);
        return tile;
    }

    private View actionTile(String symbol, String label, Runnable action, boolean dock) {
        LinearLayout tile = vertical();
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(8), dp(8), dp(8), dp(7));
        tile.setBackground(rounded(0x20FFFFFF, 18));
        tile.setFocusable(true);
        tile.setClickable(true);
        TextView icon = text(symbol, 33, 0xFFDAE7FC, false);
        tile.addView(icon);
        TextView caption = text(label, 11, 0xFFDEE8F6, true);
        tile.addView(caption);
        tile.setOnClickListener(v -> action.run());
        decorateFocus(tile, "Your app library", "Choose an app for your dock", "");
        if (dock) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(104), dp(112));
            lp.rightMargin = dp(8);
            tile.setLayoutParams(lp);
        }
        return tile;
    }

    private View actionButton(String label, Runnable action) {
        TextView button = text(label, 12, 0xFFDBE7F6, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(16), dp(10), dp(16), dp(10));
        button.setBackground(rounded(0x1EFFFFFF, 14));
        button.setFocusable(true);
        button.setClickable(true);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        p.rightMargin = dp(11);
        button.setLayoutParams(p);
        button.setOnClickListener(v -> action.run());
        decorateFocus(button, label, "Press OK to open", "");
        return button;
    }

    private void decorateFocus(View view, String title, String subtitle, String packageName) {
        view.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.08f : 1f).scaleY(focused ? 1.08f : 1f)
                    .setDuration(150L).start();
            v.setElevation(focused ? dp(14) : 0);
            GradientDrawable bg = (GradientDrawable) v.getBackground();
            bg.setStroke(dp(focused ? 2 : 1), focused ? THEMES_ACCENT[theme] : 0x32FFFFFF);
            bg.setColor(focused ? 0x3CFFFFFF : 0x19FFFFFF);
            if (focused) {
                if (focusTitle != null) focusTitle.setText(title);
                if (focusDescription != null) focusDescription.setText(subtitle);
                if (!packageName.isEmpty()) lastFocusedPackage = packageName;
            }
        });
    }

    private void openApp(AppEntry app) {
        try {
            Intent launch = new Intent(Intent.ACTION_MAIN);
            launch.setComponent(new ComponentName(app.packageName, app.className));
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launch);
        } catch (Exception e) {
            try {
                Intent fallback = packageManager.getLaunchIntentForPackage(app.packageName);
                if (fallback == null) throw new ActivityNotFoundException();
                startActivity(fallback);
            } catch (Exception ignored) {
                Toast.makeText(this, "Cannot open " + app.label, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showAppActions(AppEntry app) {
        boolean pinned = favoriteIds.contains(app.packageName);
        List<String> options = new ArrayList<>(Arrays.asList("Open", pinned ? "Remove from dock" : "Pin to dock"));
        if (pinned) { options.add("Move left"); options.add("Move right"); }
        options.add("App info");
        new AlertDialog.Builder(this).setTitle(app.label)
                .setItems(options.toArray(new String[0]), (d, which) -> {
                    String choice = options.get(which);
                    switch (choice) {
                        case "Open": openApp(app); break;
                        case "Pin to dock":
                            if (!favoriteIds.contains(app.packageName)) favoriteIds.add(app.packageName);
                            saveFavorites(); render(); break;
                        case "Remove from dock":
                            favoriteIds.remove(app.packageName);
                            saveFavorites(); render(); break;
                        case "Move left": shiftFavorite(app.packageName, -1); break;
                        case "Move right": shiftFavorite(app.packageName, 1); break;
                        case "App info":
                            try {
                                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:" + app.packageName)));
                            } catch (Exception ignored) { }
                            break;
                        default: break;
                    }
                }).show();
    }

    private void shiftFavorite(String pkg, int direction) {
        int current = favoriteIds.indexOf(pkg);
        int next = current + direction;
        if (current < 0 || next < 0 || next >= favoriteIds.size()) return;
        Collections.swap(favoriteIds, current, next);
        saveFavorites();
        render();
    }

    private void showAppPicker() {
        List<AppEntry> choices = new ArrayList<>();
        for (AppEntry app : installed.values()) {
            if (!favoriteIds.contains(app.packageName)) choices.add(app);
        }
        if (choices.isEmpty()) { toast("All apps are already in Favorites"); return; }
        String[] labels = new String[choices.size()];
        for (int i = 0; i < choices.size(); i++) labels[i] = choices.get(i).label;
        new AlertDialog.Builder(this).setTitle("Add to Favorites")
                .setItems(labels, (dialog, which) -> {
                    favoriteIds.add(choices.get(which).packageName);
                    saveFavorites();
                    render();
                }).setNegativeButton("Cancel", null).show();
    }

    private void showInputs() {
        try {
            TvInputManager manager = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
            if (manager == null) { toast("Use the Input button on your remote"); return; }
            List<TvInputInfo> hdmi = new ArrayList<>();
            for (TvInputInfo item : manager.getTvInputList()) {
                if (item.getType() == TvInputInfo.TYPE_HDMI) hdmi.add(item);
            }
            if (hdmi.isEmpty()) { toast("Use the Input button on your Sony remote"); return; }
            String[] names = new String[hdmi.size()];
            for (int i = 0; i < hdmi.size(); i++) {
                CharSequence name = hdmi.get(i).loadLabel(this);
                names[i] = name == null ? "HDMI " + (i+1) : name.toString();
            }
            new AlertDialog.Builder(this).setTitle("Inputs")
                    .setItems(names, (dialog, which) -> {
                        try {
                            Uri channel = TvContract.buildChannelUriForPassthroughInput(hdmi.get(which).getId());
                            Intent intent = new Intent(Intent.ACTION_VIEW, channel);
                            startActivity(intent);
                        } catch (Exception failure) {
                            toast("This Sony firmware requires the remote Input button");
                        }
                    }).setNegativeButton("Close", null).show();
        } catch (Exception failure) {
            toast("Use the Input button on your Sony remote");
        }
    }

    private void openSettings() {
        try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        catch (Exception e) { toast("Open Settings with the Sony remote"); }
    }

    private void showHomeChooser() {
        new AlertDialog.Builder(this).setTitle("Set MIX TV as Home?")
                .setMessage("The Sony launcher will stay installed. Choose MIX TV in Android Home settings if your firmware allows it. You can return to the original Home app or uninstall MIX TV at any time. No system apps will be disabled.")
                .setPositiveButton("Open Home settings", (d, w) -> {
                    try { startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)); }
                    catch (Exception e) {
                        toast("Sony may hide Home settings. MIX TV remains available as a normal app.");
                    }
                })
                .setNegativeButton("Keep current Home", null).show();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
