package com.papernotes;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;

public class EditorActivity extends Activity {

    public static final String EXTRA_PATH = "path";

    private File file;
    private EditText body;
    private TextView footer;
    private TextView dateView;
    private View appearancePanel;
    private TextView sizeLabel;
    private TextView typefaceButton;
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
        appearancePanel = findViewById(R.id.appearance_panel);
        sizeLabel = (TextView) findViewById(R.id.size_label);
        typefaceButton = (TextView) findViewById(R.id.typeface);
        applyAppearance();

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

        TextView back = (TextView) findViewById(R.id.back);
        back.setText(getString(R.string.back_to, NoteStore.folderName(this, file.getParentFile())));
        back.setOnClickListener(new View.OnClickListener() {
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
        findViewById(R.id.appearance).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean showing = appearancePanel.getVisibility() == View.VISIBLE;
                appearancePanel.setVisibility(showing ? View.GONE : View.VISIBLE);
            }
        });
        findViewById(R.id.smaller).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeTextSize(-1);
            }
        });
        findViewById(R.id.larger).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeTextSize(1);
            }
        });
        typefaceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTypefacePicker();
            }
        });
    }

    private void applyAppearance() {
        String font = Prefs.font(this);
        float size = Prefs.textSize(this);
        body.setTypeface(Fonts.load(font));
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        sizeLabel.setText(String.valueOf(Math.round(size)));
        typefaceButton.setText(Fonts.nameOf(this, font) + " ▾");
        typefaceButton.setTypeface(Fonts.load(font));
    }

    private void changeTextSize(int deltaSp) {
        Prefs.setTextSize(this, Prefs.textSize(this) + deltaSp);
        applyAppearance();
    }

    private void showTypefacePicker() {
        final List<Fonts.Font> fonts = Fonts.available(this);
        final String current = Prefs.font(this);

        TextView hint = new TextView(this);
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);
        hint.setPadding(pad + pad / 2, pad, pad + pad / 2, pad);
        hint.setTextColor(getResources().getColor(R.color.ink_soft));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        hint.setText(getString(R.string.fonts_hint, Fonts.userFontsDir(this).getAbsolutePath()));

        int selected = 0;
        for (int i = 0; i < fonts.size(); i++) {
            if (fonts.get(i).key.equals(current)) {
                selected = i;
            }
        }
        ChoiceDialog.show(this, getString(R.string.typeface), hint, selected, new ChoiceDialog.Rows() {
            @Override
            public int count() {
                return fonts.size();
            }

            @Override
            public void bind(TextView row, int position) {
                Fonts.Font font = fonts.get(position);
                boolean chosen = font.key.equals(current);
                row.setText(chosen ? font.name + "  ✓" : font.name);
                row.setTextColor(getResources().getColor(chosen ? R.color.accent : R.color.ink));
                row.setTypeface(Fonts.load(font.key));
            }

            @Override
            public void onChosen(int position) {
                Prefs.setFont(EditorActivity.this, fonts.get(position).key);
                applyAppearance();
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
