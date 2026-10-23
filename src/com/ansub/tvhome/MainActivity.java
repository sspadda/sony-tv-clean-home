package com.ansub.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.TreeMap;
import java.util.List;

public class MainActivity extends Activity {

    // Top-row apps, in this order of preference:
    //  1. a list saved at install time:  am start -n io.github.sspadda.sonytvcleanhome/com.ansub.tvhome.MainActivity --es apps "pkg1,pkg2"
    //     (--es apps auto  goes back to 2; add --es stamp <unique> so it's applied only once, see saveConfig)
    //  2. apps.txt (built in as Apps.LIST), topped up with other TV apps installed on this TV.
    // Missing apps are skipped. Topping up stops at six apps, but a chosen list can be any length:
    // the apps sit in CleanFrame's glass dock, followed by the HDMI inputs, and the dock scrolls once
    // they don't all fit.
    // On the TV, hold OK on an app (or press Menu) to move or remove it; Settings adds one. That saves list 1.
    // Wallpaper, All apps and Settings sit at the top right, level with the clock. Settings shortcuts
    // can join them there, off until picked the same way:
    //   --es quick "bluetooth,sound,picture"
    // (--es quick none  takes them away again).
    private static final String PREFS = "config";
    private static final List<String> NOT_APPS = Arrays.asList("com.android.vending", "com.android.tv.settings",
            "com.android.tv", "com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx",
            "com.google.android.leanbacklauncher");
    private static final String PLAY_STORE = "com.android.vending";
    private static final String SETTINGS = "settings";
    private static final String WALLPAPER = "wallpaper";
    private static final String ALL_APPS = "all apps";
    private static final String ADD = "add";
    private static final String[] EDIT_ITEMS = {"Move", "Replace", "Remove from home", "Uninstall"};
    private static final String[] EDIT_ITEMS_SYSTEM = {"Move", "Replace", "Remove from home", "App info"};
    private static final int PER_ROW = 6, MAX_INPUTS = 4;

    // Settings shortcuts: name, label, icon. Tile keys are QUICK + name.
    private static final String QUICK = "quick:";
    private static final String[] QUICK_NAMES = {"bluetooth", "sound", "picture"};
    private static final String[] QUICK_LABELS = {"Bluetooth", "Sound", "Picture"};
    private static final int[][] QUICK_ICONS = {LineIcon.BLUETOOTH, LineIcon.SOUND, LineIcon.PICTURE};
    private static final String NETWORK = "network";
    private static final int OFFLINE = 0, ETHERNET = 4; // network states; 1 to 3 are Wi-Fi bars
    // TCL keeps its own Picture and Sound panels in a service; Android's Sound page is missing there.
    private static final String TCL_SHOW_WINDOW = "com.tcl.settings.SHOW_WINDOW";

    // Layout is CleanFrame's Dock, designed at 1920x1080 and scaled to the TV's real UI size by px().
    private static final int DOCK_X = 208, DOCK_Y = 676, DOCK_W = 1504, DOCK_H = 232, DOCK_RADIUS = 48;
    private static final int SHELF_INSET = 16, ITEM_W = 288, PLATE_TOP = 16, PLATE_H = 200, PLATE_RADIUS = 32;
    private static final int CLOCK_X = 140, CLOCK_Y = 72;
    private static final int UTIL_RIGHT = 1780, UTIL_TOP = 72, UTIL_SIZE = 64, UTIL_ICON = 36, UTIL_GAP = 16;
    private static final int RADIUS = 22; // menus and panels
    private static final float DIM = 0.84f; // marks and names until focused

    // The Settings icon opens a small menu: the home screen's own settings, then the TV's.
    private static final String[] SETTINGS_ITEMS = {"Add app to Home", "Brightness", "TV settings"};
    // Android TV 11 and later (Google TV too) has no Display page, but its "Display & Sound" page holds Picture.
    private static final String[] TV_DISPLAY_SOUND = {"com.android.tv.settings",
            "com.android.tv.settings.device.displaysound.DisplaySoundActivity"};
    // CleanFrame's photos, saved by id: id, name, and whether the photo is light (it gets a darker top
    // shade and dock veil). assets/wallpapers holds each one at 1920x1080, assets/glass a blurred copy
    // lined up with it for the dock, and assets/thumbs a small one for the list. See prepare-assets.sh.
    private static final String[][] WALLPAPERS = {
        {"misty", "Misty coast", "light"}, {"cove", "Quiet cove", "light"}, {"forest", "Forest mist", "dark"},
        {"valley", "Mountain valley", "dark"}, {"night", "Desert stars", "light"}, {"peaks", "Blue peaks", "dark"},
        {"bridge", "Bridge at dusk", "dark"}, {"ridge", "Sunlit ridges", "dark"}, {"golden", "Golden hour", "dark"},
        {"summit", "Sunlit summits", "dark"}, {"goldcoast", "Gold Coast dusk", "light"},
        {"sydney", "Sydney Harbour", "light"}, {"amber", "Amber hills", "light"}, {"mint", "Mint wave", "light"},
        {"alpine", "Alpine glow", "light"},
    };
    private float scale = 1f;

    private static Typeface regular, semibold;

    private FrameLayout root; // the page, inside a vertical scroller
    private FrameLayout dock;  // the glass panel along the bottom
    private LinearLayout shelf; // the dock's items, in a horizontal scroller
    private Bitmap photo; // the wallpaper; reused while browsing, so each preview doesn't take another 8MB
    private String lastDockKey; // where Down from the top-right icons goes back to
    private final List<View> tiles = new ArrayList<>();
    private final List<View> utils = new ArrayList<>(); // the top-right icons, left to right
    private boolean dirty = true;
    private java.util.Locale shownLocale; // the TV's language (and region) when the page was built
    private String focusedKey;
    private final List<String> rowApps = new ArrayList<>(); // the top row as shown, in order
    private String movingKey;   // the app being moved with left/right, if any
    private TextView moveHint;  // shown above the apps while moving
    private boolean longPressed; // OK was held: swallow the key-up so the app doesn't open
    // The tile OK went down on. An app that closes on OK's key-down (Netflix's "Exit") hands the matching
    // key-up to the tile still focused here, so a key-up only opens the app it went down on (#30).
    private String okDownKey;

