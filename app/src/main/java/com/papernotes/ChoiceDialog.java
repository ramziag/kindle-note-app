package com.papernotes;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

/** A dialog listing choices in the app's own large serif rows; the caller fills in each row. */
final class ChoiceDialog {

    interface Rows {
        int count();

        void bind(TextView row, int position);

        void onChosen(int position);
    }

    private ChoiceDialog() {}

    /** footer may be null; scrollTo is the row to bring into view first. */
    static void show(final Activity activity, CharSequence title, View footer, int scrollTo, final Rows rows) {
        ListView list = new ListView(activity);
        list.setCacheColorHint(0);
        if (footer != null) {
            list.addFooterView(footer, null, false);
        }
        list.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return rows.count();
            }

            @Override
            public Object getItem(int position) {
                return position;
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView row = (TextView) (convertView != null ? convertView
                        : LayoutInflater.from(activity).inflate(R.layout.item_choice, parent, false));
                rows.bind(row, position);
                return row;
            }
        });

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(list)
                .setNegativeButton(R.string.cancel, null)
                .create();
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                dialog.dismiss();
                if (position < rows.count()) {
                    rows.onChosen(position);
                }
            }
        });
        list.setSelection(scrollTo);
        dialog.show();
    }
}
