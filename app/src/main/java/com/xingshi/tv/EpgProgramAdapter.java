package com.xingshi.tv;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class EpgProgramAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("HH:mm", Locale.getDefault());
    private EpgManager.Programme[] programmes = new EpgManager.Programme[0];
    private int currentIndex = -1;

    EpgProgramAdapter(Context context) {
        inflater = LayoutInflater.from(context);
    }

    void setPrograms(EpgManager.Programme[] programmes, int currentIndex) {
        this.programmes = programmes == null
                ? new EpgManager.Programme[0] : programmes;
        this.currentIndex = currentIndex;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return programmes.length;
    }

    @Override
    public Object getItem(int position) {
        return programmes[position];
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_epg_program, parent, false);
            holder = new ViewHolder();
            holder.time = (TextView) convertView.findViewById(R.id.epg_program_time);
            holder.title = (TextView) convertView.findViewById(R.id.epg_program_title);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        EpgManager.Programme programme = programmes[position];
        holder.time.setText(timeFormat.format(new Date(programme.startMs))
                + "-" + timeFormat.format(new Date(programme.stopMs)));
        holder.title.setText(programme.title);
        convertView.setActivated(position == currentIndex);
        return convertView;
    }

    private static final class ViewHolder {
        TextView time;
        TextView title;
    }
}
