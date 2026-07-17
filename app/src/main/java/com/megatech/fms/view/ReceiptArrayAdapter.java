package com.megatech.fms.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.megatech.fms.R;
import com.megatech.fms.databinding.ReceiptListItemBinding;
import com.megatech.fms.model.ReceiptModel;
import com.megatech.fms.model.RefuelItemData;

import java.util.ArrayList;
import java.util.List;

public class ReceiptArrayAdapter extends ArrayAdapter<ReceiptModel> {
    public ReceiptArrayAdapter(@NonNull Context context, List<ReceiptModel> objects) {
        super(context,0,objects);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ReceiptModel itemData = getItem(position);
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.receipt_list_item, parent, false);
        }
        ReceiptListItemBinding binding = DataBindingUtil.bind((convertView));
        binding.setReceiptItem(itemData);
        binding.executePendingBindings();

        return binding.getRoot();
    }

    @Override
    public int getViewTypeCount() {
        return super.getViewTypeCount();
    }
}
