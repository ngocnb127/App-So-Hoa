package com.megatech.fms.view;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.BindingAdapter;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.InverseBindingAdapter;
import androidx.databinding.InverseBindingListener;

import com.megatech.fms.B2508Activity;
import com.megatech.fms.R;
import com.megatech.fms.databinding.B2508ItemBinding;
import com.megatech.fms.model.BM2508Model;

import java.util.ArrayList;
import java.util.List;

public class BM2508ArrayAdapter extends ArrayAdapter<BM2508Model> {
    private B2508Activity activityb2508;
    public BM2508ArrayAdapter(@NonNull Activity  activity, @NonNull List<BM2508Model> objects) {
        super(activity, 0,  objects);
        allItems = objects;
        checked = new boolean[objects.size()] ;
        activityb2508 = (B2508Activity) activity;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.b2508_item, parent, false);
        }
        B2508ItemBinding binding =  DataBindingUtil.bind(convertView);

        BM2508Model  item =getItem(position);
                binding.setMItem(item);

        CheckBox cb = convertView.findViewById(R.id.b2502_item_chk);

        if (cb != null) {
            cb.setChecked(checked[position]);

            cb.setOnClickListener(v -> {
                checked[position] = cb.isChecked();
                if (cb.isChecked())
                    checkedItems.add((Integer)item.getLocalId());
                else
                    checkedItems.remove((Integer)item.getLocalId());
            });
        }
        Button btnDetail = convertView.findViewById(R.id.bm2508_form2508);
        if (btnDetail != null){
            btnDetail.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    BM2508Model model = getItem(position);
                    if(activityb2508.modelb2508 != null){
                        if(activityb2508.modelb2508.getUserSkypecSignaturePath() != null){
                            model.setUserSkypecSignaturePath(activityb2508.modelb2508.getUserSkypecSignaturePath());
                        }
                        if (activityb2508.modelb2508.getAirlineSignaturePath() != null)
                        {
                            model.setAirlineSignaturePath(activityb2508.modelb2508.getAirlineSignaturePath());
                        }
                    }
                    activityb2508.openFormDetail(model);
                }
            });
        }
        return  binding.getRoot();
    }


    @BindingAdapter("app:waterCheck")
    public static void setWaterCheck(RadioGroup view, Boolean value) {
        if (value == null) {
            view.check(R.id.radioButtonNA);
        } else if (value) {
            view.check(R.id.radioButtonP);
        } else {
            view.check(R.id.radioButtonF);
        }
    }

    @InverseBindingAdapter(attribute = "app:waterCheck", event = "app:waterCheckAttrChanged")
    public static Boolean getWaterCheck(RadioGroup view) {
        int checkedId = view.getCheckedRadioButtonId();
        if (checkedId == R.id.radioButtonP) {
            return true;
        } else if (checkedId == R.id.radioButtonF) {
            return false;
        } else {
            return null;
        }
    }

    @BindingAdapter("app:waterCheckAttrChanged")
    public static void setWaterCheckListener(RadioGroup view, final InverseBindingListener listener) {
        if (listener != null) {
            view.setOnCheckedChangeListener((group, checkedId) -> listener.onChange());
        }
    }
    boolean[] checked;
    List<BM2508Model> allItems;
    List<Integer> checkedItems = new ArrayList<>();

    public List<Integer> getCheckedItems() {
        try {
            checkedItems.clear();
            for (int i = 0; i < allItems.size(); i++)
                if (checked[i])
                    checkedItems.add(allItems.get(i).getLocalId());
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
        for (int i = 0; i < checked.length; i++) {
            checked[i] = selected;
        }
    }
}
