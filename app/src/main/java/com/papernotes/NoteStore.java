package com.papernotes;

import android.content.Context;
import android.os.Environment;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Stores each note as a plain UTF-8 .txt file. The preferred location is a
 * "Notes" folder at the root of device storage, so the files are easy to find
 * over USB; if that is not writable we fall back to app-specific storage.
 */
public final class NoteStore {

    private static final String FOLDER_NAME = "Notes";
    private static final String EXTENSION = ".txt";
    private static final String UTF8 = "UTF-8";

    private static File notesDir;

    private NoteStore() {}

    public static synchronized File getNotesDir(Context context) {
        if (notesDir == null || !notesDir.isDirectory()) {
            notesDir = chooseNotesDir(context);
        }
        return notesDir;
    }

    private static File chooseNotesDir(Context context) {
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            File shared = new File(Environment.getExternalStorageDirectory(), FOLDER_NAME);
            if (isWritableDir(shared)) {
                return shared;
            }
            File appExternal = context.getExternalFilesDir(null);
            if (appExternal != null) {
                File dir = new File(appExternal, FOLDER_NAME);
                if (isWritableDir(dir)) {
                    return dir;
                }
            }
        }
        File internal = new File(context.getFilesDir(), FOLDER_NAME);
        internal.mkdirs();
        return internal;
    }

    private static boolean isWritableDir(File dir) {
        if (!dir.isDirectory() && !dir.mkdirs()) {
            return false;
        }
        // canWrite() is unreliable on some storage; probe with a real file.
        File probe = new File(dir, ".probe");
        try {
            new FileOutputStream(probe).close();
            probe.delete();
            return true;
        } catch (IOException | SecurityException e) {
            return false;
        }
    }

    /** All notes, most recently modified first. */
    public static List<File> listNotes(Context context) {
        File[] files = getNotesDir(context).listFiles();
        if (files == null) {
            return Collections.emptyList();
        }
        List<File> notes = new ArrayList<>();
        for (File f : files) {
            if (f.isFile() && f.getName().toLowerCase(Locale.US).endsWith(EXTENSION)) {
                notes.add(f);
            }
        }
        Collections.sort(notes, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                long diff = b.lastModified() - a.lastModified();
                return diff > 0 ? 1 : (diff < 0 ? -1 : 0);
            }
        });
        return notes;
    }

    /** A fresh, not-yet-created file named after the current time. */
    public static File newNoteFile(Context context) {
        File dir = getNotesDir(context);
        String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
        File file = new File(dir, "Note " + stamp + EXTENSION);
        int n = 2;
        while (file.exists()) {
            file = new File(dir, "Note " + stamp + " (" + n++ + ")" + EXTENSION);
        }
        return file;
    }

    public static String read(File file) throws IOException {
        InputStream in = new FileInputStream(file);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            return out.toString(UTF8);
        } finally {
            in.close();
        }
    }

    /** Writes via a temp file and rename so a crash never leaves a half-written note. */
    public static void write(File file, String text) throws IOException {
        File tmp = new File(file.getParentFile(), "." + file.getName() + ".tmp");
        OutputStream out = new FileOutputStream(tmp);
        try {
            out.write(text.getBytes(UTF8));
            out.flush();
            ((FileOutputStream) out).getFD().sync();
        } finally {
            out.close();
        }
        if (!tmp.renameTo(file)) {
            // Some filesystems refuse to rename over an existing file.
            file.delete();
            if (!tmp.renameTo(file)) {
                tmp.delete();
                throw new IOException("Could not write " + file.getName());
            }
        }
    }

    public static boolean delete(File file) {
        return file.delete();
    }

    /** First non-blank line, used as the note's title. */
    public static String titleOf(String text, String fallback) {
        for (String line : text.split("\n")) {
            String t = line.trim();
            if (t.length() > 0) {
                return t;
            }
        }
        return fallback;
    }

    /** Text after the title line, collapsed onto one line for list previews. */
    public static String previewOf(String text) {
        String trimmed = text.trim();
        int nl = trimmed.indexOf('\n');
        if (nl < 0) {
            return "";
        }
        String rest = trimmed.substring(nl + 1).trim().replaceAll("\\s+", " ");
        return rest.length() > 200 ? rest.substring(0, 200) : rest;
    }

    public static int wordCount(String text) {
        String t = text.trim();
        if (t.length() == 0) {
            return 0;
        }
        return t.split("\\s+").length;
    }
}
