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
import com.megatech.fms.databinding.InvoiceItemBinding;
import com.megatech.fms.databinding.InvoicePreviewItemBinding;
import com.megatech.fms.model.ReceiptItemModel;
import com.megatech.fms.model.ReceiptItemModel;

import java.util.List;

public class ReceiptItemAdapter extends ArrayAdapter<ReceiptItemModel> {


    public ReceiptItemAdapter(@NonNull Context context, @NonNull List<ReceiptItemModel> objects) {
        super(context, 0, objects);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ReceiptItemModel itemData = getItem(position);
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.invoice_item, parent, false);
        }
//        View view = super.getView(position,convertView, parent);
//        ViewGroup.LayoutParams params = view.getLayoutParams();
//        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
//
//        view.setLayoutParams(params);

        // Perform the binding

        InvoiceItemBinding binding = DataBindingUtil.bind(convertView);

        binding.setTruckItem(itemData);
        binding.executePendingBindings();
        return binding.getRoot();
    }
}
