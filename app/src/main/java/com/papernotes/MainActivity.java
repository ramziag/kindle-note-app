package com.papernotes;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.format.DateUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shows one folder: the top-level Notes folder or a notebook, with its notebooks above its notes. */
public class MainActivity extends Activity {

    /** Absolute path of the notebook to show; the top-level Notes folder when absent. */
    public static final String EXTRA_DIR = "dir";

    private static final int REQUEST_STORAGE = 1;
    private static final int MAX_NAME_LENGTH = 60;
    private static final int TYPE_NOTEBOOK = 0;
    private static final int TYPE_NOTE = 1;

    // Muted book-cloth colours for notebook spines; each notebook's name picks one.
    private static final int[] SPINE_COLORS = {
        0xFF8B3A2B, 0xFF3F5E4A, 0xFF2F4A6B, 0xFFA0782C, 0xFF5E3A5C, 0xFF4E5B63, 0xFF7A4E2D,
    };

    private final List<Entry> entries = new ArrayList<>();
    private EntryAdapter adapter;
    private File dir;
    private boolean isRoot;
    private TextView folderView;
    private Typeface noteTypeface = Typeface.SERIF;

    /** A row in the list: a notebook or a note. */
    private static final class Entry {
        final File file;
        final boolean notebook;
        String title;
        String detail;  // a note's preview, or what a notebook holds
        long modified;  // 0 when there is nothing to date

        Entry(File file, boolean notebook) {
            this.file = file;
            this.notebook = notebook;
        }
    }

    private interface NameCallback {
        /** Returns false if the name couldn't be used after all. */
        boolean onName(String name);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        File root = NoteStore.getNotesDir(this);
        String path = getIntent().getStringExtra(EXTRA_DIR);
        dir = path != null ? new File(path) : root;
        isRoot = dir.equals(root);

        ((TextView) findViewById(R.id.title)).setText(NoteStore.folderName(this, dir));
        TextView emptyView = (TextView) findViewById(R.id.empty);
        emptyView.setText(isRoot ? R.string.empty_notes : R.string.empty_notebook);
        folderView = (TextView) findViewById(R.id.folder_path);

