package com.megatech.fms.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.megatech.fms.R;
import com.megatech.fms.databinding.B2504ItemBinding;
import com.megatech.fms.model.BM2504Model;

import java.util.ArrayList;
import java.util.List;

public class BM2504ArrayAdapter extends ArrayAdapter<BM2504Model> {

    private boolean[] checked;
    private List<BM2504Model> allItems;
    private List<Integer> checkedItems = new ArrayList<>();

    public BM2504ArrayAdapter(
            @NonNull Context context,
            @NonNull List<BM2504Model> objects) {

        super(context, 0, objects);
        this.allItems = objects;
        this.checked = new boolean[objects.size()];
    }

    @NonNull
    @Override
    public View getView(int position,
                        @Nullable View convertView,
                        @NonNull ViewGroup parent) {

        B2504ItemBinding binding;

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext())
                    .inflate(R.layout.b2504_item, parent, false);
        }

        binding = DataBindingUtil.bind(convertView);

        BM2504Model item = getItem(position);
        if (binding != null && item != null) {
            binding.setMItem(item);
            binding.executePendingBindings();
        }

        CheckBox cb = convertView.findViewById(R.id.b2504_item_chk);
        if (cb != null && item != null) {

            cb.setOnCheckedChangeListener(null);
            cb.setChecked(checked[position]);

            cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                checked[position] = isChecked;
                if (isChecked) {
                    checkedItems.add(item.getLocalId());
                } else {
                    checkedItems.remove((Integer) item.getLocalId());
                }
            });
        }

        return convertView;
    }

    /* =========================
       CHECKED ITEMS
     ========================= */

    public List<Integer> getCheckedItems() {
        checkedItems.clear();
        for (int i = 0; i < allItems.size(); i++) {
            if (checked[i]) {
                checkedItems.add(allItems.get(i).getLocalId());
            }
        }
        return checkedItems;
    }

    public void selectAll() {
        setAll(true);
        notifyDataSetChanged();
    }

    public void selectNone() {
        setAll(false);
        notifyDataSetChanged();
    }

    private void setAll(boolean value) {
        for (int i = 0; i < checked.length; i++) {
            checked[i] = value;
        }
    }
}
