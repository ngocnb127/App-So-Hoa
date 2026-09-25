package com.megatech.fms;

import androidx.fragment.app.DialogFragment;

import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.helpers.DateUtils;
import com.megatech.fms.model.BM2506Model;

import java.util.Date;
import java.util.List;

// BM 25.06/NLHK - Biên bản lấy và giao nhận mẫu
public class B2506Activity extends BMFormListActivity<BM2506Model> {

    @Override
    protected int getTitleRes() {
        return R.string.bm2506_title;
    }

    @Override
    protected String[] getHeaders() {
        return new String[]{getString(R.string.bm2506_sample_no), getString(R.string.bm2506_time),
                getString(R.string.bm2506_sample_type), getString(R.string.bm2506_flight),
                getString(R.string.bm2506_taken_by)};
    }

    @Override
    protected String[] getColumns(BM2506Model item) {
        return new String[]{item.getSampleNo(), DateUtils.formatDate(item.getTime(), "dd/MM/yyyy HH:mm"),
                item.getSampleType(), item.getFlightCode(), item.getOperatorName()};
    }

    @Override
    protected List<BM2506Model> getList(Date date) {
        return DataHelper.getBM2506List(date);
    }

    @Override
    protected void delete(int[] ids) {
        DataHelper.deleteBM2506(ids);
    }

    @Override
    protected DialogFragment createItemDialog(BM2506Model model) {
        return model == null ? new B2506NewItemFragement() : new B2506NewItemFragement(model);
    }
}
