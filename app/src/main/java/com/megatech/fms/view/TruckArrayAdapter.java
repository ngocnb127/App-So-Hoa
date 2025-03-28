package com.megatech.fms.view;


import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.megatech.fms.R;
import com.megatech.fms.databinding.RefuelPreviewItemBinding;
import com.megatech.fms.model.RefuelItemData;

import java.util.ArrayList;
import java.util.Collections;

public class TruckArrayAdapter extends ArrayAdapter<RefuelItemData> {
    public TruckArrayAdapter(Context context, ArrayList<RefuelItemData> items) {

        super(context, 0, items);
        allItems = items;
        checked = new ArrayList<>(Collections.nCopies(items.size(),false)) ;
    }

    private int selectedIndex = -1;

    public void setSelected(int valor) {
        selectedIndex = valor;
        notifyDataSetChanged();
    }

    public void setSelectedObject(RefuelItemData obj) {

        selectedIndex = allItems.indexOf(obj);
        notifyDataSetChanged();
    }

    ArrayList<Boolean> checked;
    ArrayList<RefuelItemData> allItems;
    ArrayList<RefuelItemData> checkedItems = new ArrayList<>();


    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {

        RefuelItemData itemData = getItem(position);
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.refuel_preview_item, parent, false);
        }

        // Perform the binding

        RefuelPreviewItemBinding binding = DataBindingUtil.bind(convertView);

        binding.setTruckItem(itemData);
        binding.executePendingBindings();

        if (position == selectedIndex)
            convertView.setBackgroundColor(Color.LTGRAY);
        else
            convertView.setBackgroundColor(Color.TRANSPARENT);
        //convertView.setBackgroundColor(Color.LTGRAY);

        CheckBox cb = convertView.findViewById(R.id.truck_item_chk);

        if (cb != null) {
            cb.setChecked(checked.get(position));

            cb.setOnClickListener(v -> {
                checked.set(position, cb.isChecked());
                if (cb.isChecked())
                    checkedItems.add(itemData);
                else
                    checkedItems.remove(itemData);
            });
        }
        return binding.getRoot();
    }

    public ArrayList<RefuelItemData> getCheckedItems() {
        try {
            checkedItems.clear();
            for (int i = 0; i < allItems.size(); i++)
                if (checked.get(i))
                    checkedItems.add(allItems.get(i));
        }catch (Exception ex)
        {}
        return  checkedItems;
    }

    public void selectAll() {
        selectAll(true);
        notifyDataSetChanged();
    }

    public void selectNone() {
        selectAll(false);
        notifyDataSetChanged();
    }

    void selectAll(boolean selected) {
        for (int i = 0; i < checked.size(); i++) {
            checked.set(i, selected);
        }
    }

    @Override
    public void add(@Nullable RefuelItemData object) {
        super.add(object);
        checked.add(false);
    }

    @Override
    public void notifyDataSetChanged() {
        if (checked.size() < allItems.size())
            checked.add(false);
        super.notifyDataSetChanged();
    }
}
