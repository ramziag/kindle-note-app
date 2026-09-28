package com.papernotes;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQUEST_STORAGE = 1;

    private final List<Note> notes = new ArrayList<>();
    private NoteAdapter adapter;
    private TextView emptyView;
    private TextView folderView;
    private Typeface noteTypeface = Typeface.SERIF;

    private static final class Note {
        File file;
        String title;
        String preview;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ListView list = (ListView) findViewById(R.id.note_list);
        emptyView = (TextView) findViewById(R.id.empty);
        folderView = (TextView) findViewById(R.id.folder_path);
        adapter = new NoteAdapter();
        list.setAdapter(adapter);
        list.setEmptyView(emptyView);

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                openEditor(notes.get(position).file);
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                confirmDelete(notes.get(position));
                return true;
            }
        });

        findViewById(R.id.new_note).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openEditor(NoteStore.newNoteFile(MainActivity.this));
            }
        });

        maybeRequestStoragePermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        noteTypeface = Fonts.load(Prefs.font(this));
        notes.clear();
        String untitled = getString(R.string.untitled);
        for (File f : NoteStore.listNotes(this)) {
            Note n = new Note();
            n.file = f;
            try {
                String text = NoteStore.read(f);
                n.title = NoteStore.titleOf(text, untitled);
                n.preview = NoteStore.previewOf(text);
            } catch (IOException e) {
                n.title = f.getName();
                n.preview = "";
            }
            notes.add(n);
        }
        adapter.notifyDataSetChanged();
        folderView.setText(getString(R.string.saved_to, NoteStore.getNotesDir(this).getAbsolutePath()));
    }

    private void openEditor(File file) {
        Intent intent = new Intent(this, EditorActivity.class);
        intent.putExtra(EditorActivity.EXTRA_PATH, file.getAbsolutePath());
        startActivity(intent);
    }

    private void confirmDelete(final Note note) {
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

    private final class NoteAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return notes.size();
        }

        @Override
        public Note getItem(int position) {
            return notes.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView != null ? convertView
                    : LayoutInflater.from(MainActivity.this).inflate(R.layout.item_note, parent, false);
            Note note = getItem(position);
            TextView title = (TextView) v.findViewById(R.id.title);
            title.setText(note.title);
            title.setTypeface(noteTypeface);
            ((TextView) v.findViewById(R.id.date)).setText(DateUtils.getRelativeTimeSpanString(
                    note.file.lastModified(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE));
            TextView preview = (TextView) v.findViewById(R.id.preview);
            preview.setText(note.preview);
            preview.setTypeface(noteTypeface);
            preview.setVisibility(note.preview.length() > 0 ? View.VISIBLE : View.GONE);
            return v;
        }
    }
}
