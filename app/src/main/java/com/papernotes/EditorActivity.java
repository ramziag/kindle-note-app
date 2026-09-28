package com.papernotes;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.Date;

public class EditorActivity extends Activity {

    public static final String EXTRA_PATH = "path";

    private File file;
    private EditText body;
    private TextView footer;
    private TextView dateView;
    private String savedText = "";
    private boolean deleted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);

        file = new File(getIntent().getStringExtra(EXTRA_PATH));
        body = (EditText) findViewById(R.id.body);
        footer = (TextView) findViewById(R.id.footer);
        dateView = (TextView) findViewById(R.id.date);

        if (file.exists()) {
            try {
                savedText = NoteStore.read(file);
            } catch (IOException e) {
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
        body.setText(savedText);
        updateFooter();
        updateDate(file.exists() ? file.lastModified() : System.currentTimeMillis());

        if (savedText.length() == 0) {
            body.requestFocus();
            getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                            | android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        body.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                updateFooter();
            }
        });

        findViewById(R.id.back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        findViewById(R.id.delete).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmDelete();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        save();
    }

    private void save() {
        if (deleted) {
            return;
        }
        String text = body.getText().toString();
        if (text.equals(savedText) && (file.exists() || text.length() == 0)) {
            return;
        }
        if (text.trim().length() == 0) {
            // An empty page isn't worth keeping.
            NoteStore.delete(file);
            savedText = text;
            return;
        }
        try {
            NoteStore.write(file, text);
            savedText = text;
            updateDate(file.lastModified());
        } catch (IOException e) {
            Toast.makeText(this, getString(R.string.save_failed, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void confirmDelete() {
        String title = NoteStore.titleOf(body.getText().toString(), getString(R.string.untitled));
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_title)
                .setMessage(getString(R.string.delete_message, title))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        deleted = true;
                        NoteStore.delete(file);
                        finish();
                    }
                })
                .show();
    }

    private void updateFooter() {
        int words = NoteStore.wordCount(body.getText().toString());
        footer.setText("— " + words + (words == 1 ? " word" : " words") + " —");
    }

    private void updateDate(long millis) {
        dateView.setText(DateFormat.format("EEEE, d MMMM yyyy", new Date(millis)));
    }
}