        if (!isRoot) {
            TextView back = (TextView) findViewById(R.id.back);
            back.setText(getString(R.string.back_to, NoteStore.folderName(this, dir.getParentFile())));
            back.setVisibility(View.VISIBLE);
            back.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        ListView list = (ListView) findViewById(R.id.note_list);
        adapter = new EntryAdapter();
        list.setAdapter(adapter);
        list.setEmptyView(emptyView);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Entry entry = entries.get(position);
                if (entry.notebook) {
                    openNotebook(entry.file);
                } else {
                    openEditor(entry.file);
                }
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                Entry entry = entries.get(position);
                if (entry.notebook) {
                    showNotebookOptions(entry);
                } else {
                    showNoteOptions(entry);
                }
                return true;
            }
        });

        findViewById(R.id.new_note).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openEditor(NoteStore.newNoteFile(dir));
            }
        });
        findViewById(R.id.new_notebook).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                promptForName(R.string.new_notebook_title, R.string.create, dir, null, new NameCallback() {
                    @Override
                    public boolean onName(String name) {
                        if (!NoteStore.createNotebook(dir, name)) {
                            return false;
                        }
                        reload();
                        return true;
                    }
                });
            }
        });

        if (isRoot) {
            maybeRequestStoragePermission();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isRoot && !dir.isDirectory()) {
            // Renamed or removed while we were away, e.g. over USB.
            finish();
            return;
        }
        reload();
    }

    private void reload() {
        noteTypeface = Fonts.load(Prefs.font(this));
        entries.clear();
        for (File notebook : NoteStore.listNotebooks(this, dir)) {
            List<File> notes = NoteStore.listNotes(notebook);
            Entry entry = new Entry(notebook, true);
            entry.title = notebook.getName();
            entry.detail = describeContents(notes.size(), NoteStore.listNotebooks(this, notebook).size());
            entry.modified = notes.isEmpty() ? 0 : notes.get(0).lastModified();
            entries.add(entry);
        }
        String untitled = getString(R.string.untitled);
        for (File f : NoteStore.listNotes(dir)) {
            Entry entry = new Entry(f, false);
            try {
                String text = NoteStore.read(f);
                entry.title = NoteStore.titleOf(text, untitled);
                entry.detail = NoteStore.previewOf(text);
            } catch (IOException e) {
                entry.title = f.getName();
                entry.detail = "";
            }
            entry.modified = f.lastModified();
            entries.add(entry);
        }
        adapter.notifyDataSetChanged();
        folderView.setText(getString(R.string.saved_to, dir.getAbsolutePath()));
    }

    /** "2 notebooks · 12 notes", "1 note", "Empty" and so on. */
    private String describeContents(int notes, int notebooks) {
        if (notes == 0 && notebooks == 0) {
            return getString(R.string.notebook_empty);
        }
        String noteCount = getResources().getQuantityString(R.plurals.note_count, notes, notes);
        if (notebooks == 0) {
            return noteCount;
        }
        String notebookCount = getResources().getQuantityString(R.plurals.notebook_count, notebooks, notebooks);
        return notes == 0 ? notebookCount : notebookCount + " · " + noteCount;
    }

    private void openNotebook(File notebook) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(EXTRA_DIR, notebook.getAbsolutePath());
        startActivity(intent);
    }

    private void openEditor(File file) {
        Intent intent = new Intent(this, EditorActivity.class);
        intent.putExtra(EditorActivity.EXTRA_PATH, file.getAbsolutePath());
        startActivity(intent);
    }

    private void showNoteOptions(final Entry note) {
        new AlertDialog.Builder(this)
                .setTitle(note.title)
                .setItems(new CharSequence[] {getString(R.string.move_to), getString(R.string.delete)},
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (which == 0) {
                                    showMovePicker(note);
                                } else {
                                    confirmDeleteNote(note);
                                }
                            }
                        })
                .show();
    }

    private void showNotebookOptions(final Entry notebook) {
        new AlertDialog.Builder(this)
                .setTitle(notebook.title)
                .setItems(new CharSequence[] {getString(R.string.rename), getString(R.string.delete)},
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (which == 0) {
                                    renameNotebook(notebook);
                                } else {
                                    confirmDeleteNotebook(notebook);
                                }
                            }
                        })
                .show();
    }

    /** Every notebook, indented by nesting with the note's current one ticked, then "+ New notebook…". */
    private void showMovePicker(final Entry note) {
        final List<NoteStore.Folder> folders = NoteStore.allFolders(this);
        int current = 0;
        for (int i = 0; i < folders.size(); i++) {
            if (folders.get(i).dir.equals(dir)) {
                current = i;
            }
        }
        final int here = current;
        final int indent = dp(24);
        ChoiceDialog.show(this, getString(R.string.move_title, note.title), null, here, new ChoiceDialog.Rows() {
            @Override
            public int count() {
                return folders.size() + 1;
            }

            @Override
            public void bind(TextView row, int position) {
                int depth = 0;
                if (position == folders.size()) {
                    row.setText(R.string.new_notebook_choice);
                    row.setTextColor(getResources().getColor(R.color.accent));
                } else {
                    NoteStore.Folder folder = folders.get(position);
                    String name = NoteStore.folderName(MainActivity.this, folder.dir);
                    row.setText(position == here ? name + "  ✓" : name);
                    row.setTextColor(getResources().getColor(position == here ? R.color.accent : R.color.ink));
                    depth = folder.depth;
                }
                row.setPadding(indent + depth * indent, row.getPaddingTop(), row.getPaddingRight(),
                        row.getPaddingBottom());
            }

            @Override
            public void onChosen(int position) {
                if (position == folders.size()) {
                    final File root = NoteStore.getNotesDir(MainActivity.this);
                    promptForName(R.string.new_notebook_title, R.string.create, root, null, new NameCallback() {
                        @Override
                        public boolean onName(String name) {
                            if (!NoteStore.createNotebook(root, name)) {
                                return false;
                            }
                            moveNote(note, new File(root, name));
                            return true;
                        }
                    });
                } else if (position != here) {
                    moveNote(note, folders.get(position).dir);
                }
            }
        });
    }

    private void moveNote(Entry note, File toDir) {
        try {
            NoteStore.moveNote(note.file, toDir);
        } catch (IOException e) {
            Toast.makeText(this, R.string.move_failed, Toast.LENGTH_LONG).show();
        }
        reload();
    }

    private void renameNotebook(final Entry notebook) {
        promptForName(R.string.rename_notebook_title, R.string.rename, dir, notebook.file, new NameCallback() {
            @Override
            public boolean onName(String name) {
                if (!NoteStore.renameNotebook(notebook.file, name)) {
                    return false;
                }
                reload();
                return true;
            }
        });
    }

    private void confirmDeleteNote(final Entry note) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_title)
                .setMessage(getString(R.string.delete_message, note.title))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        NoteStore.delete(note.file);
                        reload();
                    }
                })
                .show();
    }

    private void confirmDeleteNotebook(final Entry notebook) {
        int notes = NoteStore.countNotesWithin(this, notebook.file);
        String message = notes == 0
                ? getString(R.string.delete_notebook_message, notebook.title)
                : getString(R.string.delete_notebook_message_count, notebook.title,
                        getResources().getQuantityString(R.plurals.note_count, notes, notes));
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_notebook_title)
                .setMessage(message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (!NoteStore.deleteNotebook(notebook.file)) {
                            Toast.makeText(MainActivity.this,
                                    getString(R.string.delete_notebook_failed, notebook.title),
                                    Toast.LENGTH_LONG).show();
                        }
                        reload();
                    }
                })
                .show();
    }

    /**
     * Asks for a notebook name inside parent and keeps asking until it's usable.
     * existing is the notebook being renamed, or null when making a new one.
     */
    private void promptForName(int titleRes, int actionRes, final File parent, final File existing,
            final NameCallback callback) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setFilters(new InputFilter[] {new InputFilter.LengthFilter(MAX_NAME_LENGTH)});
        input.setHint(R.string.notebook_name_hint);
        if (existing != null) {
            input.setText(existing.getName());
            input.setSelection(input.length());
        }
        FrameLayout frame = new FrameLayout(this);
        frame.setPadding(dp(20), dp(12), dp(20), 0);
        frame.addView(input);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(titleRes)
                .setView(frame)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(actionRes, null)
                .create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();

        // Set after show() so a bad name leaves the dialog open instead of closing it.
        final View action = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        action.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = input.getText().toString().trim();
                int problem = NoteStore.checkNotebookName(MainActivity.this, parent, name, existing);
                if (problem != 0) {
                    input.setError(getString(problem));
                } else if (callback.onName(name)) {
                    dialog.dismiss();
                } else {
                    input.setError(getString(R.string.name_failed));
                }
            }
        });
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean enterDown = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && event.getAction() == KeyEvent.ACTION_DOWN;
                if (actionId == EditorInfo.IME_ACTION_DONE || enterDown) {
                    action.performClick();
                    return true;
                }
                return false;
            }
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int spineColor(String name) {
        return SPINE_COLORS[(name.toLowerCase(Locale.US).hashCode() & 0x7fffffff) % SPINE_COLORS.length];
    }

    // Fire OS 3 grants storage access at install time; this only matters on newer Android.
    @TargetApi(23)
    private void maybeRequestStoragePermission() {
        if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT < 30
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_STORAGE) {
            reload();
        }
    }

    private final class EntryAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return entries.size();
        }

        @Override
        public Entry getItem(int position) {
            return entries.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            return entries.get(position).notebook ? TYPE_NOTEBOOK : TYPE_NOTE;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Entry entry = getItem(position);
            View v = convertView != null ? convertView : LayoutInflater.from(MainActivity.this)
                    .inflate(entry.notebook ? R.layout.item_notebook : R.layout.item_note, parent, false);
            TextView title = (TextView) v.findViewById(R.id.title);
            title.setText(entry.title);
            title.setTypeface(noteTypeface);
            ((TextView) v.findViewById(R.id.date)).setText(entry.modified == 0 ? ""
                    : DateUtils.getRelativeTimeSpanString(entry.modified, System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE));
            if (entry.notebook) {
                ((TextView) v.findViewById(R.id.detail)).setText(entry.detail);
                GradientDrawable spine = new GradientDrawable();
                spine.setColor(spineColor(entry.title));
                spine.setCornerRadius(dp(2));
                v.findViewById(R.id.spine).setBackground(spine);
            } else {
                TextView preview = (TextView) v.findViewById(R.id.preview);
                preview.setText(entry.detail);
                preview.setTypeface(noteTypeface);
                preview.setVisibility(entry.detail.length() > 0 ? View.VISIBLE : View.GONE);
            }
            return v;
        }
    }
}