    // Apps installed, removed or updated. This often lands once Home is already back in front (an app
    // uninstalled from the menu is gone only after Android's prompt has closed), so catch up then.
    private final BroadcastReceiver packagesChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            dirty = true;
            refreshApps();
        }
    };

    // When the TV boots, its HDMI inputs register a little after Home has started, so the first build
    // can find none. An input turning up (or going, or being renamed) later rebuilds the tiles.
    private final TvInputManager.TvInputCallback inputsChanged = new TvInputManager.TvInputCallback() {
        @Override public void onInputAdded(String id) { onInputsChanged(); }
        @Override public void onInputRemoved(String id) { onInputsChanged(); }
        @Override public void onInputUpdated(String id) { onInputsChanged(); }
        @Override public void onTvInputInfoUpdated(TvInputInfo info) { onInputsChanged(); }
    };

    private void onInputsChanged() {
        dirty = true;
        refreshApps();
    }

    // The network tile follows the connection live: Wi-Fi bars, Ethernet or Offline. Only its icon and
    // label change, so nothing is rebuilt.
    private ImageView netIcon;
    private int netState = -1;
    private final ConnectivityManager.NetworkCallback networkChanged = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(Network n) { runOnUiThread(MainActivity.this::refreshNetwork); }
        @Override public void onLost(Network n) { runOnUiThread(MainActivity.this::refreshNetwork); }
        // Also fires when the Wi-Fi signal gets stronger or weaker.
        @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities c) {
            runOnUiThread(MainActivity.this::refreshNetwork);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        saveConfig(getIntent());
        loadFonts();
        scale = getResources().getDisplayMetrics().widthPixels / 1920f;

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroller.setClipChildren(false);
        root = new FrameLayout(this);
        root.setClipChildren(false);
        scroller.addView(root, new FrameLayout.LayoutParams(-1, -2));

        // CleanFrame's clock: small, 12-hour without AM/PM, and no date.
        shownLocale = java.util.Locale.getDefault();
        TextClock time = clock("h:mm", "h:mm", px(48), 0xFFF4F4F1, regular);
        time.setLetterSpacing(-0.031f);
        time.setFontFeatureSettings("tnum");
        time.setShadowLayer(px(3), 0, px(1), 0x38000000);
        root.addView(time, at(px(CLOCK_X), px(CLOCK_Y), -2, px(64)));

        // The dock: glass, with the items in a scroller inset from its rounded ends, fading out at
        // whichever end has more to scroll to.
        dock = new FrameLayout(this);
        dock.setClipToOutline(true);
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setHorizontalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(px(132));
        shelf = new LinearLayout(this);
        scroll.addView(shelf, new FrameLayout.LayoutParams(-2, -1));
        dock.addView(scroll, at(px(SHELF_INSET), 0, px(DOCK_W - 2 * SHELF_INSET), -1));
        root.addView(dock, at(px(DOCK_X), px(DOCK_Y), px(DOCK_W), px(DOCK_H)));
        showWallpaper(wallpaper());

        moveHint = text(px(28), 0xFFF6F1E7, regular);
        moveHint.setShadowLayer(px(2), 0, px(1), 0x660E0A06);
        moveHint.setText(Strings.t("Use the arrows to move it, then press OK"));
        moveHint.setVisibility(View.GONE);
        root.addView(moveHint, at(px(DOCK_X + 32), px(DOCK_Y - 56), -2, -2));

        setContentView(scroller);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(packagesChanged, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(packagesChanged, f);

        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) try {
            if (android.os.Build.VERSION.SDK_INT >= 24) cm.registerDefaultNetworkCallback(networkChanged);
            else cm.registerNetworkCallback(new NetworkRequest.Builder().build(), networkChanged);
        } catch (Exception ignored) {}

        TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        if (tim != null) tim.registerCallback(inputsChanged, new Handler());
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packagesChanged);
        TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        if (tim != null) tim.unregisterCallback(inputsChanged);
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) try { cm.unregisterNetworkCallback(networkChanged); } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (saveConfig(intent)) buildTiles();
    }

    // Saves an app list passed with --es apps "..." and shortcuts passed with --es quick "...".
    // Returns true if it changed.
    // When Android kills the home screen in the background, it can start it again with the same old
    // intent; the stamp install.sh sends stops that from undoing changes made on the TV since.
    private boolean saveConfig(Intent intent) {
        String apps = intent == null ? null : intent.getStringExtra("apps");
        String quick = intent == null ? null : intent.getStringExtra("quick");
        if (apps == null && quick == null) return false;
        SharedPreferences prefs = getSharedPreferences(PREFS, 0);
        String stamp = intent.getStringExtra("stamp");
        if (stamp != null && stamp.equals(prefs.getString("stamp", null))) return false;
        SharedPreferences.Editor e = prefs.edit();
        if (stamp != null) e.putString("stamp", stamp);
        if (apps != null) {
            apps = apps.trim();
            if (apps.isEmpty() || apps.equalsIgnoreCase("auto")) e.remove("apps");
            else e.putString("apps", apps);
        }
        if (quick != null) {
            quick = quick.trim();
            if (quick.isEmpty() || quick.equalsIgnoreCase("none")) e.remove("quick");
            else e.putString("quick", quick);
        }
        e.apply();
        dirty = true;
        return true;
    }

    private List<String> appList(PackageManager pm) {
        List<String> out = new ArrayList<>();
        String saved = getSharedPreferences(PREFS, 0).getString("apps", null);
        String[] wanted = saved != null ? saved.split("[,\\s]+") : Apps.LIST;
        for (String p : wanted) if (!p.isEmpty() && !out.contains(p) && launchIntent(pm, p) != null) out.add(p);
        if (saved == null) {
            // Top up with other TV apps people installed (or updated), skipping launchers and system tools.
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                if (out.size() >= PER_ROW) break;
                String p = ri.activityInfo.packageName;
                int flags = ri.activityInfo.applicationInfo.flags;
                boolean stockSystem = (flags & ApplicationInfo.FLAG_SYSTEM) != 0
                        && (flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
                if (stockSystem || out.contains(p) || NOT_APPS.contains(p) || p.equals(getPackageName())) continue;
                out.add(p);
            }
        }
        return out;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Rebuild only when apps were installed, removed or updated.
        if (dirty) {
            dirty = false;
            buildTiles();
        }
        refreshNetwork();
        View f = getCurrentFocus();
        if (f == null && !tiles.isEmpty()) tiles.get(0).requestFocus();
    }

    // Back from a menu or a window on top (like the uninstall prompt): catch up on apps that changed.
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) refreshApps();
        else okDownKey = null; // an OK still held finishes in the window that took over
    }

    // Rebuilds the tiles if apps changed, but only while Home is in front with no menu open and no
    // app being moved; otherwise the next onResume, focus change or end of the move does it.
    private void refreshApps() {
        if (!dirty || !hasWindowFocus() || movingKey != null) return;
        dirty = false;
        buildTiles();
    }

    // The TV's language changed: start over so the labels and the date follow it.
    @Override
    public void onConfigurationChanged(android.content.res.Configuration c) {
        super.onConfigurationChanged(c);
        if (!java.util.Locale.getDefault().equals(shownLocale)) recreate();
    }

    @Override
    public void onBackPressed() { /* home screen: nothing to go back to */ }

    // The saved wallpaper, as an index into WALLPAPERS (Misty coast if none is saved).
    private int wallpaper() {
        String saved = getSharedPreferences(PREFS, 0).getString("wallpaper", null);
        for (int i = 0; i < WALLPAPERS.length; i++) if (WALLPAPERS[i][0].equals(saved)) return i;
        return 0;
    }

    // The photo, drawn once by the window, with CleanFrame's shade at the top (for the clock and icons)
    // and scrim at the bottom (for the dock). The dock gets its slice of the blurred copy.
    private void showWallpaper(int i) {
        String id = WALLPAPERS[i][0];
        boolean light = "light".equals(WALLPAPERS[i][2]);
        Bitmap b = decodePhoto("wallpapers/" + id + ".jpg");
        Drawable base = b != null ? new BitmapDrawable(getResources(), b) : new ColorDrawable(0xFF1D2A30);
        GradientDrawable shade = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                light ? new int[] {0x6B040A0C, 0x33040A0C, 0x00040A0C} : new int[] {0x52040A0C, 0x24040A0C, 0x00040A0C});
        GradientDrawable scrim = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x00040A0C, 0x5C040A0C, 0x85040A0C});
        LayerDrawable bg = new LayerDrawable(new Drawable[] {base, shade, scrim});
        int h = getResources().getDisplayMetrics().heightPixels;
        bg.setLayerInset(1, 0, 0, 0, h - px(300));
        bg.setLayerInset(2, 0, px(560), 0, 0);
        getWindow().setBackgroundDrawable(bg);
        dock.setBackground(Glass.dock(glassSlice(id), px(DOCK_RADIUS), light));
    }

    private Bitmap decodePhoto(String path) {
        for (int attempt = 0; attempt < 2; attempt++) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inMutable = true;
            if (attempt == 0) o.inBitmap = photo; // a different size makes this throw: try again without
            try (InputStream in = getAssets().open(path)) {
                Bitmap b = BitmapFactory.decodeStream(in, null, o);
                if (b != null) return photo = b;
            } catch (Exception ignored) {}
        }
        return null;
    }

    // Just the part of the blurred photo under the dock (the assets are at the 1920x1080 design size).
    private Bitmap glassSlice(String id) {
        try (InputStream in = getAssets().open("glass/" + id + ".jpg")) {
            return BitmapRegionDecoder.newInstance(in, false)
                    .decodeRegion(new Rect(DOCK_X, DOCK_Y, DOCK_X + DOCK_W, DOCK_Y + DOCK_H), null);
        } catch (Exception e) {
            return null;
        }
    }

    // The photos, each with a thumbnail. Moving through the list shows each one behind the panel;
    // OK keeps it, Back goes back to the one before.
    private void showWallpapers() {
        List<String> names = new ArrayList<>();
        for (String[] w : WALLPAPERS) names.add(w[1]);
        panel(Strings.t("Wallpaper"), names, pos -> {
            try (InputStream in = getAssets().open("thumbs/" + WALLPAPERS[pos][0] + ".jpg")) {
                return new BitmapDrawable(getResources(), BitmapFactory.decodeStream(in));
            }
        }, px(128), wallpaper(), this::showWallpaper, which -> {
            getSharedPreferences(PREFS, 0).edit().putString("wallpaper", WALLPAPERS[which][0]).apply();
            showWallpaper(which);
        });
    }

    private void buildTiles() {
        // While tiles are swapped out, removing the focused one would hand focus down the row (and
        // overwrite focusedKey), so hold focus off until the new tiles are in.
        String want = focusedKey;
        int wantAt = rowApps.indexOf(want); // where it was, in case the app is gone now
        root.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        for (View t : tiles) {
            View wrap = (View) t.getParent();
            ((ViewGroup) wrap.getParent()).removeView(wrap);
        }
        tiles.clear();
        utils.clear();
        rowApps.clear();
        movingKey = null;
        moveHint.setVisibility(View.GONE);

        // The dock: the Home apps in order, then the HDMI inputs. It scrolls once they don't all fit.
        PackageManager pm = getPackageManager();
        for (String pkg : appList(pm)) {
            if (launchIntent(pm, pkg) == null) continue;
            rowApps.add(pkg);
            addTile(pkg, labelOf(pkg), mark(pkg), null);
        }
        if (rowApps.isEmpty()) addTile(ADD, Strings.t("Add app"), null, LineIcon.PLUS);
        for (TvInputInfo in : hdmiInputs()) addTile(in.getId(), inputLabel(in), null, LineIcon.HDMI);

        // Quiet icons at the top right, level with the clock: any settings shortcuts picked, then
        // Wallpaper, All apps and Settings. Added last, so the first app keeps first focus; Up from the
        // dock reaches them.
        List<Integer> quick = quickList();
        int ib = px(UTIL_SIZE), ig = px(UTIL_GAP), count = quick.size() + 3;
        int qx = px(UTIL_RIGHT) - count * ib - (count - 1) * ig;
        for (int q : quick) {
            addIcon(QUICK + QUICK_NAMES[q], Strings.t(QUICK_LABELS[q]), new LineIcon(QUICK_ICONS[q]), qx);
            qx += ib + ig;
        }
        addIcon(WALLPAPER, Strings.t("Wallpaper"), new LineIcon(LineIcon.PHOTO), qx);
        addIcon(ALL_APPS, Strings.t("All apps"), new LineIcon(LineIcon.GRID), qx += ib + ig);
        addIcon(SETTINGS, Strings.t("Settings"), new LineIcon(LineIcon.SETTINGS), qx + ib + ig);

        root.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        // An app that was uninstalled hands focus to the one now in its place, as Remove does.
        if (wantAt >= 0 && !rowApps.contains(want))
            want = rowApps.isEmpty() ? ADD : rowApps.get(Math.min(wantAt, rowApps.size() - 1));
        View restore = null;
        for (View t : tiles) if (t.getTag().equals(want)) restore = t;
        if (restore != null) restore.requestFocus();
    }

    // A dock item, as in CleanFrame: a mark (or a line icon) above the name, both a little dim until
    // focused, when the glass plate shows behind them and the mark lifts slightly.
    private void addTile(final String key, String label, Drawable mark, int[] lineIcon) {
        int w = px(ITEM_W), h = px(PLATE_H);
        FrameLayout card = new FrameLayout(this);
        final Drawable plate = Glass.plate(px(PLATE_RADIUS));

        final ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setImageDrawable(lineIcon != null ? new LineIcon(lineIcon) : mark);
        FrameLayout.LayoutParams ip = lineIcon != null
                ? new FrameLayout.LayoutParams(px(60), px(60), Gravity.CENTER_HORIZONTAL)
                : new FrameLayout.LayoutParams(px(120), px(72), Gravity.CENTER_HORIZONTAL);
        ip.topMargin = lineIcon != null ? px(40) : px(34);
        card.addView(iv, ip);

        final TextView name = text(px(36), 0xFFF4F4F1, regular);
        name.setText(label);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER_HORIZONTAL);
        name.setLetterSpacing(-0.022f);
        name.setIncludeFontPadding(false);
        FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(-1, -2);
        np.topMargin = px(130);
        np.leftMargin = np.rightMargin = px(8);
        card.addView(name, np);
        iv.setAlpha(DIM);
        name.setAlpha(DIM);
        card.setFocusable(true);
        card.setTag(key);

        card.setOnFocusChangeListener((v, has) -> {
            if (has) focusedKey = lastDockKey = key;
            v.setBackground(has ? plate : null);
            iv.setAlpha(has ? 1f : DIM);
            name.setAlpha(has ? 1f : DIM);
            iv.animate().translationY(has ? -px(3) : 0).scaleX(has ? 1.04f : 1f).scaleY(has ? 1.04f : 1f)
                    .setDuration(180).start();
        });
        final boolean editable = rowApps.contains(key);
        card.setOnClickListener(v -> open(key));
        if (editable) card.setOnLongClickListener(v -> { showEditMenu(key); return true; });
        card.setOnKeyListener((v, code, ev) -> {
            boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                    || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
            boolean down = ev.getAction() == KeyEvent.ACTION_DOWN;
            if (key.equals(movingKey)) return moveKey(key, code, ok, down);
            if (editable && code == KeyEvent.KEYCODE_MENU) {
                if (!down) showEditMenu(key);
                return true;
            }
            // The dock doesn't wrap, and its ends don't hand focus up to the icons.
            int at = shelf.indexOfChild((View) v.getParent());
            if ((code == KeyEvent.KEYCODE_DPAD_LEFT && at == 0)
                    || (code == KeyEvent.KEYCODE_DPAD_RIGHT && at == shelf.getChildCount() - 1)) return true;
            if (!ok) return false;
            if (down) {
                if (ev.getRepeatCount() == 0) {
                    longPressed = false;
                    okDownKey = key;
                } else if (editable && !longPressed && (ev.isLongPress()
                        || ev.getEventTime() - ev.getDownTime() >= ViewConfiguration.getLongPressTimeout())) {
                    longPressed = true;
                    showEditMenu(key);
                }
            } else {
                // Only the key-up of an OK that went down on this tile opens the app, and not a cancelled
                // one (focus left while OK was held). View does the same for a click with isPressed().
                boolean pressed = key.equals(okDownKey) && !ev.isCanceled();
                okDownKey = null;
                if (pressed && !longPressed) open(key);
            }
            return true;
        });

        FrameLayout wrap = new FrameLayout(this);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(w, h);
        cp.topMargin = px(PLATE_TOP);
        wrap.addView(card, cp);
        shelf.addView(wrap, new LinearLayout.LayoutParams(w, px(DOCK_H)));
        tiles.add(card);
    }

    // A white brand mark from CleanFrame when there is one (assets/marks), else the app's own icon.
    private Drawable mark(String pkg) {
        try (InputStream in = getAssets().open("marks/" + pkg + ".png")) {
            return new BitmapDrawable(getResources(), BitmapFactory.decodeStream(in));
        } catch (Exception ignored) {}
        try { return getPackageManager().getApplicationIcon(pkg); }
        catch (Exception e) { return null; }
    }

    // CleanFrame's quiet controls: a bare line icon, dim until focused, then full strength with its
    // name underneath. Down goes back to the dock item that was focused last.
    private void addIcon(final String key, String label, Drawable icon, int x) {
        int b = px(UTIL_SIZE), size = px(UTIL_ICON), side = px(128);
        FrameLayout card = new FrameLayout(this);
        final ImageView iv = new ImageView(this);
        iv.setImageDrawable(icon);
        card.addView(iv, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        card.setAlpha(0.66f);
        card.setFocusable(true);
        card.setTag(key);
        card.setContentDescription(label);

        final TextView name = text(px(28), 0xFFF6F1E7, regular);
        name.setText(label);
        name.setSingleLine(true);
        name.setGravity(Gravity.CENTER_HORIZONTAL);
        name.setShadowLayer(px(2), 0, px(1), 0x660E0A06);
        name.setVisibility(View.INVISIBLE);

        card.setOnFocusChangeListener((v, has) -> {
            if (has) focusedKey = key;
            v.setAlpha(has ? 1f : 0.66f);
            name.setVisibility(has ? View.VISIBLE : View.INVISIBLE);
            iv.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).setDuration(140).start();
        });
        card.setOnClickListener(v -> open(key));
        card.setOnKeyListener((v, code, ev) -> {
            // Left and Right stay among the icons: the row's ends don't drop down into the dock.
            int at = utils.indexOf(v);
            if ((code == KeyEvent.KEYCODE_DPAD_LEFT && at == 0)
                    || (code == KeyEvent.KEYCODE_DPAD_RIGHT && at == utils.size() - 1)) return true;
            if (code != KeyEvent.KEYCODE_DPAD_DOWN) return false;
            View back = tile(lastDockKey);
            if (back == null && !rowApps.isEmpty()) back = tile(rowApps.get(0));
            if (back == null) return false;
            if (ev.getAction() == KeyEvent.ACTION_DOWN) back.requestFocus();
            return true;
        });

        // The name is wider than the icon, so the wrap reaches past it on both sides.
        FrameLayout wrap = new FrameLayout(this);
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(b, b);
        ip.leftMargin = side;
        wrap.addView(card, ip);
        FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(b + 2 * side, -2);
        np.topMargin = b + px(8);
        wrap.addView(name, np);
        root.addView(wrap, at(x - side, px(UTIL_TOP), b + 2 * side, -2));
        tiles.add(card);
        utils.add(card);
    }

    private void open(String key) {
        Intent i;
        if (ADD.equals(key)) {
            showAddMenu(-1);
            return;
        } else if (SETTINGS.equals(key)) {
            dropdown(tile(SETTINGS), SETTINGS_ITEMS, which -> {
                if (which == 0) showAddMenu(-1);
                else if (which == 1) openBrightness();
                else openTvSettings();
            });
            return;
        } else if (WALLPAPER.equals(key)) {
            showWallpapers();
            return;
        } else if (ALL_APPS.equals(key)) {
            showAllApps();
            return;
        } else if (isInput(key)) {
            openInput(key);
            return;
        } else if (NETWORK.equals(key)) {
            openNetworkSettings();
            return;
        } else if (key.startsWith(QUICK)) {
            openQuick(key.substring(QUICK.length()));
            return;
        } else {
            i = launchIntent(getPackageManager(), key);
            if (i == null) return;
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (Exception ignored) {}
    }

    // Live TV apps an HDMI tile can open, in order. Droidlogic (Amlogic) TVs ship Live TV under their
    // own package, with TvActivity as its entry point.
    private static final String[][] LIVE_TV = {{"com.android.tv", "com.android.tv.MainActivity"},
            {"com.droidlogic.android.tv", "com.android.tv.TvActivity"}};
    private static final String INPUT_PLAYER = "com.google.android.tv.inputplayer";

    // Opens an HDMI input: the stock Live TV app first, then Droidlogic's, then whatever the TV picks.
    // Each one is tried only when the one before it can't start, so a TV where the stock app already
    // worked takes exactly the same path as before. The one exception: when the TV itself sends HDMI to
    // Google TV's input player, that goes first, because some Google TVs ship com.android.tv as a stub
    // that starts fine but only says "Hello World!" (issue #5).
    private void openInput(String id) {
        Intent view = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id));
        List<Intent> tries = new ArrayList<>();
        ResolveInfo def = getPackageManager().resolveActivity(view, PackageManager.MATCH_DEFAULT_ONLY);
        if (def != null && def.activityInfo != null && INPUT_PLAYER.equals(def.activityInfo.packageName))
            tries.add(new Intent(view).setClassName(INPUT_PLAYER, def.activityInfo.name));
        for (String[] live : LIVE_TV) {
            Intent t = new Intent(view).setClassName(live[0], live[1]);
            if (t.resolveActivity(getPackageManager()) != null) tries.add(t);
        }
        tries.add(view);
        for (Intent t : tries) {
            t.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(t);
                return;
            } catch (Exception ignored) {}
        }
    }

    // The shortcuts picked with --es quick, in that order, as indexes into QUICK_NAMES. A shortcut
    // this TV has no screen for is left out, so it never shows a tile that does nothing.
    private List<Integer> quickList() {
        List<Integer> out = new ArrayList<>();
        String saved = getSharedPreferences(PREFS, 0).getString("quick", null);
        if (saved == null) return out;
        for (String name : saved.split("[,\\s]+")) {
            int q = Arrays.asList(QUICK_NAMES).indexOf(name.toLowerCase(java.util.Locale.ROOT));
            if (q < 0 || out.contains(q) || quickIntents(QUICK_NAMES[q]).isEmpty()) continue;
            out.add(q);
        }
        return out;
    }

    // OFFLINE, 1 to 3 for Wi-Fi with that many bars, or ETHERNET (any other link, which on a TV is a cable).
    private int networkState() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return OFFLINE;
        try {
            int rssi = Integer.MIN_VALUE;
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                NetworkCapabilities c = cm.getNetworkCapabilities(cm.getActiveNetwork());
                if (c == null) return OFFLINE;
                if (!c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return ETHERNET;
                if (android.os.Build.VERSION.SDK_INT >= 29) rssi = c.getSignalStrength();
            } else {
                android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
                if (ni == null || !ni.isConnected()) return OFFLINE;
                if (ni.getType() != ConnectivityManager.TYPE_WIFI) return ETHERNET;
            }
            if (rssi == Integer.MIN_VALUE) { // Android 9 and older: ask the Wi-Fi service
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null && wm.getConnectionInfo() != null) rssi = wm.getConnectionInfo().getRssi();
            }
            return 1 + WifiManager.calculateSignalLevel(rssi, 3);
        } catch (Exception e) {
            return OFFLINE;
        }
    }

    private void refreshNetwork() {
        if (netIcon == null) return;
        int state = networkState();
        if (state == netState) return;
        netState = state;
        if (state == ETHERNET) {
            netIcon.setImageDrawable(new LineIcon(LineIcon.ETHERNET));
        } else {
            // The whole Wi-Fi fan dim, with the bars it has bright on top. Offline is a slash instead.
            LineIcon fan = new LineIcon(LineIcon.WIFI);
            fan.setAlpha(0x47);
            int[] top = state == OFFLINE ? LineIcon.SLASH : state == 1 ? LineIcon.WIFI_1
                    : state == 2 ? LineIcon.WIFI_2 : LineIcon.WIFI;
            netIcon.setImageDrawable(new LayerDrawable(new Drawable[] {fan, new LineIcon(top)}));
        }
        ((View) netIcon.getParent()).setContentDescription(
                state == OFFLINE ? Strings.t("Offline") : state == ETHERNET ? "Ethernet" : "Wi-Fi");
    }

    // Wi-Fi settings (on Android TV that page lists Ethernet too), else the TV's network page, else Settings.
    private void openNetworkSettings() {
        for (String a : new String[] {Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS,
                Settings.ACTION_SETTINGS}) {
            try {
                startActivity(new Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (Exception ignored) {}
        }
    }

    // Screens a shortcut can open, best first, keeping only those this TV has.
    private List<Intent> quickIntents(String name) {
        List<Intent> all = new ArrayList<>();
        String tclType = name.equals("sound") ? "SoundPreset" : name.equals("picture") ? "picture" : null;
        if (tclType != null)
            all.add(new Intent(TCL_SHOW_WINDOW).setClassName("com.tcl.settings", "com.tcl.settings.ShowWindowService")
                    .putExtra("Type", tclType));
        switch (name) {
            case "bluetooth":
                all.add(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                all.add(new Intent("com.google.android.intent.action.CONNECT_INPUT")); // Android TV's pairing screen
                break;
            case "sound": all.add(new Intent(Settings.ACTION_SOUND_SETTINGS)); break;
            case "picture": all.add(new Intent(Settings.ACTION_DISPLAY_SETTINGS)); break;
        }
        PackageManager pm = getPackageManager();
        List<Intent> out = new ArrayList<>();
        for (Intent i : all) {
            boolean service = TCL_SHOW_WINDOW.equals(i.getAction());
            if (service ? pm.resolveService(i, 0) != null : i.resolveActivity(pm) != null) out.add(i);
        }
        return out;
    }

    // Opens a shortcut's screen: the first one that starts wins. False if none did.
    private boolean openQuick(String name) {
        for (Intent i : quickIntents(name)) {
            try {
                if (TCL_SHOW_WINDOW.equals(i.getAction())) startService(i);
                else startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    // The TV's own picture screen, where brightness lives: the Picture shortcut's screens first (TCL's
    // panel, then Android's Display page), then Android TV's Display & Sound page, else TV settings.
    // Only system screens are started, so it needs no permission and changes nothing by itself.
    private void openBrightness() {
        if (openQuick("picture")) return;
        Intent i = new Intent().setClassName(TV_DISPLAY_SOUND[0], TV_DISPLAY_SOUND[1]);
        if (i.resolveActivity(getPackageManager()) != null) {
            try {
                startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (Exception ignored) {}
        }
        openTvSettings();
    }

    private void openTvSettings() {
        try { startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception ignored) {}
    }

    interface Pick { void at(int which); }
    interface Icon { Drawable at(int which) throws Exception; }

    private void panel(String title, List<String> labels, Pick pick) {
        panel(title, labels, null, 0, -1, null, pick);
    }

    // A side panel in the home screen's own look: a title, then one row per choice, with the same
    // glass focus as the tiles. Back closes it. icon (or null) puts an icon of that size by each name.
    // With preview, the list starts on selected and moving through it calls preview; Back calls it
    // with selected again, to put things back.
    private void panel(String title, final List<String> labels, final Icon icon, final int size,
                       final int selected, final Pick preview, final Pick pick) {
        final Dialog d = new Dialog(this, android.R.style.Theme_Material_Dialog_NoActionBar);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(px(48), px(92), px(48), px(48));

        TextView head = text(px(40), Color.WHITE, semibold);
        head.setText(title);
        head.setSingleLine(true);
        head.setEllipsize(android.text.TextUtils.TruncateAt.END);
        head.setPadding(px(24), 0, px(24), 0);
        box.addView(head);

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setVerticalScrollBarEnabled(false);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        GradientDrawable focus = new GradientDrawable();
        focus.setCornerRadius(px(RADIUS));
        focus.setColor(0x38FFFFFF);
        list.setSelector(focus);
        // Icons load as rows appear, drawn small to save RAM.
        final Drawable[] icons = new Drawable[labels.size()];
        list.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return labels.size(); }
            @Override public Object getItem(int pos) { return labels.get(pos); }
            @Override public long getItemId(int pos) { return pos; }
            @Override public View getView(int pos, View convert, ViewGroup parent) {
                TextView t = convert != null ? (TextView) convert : text(px(30), 0xE6FFFFFF, regular);
                t.setText(labels.get(pos));
                t.setSingleLine(true);
                t.setEllipsize(android.text.TextUtils.TruncateAt.END);
                t.setGravity(Gravity.CENTER_VERTICAL);
                t.setMinHeight(px(88));
                t.setPadding(px(24), 0, px(24), 0);
                if (icon != null && icons[pos] == null) {
                    try { icons[pos] = icon.at(pos); }
                    catch (Exception e) { icons[pos] = new ColorDrawable(Color.TRANSPARENT); }
                    // size is the width; wallpaper thumbnails keep their 16:9.
                    int iw = icons[pos].getIntrinsicWidth(), ih = icons[pos].getIntrinsicHeight();
                    icons[pos].setBounds(0, 0, size, iw > 0 && ih > 0 ? size * ih / iw : size);
                }
                t.setCompoundDrawablesRelative(icons[pos], null, null, null);
                t.setCompoundDrawablePadding(px(24));
                return t;
            }
        });
        list.setOnItemClickListener((parent, v, pos, id) -> {
            d.dismiss();
            pick.at(pos);
        });
        if (preview != null) {
            list.setSelection(selected);
            list.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                    preview.at(pos);
                }
                @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
            });
            d.setOnCancelListener(x -> preview.at(selected));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1f);
        lp.topMargin = px(36);
        box.addView(list, lp);

        d.setContentView(box);
        Window w = d.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(preview != null ? 0xFF161113 : 0xF7161113));
        w.setLayout(px(640), -1);
        w.setGravity(Gravity.END);
        w.setDimAmount(preview != null ? 0f : 0.5f); // a preview needs the home screen undimmed
        d.show();
    }

    // A small menu that drops down under a tile, in the same glass as the tiles. Opens above the
    // tile when there's no room below. Back closes it.
    private void dropdown(View anchor, String[] items, final Pick pick) {
        final Dialog d = new Dialog(this, android.R.style.Theme_Material_Dialog_NoActionBar);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = px(10), rowH = px(64);
        box.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(RADIUS));
        bg.setColor(0xFF231D1F);
        bg.setStroke(Math.max(1, px(2)), 0x17FFFFFF);
        box.setBackground(bg);
        float widest = 0; // some languages need more room than the tile is wide
        for (int i = 0; i < items.length; i++) {
            final int which = i;
            final TextView t = text(px(26), 0xD9FFFFFF, regular);
            t.setText(Strings.t(items[i]));
            t.setSingleLine(true);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setPadding(px(22), 0, px(22), 0);
            widest = Math.max(widest, t.getPaint().measureText(t.getText().toString()) + 2 * px(22) + 2 * pad);
            final GradientDrawable focus = new GradientDrawable();
            focus.setCornerRadius(px(RADIUS - pad));
            focus.setColor(Color.TRANSPARENT);
            t.setBackground(focus);
            t.setFocusable(true);
            t.setOnFocusChangeListener((v, has) -> {
                focus.setColor(has ? 0x38FFFFFF : Color.TRANSPARENT);
                t.setTextColor(has ? Color.WHITE : 0xD9FFFFFF);
            });
            t.setOnClickListener(v -> {
                d.dismiss();
                pick.at(which);
            });
            box.addView(t, new LinearLayout.LayoutParams(-1, rowH));
        }
        d.setContentView(box);

        // Line it up with the tile as drawn (focused tiles are scaled up), below its name.
        int[] at = new int[2];
        anchor.getLocationOnScreen(at);
        int tileBottom = at[1] + Math.round(anchor.getHeight() * anchor.getScaleY());
        int w = Math.max(Math.max(Math.round(anchor.getWidth() * anchor.getScaleX()), px(320)), (int) Math.ceil(widest));
        int h = items.length * rowH + 2 * pad;
        int y = tileBottom + px(64);
        if (y + h > getResources().getDisplayMetrics().heightPixels - px(24)) y = at[1] - h - px(16);
        Window win = d.getWindow();
        win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        win.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        win.setGravity(Gravity.TOP | Gravity.START);
        android.view.WindowManager.LayoutParams lp = win.getAttributes();
        // Keep it on screen: the Settings icon sits near the right edge.
        lp.x = Math.max(px(24), Math.min(at[0], getResources().getDisplayMetrics().widthPixels - w - px(24)));
        lp.y = y;
        lp.width = w;
        lp.height = h;
        win.setAttributes(lp);
        d.show();
    }

    // Hold OK (or Menu) on a top-row app: move it, swap it for another, or take it off the home
    // screen. Removing only takes the tile away: the app stays installed and can be added back.
    // Apps the user installed can also be uninstalled; Android asks to confirm, and the tile goes
    // once the app is gone. Apps that came with the TV can't be, so they get App info instead,
    // where Android offers to turn them off.
    private void showEditMenu(final String key) {
        final int index = rowApps.indexOf(key);
        if (index < 0) return;
        final boolean user = userInstalled(key);
        dropdown(tile(key), user ? EDIT_ITEMS : EDIT_ITEMS_SYSTEM, which -> {
            if (which == 0) startMove(key);
            else if (which == 1) showAddMenu(index);
            else if (which == 3) {
                Uri pkg = Uri.parse("package:" + key);
                try { startActivity(user ? new Intent(Intent.ACTION_DELETE, pkg)
                        : new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)); }
                catch (Exception ignored) {}
            } else {
                rowApps.remove(index);
                saveRow();
                focusedKey = rowApps.isEmpty() ? ADD : rowApps.get(Math.min(index, rowApps.size() - 1));
                buildTiles();
            }
        });
    }

    // Every launchable app that isn't on the top row yet, by name. The pick is added at the end,
    // or takes the place of the app at replaceIndex (-1 to add).
    private void showAddMenu(final int replaceIndex) {
        final PackageManager pm = getPackageManager();
        TreeMap<String, String> byLabel = launchableApps(true);
        if (byLabel.isEmpty()) {
            panel(Strings.t("Every app is already here"), Collections.singletonList("OK"), which -> {});
            return;
        }
        final List<String> pkgs = new ArrayList<>(byLabel.values());
        String title = replaceIndex < 0 ? Strings.t("Add an app")
                : String.format(Strings.t("Replace %s"), labelOf(rowApps.get(replaceIndex)));
        panel(title, new ArrayList<>(byLabel.keySet()), pos -> fit(pm.getApplicationIcon(pkgs.get(pos)), px(48), px(48)),
                px(48), -1, null, which -> {
            if (replaceIndex < 0) rowApps.add(pkgs.get(which));
            else rowApps.set(replaceIndex, pkgs.get(which));
            saveRow();
            focusedKey = pkgs.get(which);
            buildTiles();
        });
    }

    // Every launchable app, by name, Play Store included: OK opens it, like CleanFrame's All apps.
    private void showAllApps() {
        final PackageManager pm = getPackageManager();
        TreeMap<String, String> byLabel = launchableApps(false);
        final List<String> pkgs = new ArrayList<>(byLabel.values());
        panel(Strings.t("All apps"), new ArrayList<>(byLabel.keySet()),
                pos -> fit(pm.getApplicationIcon(pkgs.get(pos)), px(48), px(48)), px(48), -1, null,
                which -> open(pkgs.get(which)));
    }

    // Apps with a launcher entry, by name (the TV's own home screens and Settings left out).
    // skipHome leaves out the ones already in the dock.
    private TreeMap<String, String> launchableApps(boolean skipHome) {
        PackageManager pm = getPackageManager();
        TreeMap<String, String> byLabel = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String category : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String p = ri.activityInfo.packageName;
                if ((skipHome && rowApps.contains(p)) || byLabel.containsValue(p) || p.equals(getPackageName())
                        || (NOT_APPS.contains(p) && !p.equals(PLAY_STORE))) continue;
                String label = labelOf(p);
                if (!byLabel.containsKey(label)) byLabel.put(label, p);
            }
        }
        return byLabel;
    }

    private String labelOf(String pkg) {
        PackageManager pm = getPackageManager();
        try { return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(); }
        catch (Exception e) { return pkg; }
    }

    // Saves the top row as the app list, so it no longer follows apps.txt.
    private void saveRow() {
        getSharedPreferences(PREFS, 0).edit().putString("apps", android.text.TextUtils.join(",", rowApps)).apply();
    }

    private View tile(String key) {
        for (View t : tiles) if (t.getTag().equals(key)) return t;
        return null;
    }

    // Move mode: the item gets an outline, Left and Right move it along the dock, OK or Back finishes.
    private void startMove(String key) {
        View card = tile(key);
        if (card == null) return;
        movingKey = key;
        GradientDrawable outline = new GradientDrawable();
        outline.setCornerRadius(px(PLATE_RADIUS));
        outline.setStroke(px(4), Color.WHITE);
        ((FrameLayout) card).setForeground(outline);
        moveHint.setVisibility(View.VISIBLE);
        card.requestFocus();
    }

    private boolean moveKey(String key, int code, boolean ok, boolean down) {
        int step = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0;
        if (step != 0) {
            int from = rowApps.indexOf(key), to = from + step;
            if (down && to >= 0 && to < rowApps.size()) {
                // Swap it with its neighbour (the apps come first in the dock): no rebuild, so it stays
                // quick on slow TVs, and the moving item keeps focus because only the neighbour moves.
                rowApps.add(to, rowApps.remove(from));
                View other = shelf.getChildAt(to);
                shelf.removeView(other);
                shelf.addView(other, from);
                // Scroll along with it once it has moved.
                final View card = tile(key);
                card.post(() -> card.requestRectangleOnScreen(
                        new android.graphics.Rect(0, 0, card.getWidth(), card.getHeight())));
            }
            return true;
        }
        if (ok || code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_MENU) {
            if (!down) {
                movingKey = null;
                longPressed = false;
                ((FrameLayout) tile(key)).setForeground(null);
                moveHint.setVisibility(View.GONE);
                saveRow();
                refreshApps();
            }
            return true;
        }
        return true; // up/down and everything else stay put while moving
    }

    private static boolean isInput(String key) {
        return key.indexOf('/') > 0;
    }

    // The TV's own HDMI inputs (not devices behind them), in order, skipping hidden ones.
    private List<TvInputInfo> hdmiInputs() {
        List<TvInputInfo> out = new ArrayList<>();
        TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return out;
        for (TvInputInfo in : tim.getTvInputList()) {
            if (in.getType() == TvInputInfo.TYPE_HDMI && in.getParentId() == null && !in.isHidden(this)) out.add(in);
        }
        Collections.sort(out, (a, b) -> a.getId().compareTo(b.getId()));
        return out.size() > MAX_INPUTS ? out.subList(0, MAX_INPUTS) : out;
    }

    // The name set in Settings > Inputs (like "Bedroom"), else the TV's own name (like "HDMI 1").
    private String inputLabel(TvInputInfo in) {
        CharSequence custom = in.loadCustomLabel(this);
        if (custom != null && custom.length() > 0) return withoutDeviceType(custom.toString());
        CharSequence label = in.loadLabel(this);
        return label != null && label.length() > 0 ? withoutDeviceType(label.toString()) : "HDMI";
    }

    // Philips stores a name as "name&DEVICE_TYPE" (like "Computer&COMPUTER"), so show the name only.
    // Only on Philips TVs: elsewhere a name like "AUDIO&VIDEO" is the name itself.
    private static String withoutDeviceType(String label) {
        if (!"Philips".equalsIgnoreCase(android.os.Build.MANUFACTURER)) return label;
        int amp = label.lastIndexOf('&');
        return amp > 0 && label.substring(amp + 1).matches("[A-Z][A-Z0-9_]{2,}") ? label.substring(0, amp).trim() : label;
    }

    private boolean userInstalled(String pkg) {
        try { return (getPackageManager().getApplicationInfo(pkg, 0).flags & ApplicationInfo.FLAG_SYSTEM) == 0; }
        catch (PackageManager.NameNotFoundException e) { return false; }
    }

    private static Intent launchIntent(PackageManager pm, String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        return i != null ? i : pm.getLaunchIntentForPackage(pkg);
    }

    // CleanFrame's Manrope, at fixed weights (see prepare-assets.sh), else Roboto.
    private void loadFonts() {
        if (regular != null) return;
        regular = fromAsset("fonts/manrope-medium.ttf", Typeface.create("sans-serif", Typeface.NORMAL));
        semibold = fromAsset("fonts/manrope-semibold.ttf", Typeface.create("sans-serif-medium", Typeface.NORMAL));
    }

    private Typeface fromAsset(String path, Typeface fallback) {
        try { return Typeface.createFromAsset(getAssets(), path); }
        catch (Exception e) { return fallback; }
    }

    // Draws a banner once into a tile-sized bitmap (center-cropped). Some apps ship huge banners
    // that Android 9 refuses to draw ("Canvas: trying to draw too large bitmap"); this also saves RAM.
    private Drawable fit(Drawable d, int w, int h) {
        try {
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            int iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
            if (iw <= 0 || ih <= 0) { iw = w; ih = h; }
            float s = Math.max((float) w / iw, (float) h / ih);
            int dw = Math.round(iw * s), dh = Math.round(ih * s);
            d.setBounds((w - dw) / 2, (h - dh) / 2, (w - dw) / 2 + dw, (h - dh) / 2 + dh);
            d.draw(new Canvas(b));
            return new BitmapDrawable(getResources(), b);
        } catch (Throwable t) {
            return d;
        }
    }

    private int px(int v) {
        return Math.round(v * scale);
    }

    private TextClock clock(String f12, String f24, int px, int color, Typeface tf) {
        TextClock c = new TextClock(this);
        c.setFormat12Hour(f12);
        c.setFormat24Hour(f24);
        c.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        c.setTextColor(color);
        c.setTypeface(tf);
        return c;
    }

    private TextView text(int px, int color, Typeface tf) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        t.setTextColor(color);
        if (tf != null) t.setTypeface(tf);
        return t;
    }

    private static FrameLayout.LayoutParams at(int x, int y, int w, int h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
        lp.leftMargin = x;
        lp.topMargin = y;
        return lp;
    }
}
