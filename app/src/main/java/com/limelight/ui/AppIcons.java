package com.limelight.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Path;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceManager;
import android.util.LruCache;

import com.limelight.R;

import java.util.Locale;

/**
 * Cover Glow: designed icon tiles for apps that have no real cover art, like Desktop or Steam Big
 * Picture (the host usually sends a plain default image for those). Real games keep the box art
 * from the PC unless "Icons for every app" is chosen.
 *
 * Each kind of app has its own colour and icon; the tile is a gradient in that colour with a
 * faint HUD grid, a soft light sweep and a glow behind the icon. Games without art get
 * a static landscape whose contours and colours are derived from the game name.
 */
public final class AppIcons {
    public static final String PREF_COVER_STYLE = "cover_style";
    public static final String STYLE_MISSING = "missing";
    public static final String STYLE_ALWAYS = "always";
    public static final String STYLE_NEVER = "never";

    public enum Kind {
        DESKTOP(R.drawable.ic_computer, 0xFFFF963E),
        LAUNCHER(R.drawable.ic_overlay_gamepad, 0xFF06B6D4),
        LIBRARY(R.drawable.ic_overlay_folder, 0xFF8B5CF6),
        RETRO(R.drawable.cg_app_retro, 0xFFF97316),
        MEDIA(R.drawable.ic_overlay_play, 0xFFEC4899),
        TERMINAL(R.drawable.cg_app_terminal, 0xFF22C55E),
        GAME(R.drawable.ic_overlay_gamepad, 0);

        final int icon;
        final int accent;

        Kind(int icon, int accent) {
            this.icon = icon;
            this.accent = accent;
        }
    }

    // Colours for games shown as icons, picked by name so each keeps its colour
    private static final int[] GAME_ACCENTS = {
            0xFFE11D48, 0xFFF97316, 0xFFEAB308, 0xFF22C55E, 0xFF14B8A6, 0xFF3B82F6, 0xFF8B5CF6, 0xFFEC4899
    };

    private AppIcons() {}

    public static Kind kindFor(String appName) {
        String name = appName == null ? "" : appName.toLowerCase(Locale.ROOT);
        if (has(name, "desktop", "remote", "windows")) {
            return Kind.DESKTOP;
        }
        if (has(name, "steam", "big picture", "xbox", "game pass")) {
            return Kind.LAUNCHER;
        }
        if (has(name, "playnite", "launchbox", "gog", "epic", "ubisoft", "battle.net", "ea app", "library", "launcher")) {
            return Kind.LIBRARY;
        }
        if (has(name, "retroarch", "emulat", "dolphin", "pcsx", "rpcs3", "cemu", "yuzu", "ryujinx", "ppsspp",
                "duckstation", "mame", "es-de", "emulationstation")) {
            return Kind.RETRO;
        }
        if (has(name, "youtube", "netflix", "plex", "jellyfin", "kodi", "vlc", "spotify", "twitch", "browser",
                "chrome", "firefox", "media")) {
            return Kind.MEDIA;
        }
        if (has(name, "terminal", "powershell", "cmd")) {
            return Kind.TERMINAL;
        }
        return Kind.GAME;
    }

    private static boolean has(String name, String... words) {
        for (String word : words) {
            if (name.contains(word)) {
                return true;
            }
        }
        return false;
    }

    public static int accentFor(String appName) {
        Kind kind = kindFor(appName);
        if (kind != Kind.GAME) {
            return kind.accent;
        }
        int hash = appName == null ? 0 : appName.toLowerCase(Locale.ROOT).hashCode();
        return GAME_ACCENTS[Math.floorMod(hash, GAME_ACCENTS.length)];
    }

