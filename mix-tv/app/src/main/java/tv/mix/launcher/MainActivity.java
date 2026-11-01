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

    private boolean showAllApps = false;
    private View firstDockApp;

    private void render() {
        theme = Math.floorMod(prefs.getInt("theme", 0), THEMES_TOP.length);
        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        frame.addView(new AtmosphereView(this, theme), new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(frame);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        frame.addView(scroll, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout main = vertical();
        main.setPadding(dp(54), dp(29), dp(54), dp(34));
        scroll.addView(main);

        // Minimal global toolbar
        LinearLayout top = horizontal();
        TextView brand = text("◈  MIX TV", 23, 0xFFFFFFFF, true);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(brand, new LinearLayout.LayoutParams(0, dp(46), 1));
        TextView input = toolButton("INPUTS", this::showInputs);
        top.addView(input);
        TextView settings = toolButton("⚙", this::openSettings);
        LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams(dp(50), dp(42));
        settingsLp.leftMargin = dp(11);
        top.addView(settings, settingsLp);
        clock = text("", 14, 0xFFE4E8F1, true);
        clock.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams clockLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        clockLp.leftMargin = dp(24);
        top.addView(clock, clockLp);
        main.addView(top);

        // Atmospheric hero: one strong line, no clutter.
        LinearLayout hero = vertical();
        hero.setPadding(dp(7), dp(72), dp(7), dp(61));
        TextView small = text("YOUR ENTERTAINMENT SPACE", 12, 0xFFBFCCDC, true);
        small.setLetterSpacing(.20f);
        hero.addView(small);
        focusTitle = text(greeting(), 43, 0xFFFFFFFF, false);
        focusTitle.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = dp(5);
        hero.addView(focusTitle, titleLp);
        focusDescription = text("Movies  ·  Series  ·  Sports  ·  Apps", 15, 0xFFD7DCE7, false);
        LinearLayout.LayoutParams descriptionLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descriptionLp.topMargin = dp(8);
        hero.addView(focusDescription, descriptionLp);
        main.addView(hero);

        // Centered glass-like dock
        LinearLayout dock = horizontal();
        dock.setGravity(Gravity.CENTER);
        dock.setPadding(dp(18), dp(17), dp(18), dp(17));
        GradientDrawable glass = rounded(0x6C263652, 28);
        glass.setStroke(dp(1), 0x77C1D5F2);
        dock.setBackground(glass);
        main.addView(dock, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        View preferredFocus = null;
        firstDockApp = null;
        int count = 0;
        for (String pkg : favoriteIds) {
            AppEntry app = installed.get(pkg);
            if (app == null || count >= 6) continue;
            View tile = premiumTile(app);
            LinearLayout.LayoutParams tileLp = new LinearLayout.LayoutParams(dp(111), dp(96));
            tileLp.setMargins(dp(4), 0, dp(4), 0);
            dock.addView(tile, tileLp);
            if (firstDockApp == null) firstDockApp = tile;
            if (pkg.equals(lastFocusedPackage)) preferredFocus = tile;
            count++;
        }
        View allAppsButton = premiumActionTile("⁙", "ALL APPS", () -> {
            showAllApps = !showAllApps;
            render();
        });
        LinearLayout.LayoutParams allLp = new LinearLayout.LayoutParams(dp(111), dp(96));
        allLp.setMargins(dp(4), 0, dp(4), 0);
        dock.addView(allAppsButton, allLp);
        if (firstDockApp == null) firstDockApp = allAppsButton;

        // Shortcuts remain low and easy to find. Inputs gracefully fall back on Sony.
        LinearLayout quick = horizontal();
        quick.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams quickLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        quickLp.topMargin = dp(29);
        main.addView(quick, quickLp);
        quick.addView(pillButton("▣   PS5 / HDMI", this::showInputs));
        quick.addView(pillButton("✦   APPEARANCE", () -> {
            prefs.edit().putInt("theme", (theme + 1) % THEMES_TOP.length).apply();
            render();
        }));
        quick.addView(pillButton("⊞   ALL APPS", () -> {
            showAllApps = !showAllApps;
            render();
        }));
        quick.addView(pillButton("⚙   SETTINGS", this::openSettings));

        if (showAllApps) {
            LinearLayout.LayoutParams titleMargin = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            titleMargin.topMargin = dp(42);
            main.addView(sectionHeader("YOUR LIBRARY", "LONG PRESS OK TO PIN TO THE DOCK"), titleMargin);
            GridLayout grid = new GridLayout(this);
            grid.setColumnCount(6);
            LinearLayout.LayoutParams gridMargin = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            gridMargin.topMargin = dp(16);
            main.addView(grid, gridMargin);
            for (AppEntry app : installed.values()) {
                View tile = premiumTile(app);
                GridLayout.LayoutParams cell = new GridLayout.LayoutParams();
                cell.width = dp(124); cell.height = dp(109);
                cell.setMargins(dp(4), 0, dp(8), dp(14));
                grid.addView(tile, cell);
                if (app.packageName.equals(lastFocusedPackage) && preferredFocus == null) preferredFocus = tile;
            }
            if (installed.isEmpty()) main.addView(text("No installed apps found.", 16, Color.WHITE, false));
        } else {
            TextView hint = text("PRESS OK TO LAUNCH     •     HOLD OK TO ORGANIZE", 10, 0xFF96A9C4, false);
            hint.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(34));
            hintLp.topMargin = dp(14);
            main.addView(hint, hintLp);
        }

        View target = preferredFocus == null ? firstDockApp : preferredFocus;
        if (target != null) target.requestFocus();
        handler.removeCallbacks(clockRefresh);
        handler.post(clockRefresh);
    }

    private TextView toolButton(String title, Runnable action) {
        TextView v = text(title, 12, 0xFFF2F5FB, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(14), 0, dp(14), 0);
        v.setBackground(rounded(0x527887AB, 25));
        v.setFocusable(true);
        v.setOnClickListener(x -> action.run());
        decorateFocus(v, title, "Press OK to open", "");
        return v;
    }

    private TextView pillButton(String title, Runnable action) {
        TextView v = text(title, 12, 0xFFF0F3FA, true);
        v.setGravity(Gravity.CENTER);
        v.setBackground(rounded(0x7850647F, 19));
        v.setFocusable(true);
        v.setOnClickListener(x -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(176), dp(47));
        lp.setMargins(dp(7), dp(1), dp(7), dp(1));
        v.setLayoutParams(lp);
        decorateFocus(v, title, "Press OK to open", "");
        return v;
    }

    private View premiumTile(AppEntry app) {
        LinearLayout tile = vertical();
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(8), dp(9), dp(8), dp(5));
        GradientDrawable tileBg = rounded(0xB3212D45, 21);
        tileBg.setStroke(dp(1), 0x99FFFFFF);
        tile.setBackground(tileBg);
        tile.setFocusable(true);
        tile.setClickable(true);
        ImageView image = new ImageView(this);
        image.setImageDrawable(app.icon);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        tile.addView(image, new LinearLayout.LayoutParams(dp(52), dp(52)));
        TextView label = text(app.label, 11, 0xFFE9F1FF, true);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(28));
        labelLp.topMargin = dp(4);
        tile.addView(label, labelLp);
        tile.setOnClickListener(v -> openApp(app));
        tile.setOnLongClickListener(v -> { showAppActions(app); return true; });
        decorateFocus(tile, app.label, "Press OK to launch  ·  Hold OK to organize", app.packageName);
        return tile;
    }

    private View premiumActionTile(String symbol, String title, Runnable action) {
        LinearLayout tile = vertical();
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(8), dp(8), dp(8), dp(8));
        tile.setBackground(rounded(0xA3324262, 21));
        tile.setFocusable(true);
        tile.setClickable(true);
        TextView glyph = text(symbol, 36, 0xFFEDF4FF, false);
        glyph.setGravity(Gravity.CENTER);
        tile.addView(glyph);
        TextView label = text(title, 11, 0xFFE4EEFD, true);
        label.setGravity(Gravity.CENTER);
        tile.addView(label);
        tile.setOnClickListener(v -> action.run());
        decorateFocus(tile, title, "Press OK to open the app library", "");
        return tile;
    }

    private final class AtmosphereView extends View {
        private final int palette;
        private final android.graphics.Paint paint = new android.graphics.Paint(3);
        AtmosphereView(android.content.Context context, int selected) {
            super(context); palette = selected;
        }
        @Override protected void onDraw(android.graphics.Canvas c) {
            super.onDraw(c);
            int w = getWidth(), h = getHeight();
            int top = palette == 2 ? 0xFF201B43 : palette == 1 ? 0xFF1C222D : 0xFF09162C;
            int bottom = 0xFF070A14;
            paint.setShader(new android.graphics.LinearGradient(
                    0, 0, w, h, top, bottom, android.graphics.Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, paint);
            int glow = palette == 2 ? 0xA08D50C7 : palette == 1 ? 0x7097A6B5 : 0x807C78BE;
            paint.setShader(new android.graphics.RadialGradient(w * .76f, h * .31f, w * .58f,
                    new int[]{glow, 0x305D759E, 0x000B162A},
                    null, android.graphics.Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, paint);
            // Subtle horizon bands, no network wallpaper or heavy video.
            paint.setShader(new android.graphics.LinearGradient(0, h * .48f, 0, h,
                    0xA01A2847, 0xFA070B16, android.graphics.Shader.TileMode.CLAMP));
            android.graphics.Path mountain = new android.graphics.Path();
            mountain.moveTo(0, h * .73f);
            mountain.lineTo(w * .12f, h * .55f);
            mountain.lineTo(w * .22f, h * .65f);
            mountain.lineTo(w * .39f, h * .43f);
            mountain.lineTo(w * .48f, h * .55f);
            mountain.lineTo(w * .61f, h * .39f);
            mountain.lineTo(w * .73f, h * .51f);
            mountain.lineTo(w * .83f, h * .37f);
            mountain.lineTo(w, h * .63f);
            mountain.lineTo(w, h); mountain.lineTo(0, h); mountain.close();
            c.drawPath(mountain, paint);
            paint.setShader(new android.graphics.LinearGradient(0, h * .59f, 0, h,
                    0xA20E1931, 0xFF070A12, android.graphics.Shader.TileMode.CLAMP));
            android.graphics.Path near = new android.graphics.Path();
            near.moveTo(0, h * .80f);
            near.lineTo(w * .16f, h * .65f);
            near.lineTo(w * .29f, h * .76f);
            near.lineTo(w * .49f, h * .59f);
            near.lineTo(w * .67f, h * .72f);
            near.lineTo(w * .82f, h * .63f);
            near.lineTo(w, h * .79f);
            near.lineTo(w, h); near.lineTo(0, h); near.close();
            c.drawPath(near, paint);
            paint.setShader(null);
        }
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
                            if (favoriteIds.size() >= 6) { toast("Dock supports up to 6 apps"); break; }
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
        if (favoriteIds.size() >= 6) { toast("Dock supports up to 6 apps"); return; }
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
