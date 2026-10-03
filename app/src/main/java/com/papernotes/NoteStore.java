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
 * Notebooks are ordinary folders inside it, and may hold notebooks of their own.
 */
public final class NoteStore {

    /** Holds the user's own fonts (see {@link Fonts}), so it is never shown as a notebook. */
    static final String FONTS_FOLDER = "Fonts";

    private static final String FOLDER_NAME = "Notes";
    private static final String EXTENSION = ".txt";
    private static final String UTF8 = "UTF-8";
    // Keeps the recursive folder walks finite however deeply folders are nested.
    private static final int MAX_DEPTH = 12;

    private static File notesDir;

    /** A notebook and how deeply it is nested; the top-level Notes folder has depth 0. */
    static final class Folder {
        final File dir;
        final int depth;

        Folder(File dir, int depth) {
            this.dir = dir;
            this.depth = depth;
        }
    }

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

    static boolean isRoot(Context context, File dir) {
        return dir.equals(getNotesDir(context));
    }

    /** "Notes" for the top-level folder, otherwise the notebook's own name. */
    static String folderName(Context context, File dir) {
        return isRoot(context, dir) ? context.getString(R.string.root_title) : dir.getName();
    }

    /** The notes directly inside dir, most recently modified first. */
    public static List<File> listNotes(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return Collections.emptyList();
        }
        List<File> notes = new ArrayList<>();
        for (File f : files) {
            String name = f.getName();
            if (f.isFile() && !name.startsWith(".") && name.toLowerCase(Locale.US).endsWith(EXTENSION)) {
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

    /** The notebooks directly inside dir, A to Z. */
    static List<File> listNotebooks(Context context, File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return Collections.emptyList();
        }
        boolean root = isRoot(context, dir);
        List<File> notebooks = new ArrayList<>();
        for (File f : files) {
            String name = f.getName();
            if (f.isDirectory() && !name.startsWith(".") && !(root && name.equalsIgnoreCase(FONTS_FOLDER))) {
                notebooks.add(f);
            }
        }
        Collections.sort(notebooks, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        return notebooks;
    }

    /** The top-level folder followed by every notebook beneath it, each before its own notebooks. */
    static List<Folder> allFolders(Context context) {
        List<Folder> folders = new ArrayList<>();
        addFolders(context, getNotesDir(context), 0, folders);
        return folders;
    }

    private static void addFolders(Context context, File dir, int depth, List<Folder> out) {
        out.add(new Folder(dir, depth));
        if (depth < MAX_DEPTH) {
            for (File child : listNotebooks(context, dir)) {
                addFolders(context, child, depth + 1, out);
            }
        }
    }

    /** How many notes dir holds, counting those in its notebooks too. */
    static int countNotesWithin(Context context, File dir) {
        return countNotesWithin(context, dir, 0);
    }

    private static int countNotesWithin(Context context, File dir, int depth) {
        int count = listNotes(dir).size();
        if (depth < MAX_DEPTH) {
            for (File child : listNotebooks(context, dir)) {
                count += countNotesWithin(context, child, depth + 1);
            }
        }
        return count;
    }

    /** A fresh, not-yet-created file in dir named after the current time. */
    public static File newNoteFile(File dir) {
        String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
        return unusedFile(dir, "Note " + stamp + EXTENSION);
    }

    /** dir/name, or "name (2)", "name (3)" and so on if that is taken. */
    private static File unusedFile(File dir, String name) {
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        File file = new File(dir, name);
        for (int n = 2; file.exists(); n++) {
            file = new File(dir, base + " (" + n + ")" + extension);
        }
        return file;
    }

    /** Moves a note into another notebook, keeping its file name unless that is taken there. */
    static File moveNote(File note, File toDir) throws IOException {
        File target = unusedFile(toDir, note.getName());
        if (!note.renameTo(target)) {
            throw new IOException("Could not move " + note.getName());
        }
        return target;
    }

    /**
     * Why name can't be used for a notebook inside parent, as a string resource,
     * or 0 if it can. When renaming, existing is the notebook being renamed.
     */
    static int checkNotebookName(Context context, File parent, String name, File existing) {
        if (name.length() == 0) {
            return R.string.name_empty;
        }
        // Characters Windows and FAT storage reject, so the folders survive a trip to a computer.
        if (name.startsWith(".") || name.endsWith(".") || !name.matches("[^\\\\/:*?\"<>|\\p{Cntrl}]+")) {
            return R.string.name_invalid;
        }
        if (isRoot(context, parent) && name.equalsIgnoreCase(FONTS_FOLDER)) {
            return R.string.name_reserved;
        }
        String[] taken = parent.list();
        if (taken != null) {
            for (String t : taken) {
                // Shared storage often ignores case, so "journal" and "Journal" would collide.
                if (t.equalsIgnoreCase(name) && (existing == null || !t.equals(existing.getName()))) {
                    return R.string.name_taken;
                }
            }
        }
        return 0;
    }

    static boolean createNotebook(File parent, String name) {
        return new File(parent, name).mkdir();
    }

    static boolean renameNotebook(File dir, String newName) {
        if (newName.equals(dir.getName())) {
            return true;
        }
        File target = new File(dir.getParentFile(), newName);
        if (!newName.equalsIgnoreCase(dir.getName())) {
            return dir.renameTo(target);
        }
        // Only the capitalisation changes: go via a temporary name in case storage ignores case.
        File temp = new File(dir.getParentFile(), "." + newName + ".renaming");
        if (!dir.renameTo(temp)) {
            return false;
        }
        if (temp.renameTo(target)) {
            return true;
        }
        temp.renameTo(dir);
        return false;
    }

    /** Deletes a notebook and everything in it. */
    static boolean deleteNotebook(File dir) {
        return deleteRecursively(dir, 0);
    }

    private static boolean deleteRecursively(File file, int depth) {
        if (file.isDirectory() && depth < MAX_DEPTH && !isSymlink(file)) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child, depth + 1);
                }
            }
        }
        return file.delete();
    }

    // A linked folder is removed as a link; what it points to is never touched.
    private static boolean isSymlink(File file) {
        try {
            File inParent = new File(file.getParentFile().getCanonicalFile(), file.getName());
            return !inParent.getCanonicalFile().equals(inParent.getAbsoluteFile());
        } catch (IOException e) {
            return true;
        }
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