    /** Whether this app shows an icon tile instead of the PC's cover. */
    public static boolean useIcon(Context context, String appName) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String style = prefs.getString(PREF_COVER_STYLE, STYLE_MISSING);
        if (STYLE_ALWAYS.equals(style)) {
            return true;
        }
        if (STYLE_NEVER.equals(style)) {
            return false;
        }
        return hasNoCoverArt(appName);
    }

    // The apps hosts add themselves, which come without real cover art. Matched by their exact
    // names, so games with similar words in their titles keep their covers.
    private static final String[] BUILT_IN_APPS = {
            "desktop", "virtual desktop", "remote desktop", "steam", "steam big picture", "big picture",
            "playnite", "playnite fullscreen", "terminal", "powershell"
    };

    static boolean hasNoCoverArt(String appName) {
        String name = appName == null ? "" : appName.trim().toLowerCase(Locale.ROOT);
        for (String builtIn : BUILT_IN_APPS) {
            if (name.equals(builtIn)) {
                return true;
            }
        }
        return false;
    }

    public static Drawable tile(Context context, String appName) {
        Kind kind = kindFor(appName);
        if (kind == Kind.GAME) {
            return new LandscapeCover(accentFor(appName), appName == null ? 0 : appName.hashCode());
        }
        Drawable icon = context.getDrawable(kind.icon).mutate();
        if (kind != Kind.DESKTOP) icon.setTint(Color.WHITE);
        return new Tile(icon, accentFor(appName));
    }

    static final class Tile extends Drawable {
        private final Drawable icon;
        private final int accent;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint grid = new Paint();
        private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);

        Tile(Drawable icon, int accent) {
            this.icon = icon;
            this.accent = accent;
            grid.setColor(0x14FFFFFF);
            grid.setStrokeWidth(1f);
        }

        int accent() {
            return accent;
        }

        @Override
        protected void onBoundsChange(Rect b) {
            int dark = blend(accent, Color.BLACK, 0.62f);
            fill.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom,
                    blend(accent, Color.WHITE, 0.08f), dark, Shader.TileMode.CLAMP));
            sheen.setShader(new LinearGradient(b.left, b.top, b.left + b.width() * 0.6f, b.top + b.height() * 0.6f,
                    0x22FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            float cx = b.exactCenterX(), cy = b.top + b.height() * 0.42f;
            glow.setShader(new RadialGradient(cx, cy, Math.max(1f, b.width() * 0.5f),
                    0x55FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            int size = Math.round(Math.min(b.width(), b.height()) * 0.42f);
            int left = Math.round(cx - size / 2f), top = Math.round(cy - size / 2f);
            icon.setBounds(left, top, left + size, top + size);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            canvas.drawRect(b, fill);

            // Faint HUD grid
            float step = Math.max(8f, b.width() / 8f);
            for (float x = b.left + step; x < b.right; x += step) {
                canvas.drawLine(x, b.top, x, b.bottom, grid);
            }
            for (float y = b.top + step; y < b.bottom; y += step) {
                canvas.drawLine(b.left, y, b.right, y, grid);
            }

            canvas.drawRect(b, sheen);
            canvas.drawRect(b, glow);
            icon.draw(canvas);
        }

        @Override
        public void setAlpha(int alpha) {
            fill.setAlpha(alpha);
            icon.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            fill.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }

    // Missing-cover tiles already drawn, by app name (about 0.5 MB each)
    private static final LruCache<String, Bitmap> MISSING_COVERS = new LruCache<String, Bitmap>(10 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    /** The landscape cover as a bitmap, for covers the PC doesn't have (shown with the app's name).
     *  Each app gets its own colour and its own take on the tile, so a row of them isn't all alike. */
    public static Bitmap missingCoverBitmap(String appName) {
        String key = appName == null ? "" : appName;
        Bitmap bitmap = MISSING_COVERS.get(key);
        if (bitmap == null) {
            // Box art proportions, like the covers it stands in for
            bitmap = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888);
            Drawable tile = new LandscapeCover(accentFor(appName), key.hashCode());
            tile.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
            tile.draw(new Canvas(bitmap));
            MISSING_COVERS.put(key, bitmap);
        }
        return bitmap;
    }

    /** Evicts cached generated tile bitmaps to reclaim heap memory during video streaming. */
    public static void clearCache() {
        MISSING_COVERS.evictAll();
    }

    /** Original poster for a missing game cover. The geometry stays fixed until the title changes. */
    static final class LandscapeCover extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path[] terrain = new Path[6];
        private final Path[] contours = new Path[6];
        private final int[] tones = new int[6];
        private final LinearGradient sky;
        private final int sun;
        private final float sunX, sunY, radius;
        private int alpha = 255;

        LandscapeCover(int accent, long seed) {
            java.util.Random random = new java.util.Random(seed);
            sunX = 30f + random.nextFloat() * 42f;
            sunY = 27f + random.nextFloat() * 10f;
            radius = 12f + random.nextFloat() * 9f;
            sun = blend(accent, 0xFFFFECD1, 0.54f);
            sky = new LinearGradient(0, 0, 0, 110, new int[] {
                    blend(accent, 0xFF0C1018, 0.86f), blend(accent, 0xFF282428, 0.49f), sun},
                    new float[] {0, 0.60f, 1f}, Shader.TileMode.CLAMP);
            for (int i = 0; i < terrain.length; i++) {
                Path edge = contours[i] = new Path();
                float y = 55 + i * 10;
                float phase = random.nextFloat() * 6f;
                float height = 9f - i * 0.8f;
                for (int x = -4; x <= 104; x += 3) {
                    float ridge = (float)(Math.sin(x * 0.062 + phase) +
                            0.44 * Math.sin(x * 0.17 + phase * 1.9));
                    float point = y + ridge * height + random.nextFloat() * 1.8f;
                    if (x == -4) edge.moveTo(x, point);
                    else edge.lineTo(x, point);
                }
                terrain[i] = new Path(edge);
                terrain[i].lineTo(104, 136);
                terrain[i].lineTo(-4, 136);
                terrain[i].close();
                tones[i] = blend(accent, 0xFF07090C, 0.54f + 0.085f * i);
            }
        }

        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            canvas.save();
            canvas.clipRect(b);
            canvas.translate(b.left, b.top);
            canvas.scale(b.width() / 100f, b.height() / 134f);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(sky);
            paint.setColor(Color.WHITE);
            paint.setAlpha(alpha);
            canvas.drawRect(0, 0, 100, 134, paint);
            paint.setShader(null);
            paint.setColor(sun);
            paint.setAlpha(alpha);
            canvas.drawCircle(sunX, sunY, radius, paint);
            for (int i = 0; i < terrain.length; i++) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(tones[i]);
                paint.setAlpha(alpha);
                canvas.drawPath(terrain[i], paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(0.38f);
                paint.setColor(sun);
                paint.setAlpha(alpha * (6 - i) / 16);
                canvas.drawPath(contours[i], paint);
            }
            canvas.restore();
        }

        @Override public void setAlpha(int value) {
            alpha = value;
            invalidateSelf();
        }

        @Override public void setColorFilter(ColorFilter filter) {
            paint.setColorFilter(filter);
            invalidateSelf();
        }

        @Override public int getOpacity() {
            return alpha == 255 ? PixelFormat.OPAQUE : PixelFormat.TRANSLUCENT;
        }
    }

    static int blend(int from, int to, float amount) {
        float keep = 1f - amount;
        return Color.rgb(Math.round(Color.red(from) * keep + Color.red(to) * amount),
                Math.round(Color.green(from) * keep + Color.green(to) * amount),
                Math.round(Color.blue(from) * keep + Color.blue(to) * amount));
    }
}
