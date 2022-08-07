package com.zeroapp.zerohfs;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SimpleAdapter;

import java.util.List;
import java.util.Map;

public class FilesAdapter extends SimpleAdapter {
    private MainActivity mainActivity;
    public FilesAdapter(Context context, List<? extends Map<String, ?>> data, int resource, String[] from, int[] to) {
        super(context, data, resource, from, to);
        mainActivity = (MainActivity) context;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = super.getView(position, convertView, parent);
        ImageView moreOptionsBtn = view.findViewById(R.id.moreOptionsBtn);
        moreOptionsBtn.setOnClickListener(v -> mainActivity.fileMoreOptions(position));
        return view;
    }
}
