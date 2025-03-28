package com.megatech.fms.view;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.megatech.fms.R;
import com.megatech.fms.databinding.AirlineSelectItemBinding;
import com.megatech.fms.databinding.FlightSelectItemBinding;
import com.megatech.fms.model.AirlineModel;
import com.megatech.fms.model.FlightModel;

import java.util.ArrayList;
import java.util.List;

public class FlightArrayAdapter extends ArrayAdapter<FlightModel> {
    private List<FlightModel> originList;
    private final List<FlightModel> filteredList;

    public FlightArrayAdapter(Context context, List<FlightModel> items) {

        super(context, 0, items);
        filteredList = items;
    }

    public void setSelected(int valor) {
        int selectedIndex = valor;

    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {

        FlightModel itemData = getItem(position);
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.flight_select_item, parent, false);
        }

        convertView.setBackgroundColor(position % 2 == 0 ? Color.TRANSPARENT : Color.rgb(250, 250, 250));
        FlightSelectItemBinding binding = DataBindingUtil.bind(convertView);

        binding.setMItem(itemData);
        binding.executePendingBindings();


        return binding.getRoot();//.getView(position, convertView, parent);
    }


    @NonNull
    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence charSequence) {
                String charString = charSequence.toString();

                final FilterResults oReturn = new FilterResults();
                final List<FlightModel> results = new ArrayList<FlightModel>();
                if (originList == null) {
                    originList = new ArrayList<FlightModel>();
                    originList.addAll(filteredList);
                }

                if (!charString.isEmpty()) {


                    for (FlightModel row : originList) {

                        // name match condition. this might differ depending on your requirement
                        // here we are looking for name or phone number match
                        if ( (row.getCode() !=null &&  row.getCode().toLowerCase().contains(charString.toLowerCase())) )
                               {
                            results.add(row);
                        }
                    }
                } else
                    results.addAll(originList);

                oReturn.values = results;
                oReturn.count = results.size();
                return oReturn;

            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                filteredList.clear();
                filteredList.addAll((List<FlightModel>) results.values);
                notifyDataSetChanged();
            }
        };
    }
}
