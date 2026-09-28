package com.papernotes;

import android.content.Context;
import android.graphics.Typeface;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The typefaces a note can be written in: Android's built-in families, every
 * font installed on the device in /system/fonts, and any .ttf/.otf files the
 * user copies into the Notes/Fonts folder.
 */
final class Fonts {

    static final String DEFAULT_KEY = "builtin:serif";
    private static final String SANS_KEY = "builtin:sans";
    private static final String MONO_KEY = "builtin:mono";
    private static final String FILE_PREFIX = "file:";

    private static final File SYSTEM_FONTS = new File("/system/fonts");

    // Fonts for non-Latin scripts, emoji and symbols, which can't show ordinary notes.
    private static final String[] SKIP = {
        "arabic", "armenian", "bengali", "canadian", "cherokee", "cjk", "clock", "devanagari",
        "emoji", "ethiopic", "fallback", "georgian", "gujarati", "gurmukhi", "hans", "hant",
        "hebrew", "japanese", "kannada", "khmer", "korean", "kufi", "lao", "lohit", "malayalam",
        "motoya", "mtlmr", "myanmar", "nanum", "naskh", "oriya", "padauk", "anjali", "sinhala",
        "symbol", "tamil", "telugu", "thai", "tibetan",
    };

    private static final String[] WEIGHTS = {
        "bold", "italic", "oblique", "light", "thin", "medium", "black", "heavy",
    };

    private static List<Font> systemFonts;
    private static final Map<String, Typeface> loaded = new HashMap<>();

    static final class Font {
        final String key;
        final String name;

        Font(String key, String name) {
            this.key = key;
            this.name = name;
        }
    }

    private Fonts() {}

    static synchronized List<Font> available(Context context) {
        if (systemFonts == null) {
            systemFonts = scan(SYSTEM_FONTS);
        }
        List<Font> fonts = new ArrayList<>();
        fonts.add(new Font(DEFAULT_KEY, context.getString(R.string.font_serif)));
        fonts.add(new Font(SANS_KEY, context.getString(R.string.font_sans)));
        fonts.add(new Font(MONO_KEY, context.getString(R.string.font_mono)));
        fonts.addAll(systemFonts);
        fonts.addAll(scan(userFontsDir(context)));
        return fonts;
    }

    /** Where the user can drop extra fonts; created so it's easy to find over USB. */
    static File userFontsDir(Context context) {
        File dir = new File(NoteStore.getNotesDir(context), "Fonts");
        dir.mkdirs();
        return dir;
    }

    static String nameOf(Context context, String key) {
        if (SANS_KEY.equals(key)) {
            return context.getString(R.string.font_sans);
        }
        if (MONO_KEY.equals(key)) {
            return context.getString(R.string.font_mono);
        }
        if (key != null && key.startsWith(FILE_PREFIX)) {
            return displayName(familyOf(new File(key.substring(FILE_PREFIX.length())).getName()));
        }
        return context.getString(R.string.font_serif);
    }

    /** The typeface for a key; falls back to serif if the font file is gone or unreadable. */
    static synchronized Typeface load(String key) {
        if (SANS_KEY.equals(key)) {
            return Typeface.SANS_SERIF;
        }
        if (MONO_KEY.equals(key)) {
            return Typeface.MONOSPACE;
        }
        if (key == null || !key.startsWith(FILE_PREFIX)) {
            return Typeface.SERIF;
        }
        if (!loaded.containsKey(key)) {
            Typeface typeface = null;
            File file = new File(key.substring(FILE_PREFIX.length()));
            if (file.isFile()) {
                try {
                    typeface = Typeface.createFromFile(file);
                } catch (RuntimeException e) {
                    // Not a font Android can read.
                }
            }
            loaded.put(key, typeface);
        }
        Typeface typeface = loaded.get(key);
        return typeface != null ? typeface : Typeface.SERIF;
    }

    /** One entry per font family in the folder, using its regular-weight file. */
    private static List<Font> scan(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return Collections.emptyList();
        }
        Map<String, File> families = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (File f : files) {
            String lower = f.getName().toLowerCase(Locale.US);
            if (!f.isFile() || !(lower.endsWith(".ttf") || lower.endsWith(".otf")) || isSkipped(lower)) {
                continue;
            }
            String family = familyOf(f.getName());
            if (family.length() == 0) {
                continue;
            }
            File current = families.get(family);
            if (current == null || rank(f.getName()) < rank(current.getName())) {
                families.put(family, f);
            }
        }
        List<Font> fonts = new ArrayList<>();
        for (Map.Entry<String, File> e : families.entrySet()) {
            fonts.add(new Font(FILE_PREFIX + e.getValue().getAbsolutePath(), displayName(e.getKey())));
        }
        return fonts;
    }

    private static boolean isSkipped(String lowerName) {
        for (String s : SKIP) {
            if (lowerName.contains(s)) {
                return true;
            }
        }
        return false;
    }

    /** "DroidSerif-BoldItalic.ttf" -> "DroidSerif", "Caecilia_Roman.ttf" -> "Caecilia". */
    private static String familyOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        int dash = base.indexOf('-');
        if (dash > 0) {
            base = base.substring(0, dash);
        }
        base = base.replaceAll(
                "(?i)[ _]*(bold|italic|oblique|regular|light|medium|thin|black|heavy|semibold|roman|book)+$", "");
        return base.replace('_', ' ').trim();
    }

    /** "DroidSansMono" -> "Droid Sans Mono". */
    private static String displayName(String family) {
        return family.replaceAll("([a-z])([A-Z])", "$1 $2").replaceAll("\\s+", " ").trim();
    }

    /** Lower is closer to a plain regular weight. */
    private static int rank(String fileName) {
        String lower = fileName.toLowerCase(Locale.US);
        int score = 0;
        for (String w : WEIGHTS) {
            if (lower.contains(w)) {
                score += 10;
            }
        }
        if (!lower.contains("regular") && !lower.contains("roman") && !lower.contains("book")) {
            score += 1;
        }
        return score * 1000 + fileName.length();
    }
}
