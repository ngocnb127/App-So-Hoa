package com.megatech.fms;

import android.view.View;

import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.model.BM2506Model;
import com.megatech.fms.model.UserModel;

import java.util.Date;
import java.util.List;

public class B2506NewItemFragement extends BMFormItemFragment<BM2506Model> {

    public B2506NewItemFragement() {
        super(new BM2506Model());
        FMSApplication app = FMSApplication.getApplication();
        model.setDate(new Date());
        model.setTruckId(app.getTruckId());
    }

    public B2506NewItemFragement(BM2506Model model) {
        super(model);
    }

    @Override
    protected int getLayoutRes() {
        return R.layout.b2506_new;
    }

    @Override
    protected void bindViews(View view) {
        // 3. Người lấy mẫu: mặc định họ tên user đăng nhập (giống web)
        if (model.getTakenBy() == null && getActivity() instanceof DateBaseActivity) {
            FMSApplication app = FMSApplication.getApplication();
            List<UserModel> users = ((DateBaseActivity) getActivity()).userList;
            String name = app.getUser().getUserName();
            if (users != null)
                for (UserModel user : users)
                    if (user.getId() == app.getUser().getUserId())
                        name = user.getName();
            model.setTakenBy(name);
            refresh();
        }
    }

    @Override
    protected void onFieldClick(int id) {
        if (id == R.id.b2506_sample_no)
            editText(R.string.bm2506_sample_no, model.getSampleNo(), model::setSampleNo);
        else if (id == R.id.b2506_time)
            pickTime(model.getDate(), model::setDate);
        else if (id == R.id.b2506_taken_by)
            editText(R.string.bm2506_taken_by, model.getTakenBy(), model::setTakenBy);
        else if (id == R.id.b2506_place)
            editText(R.string.bm2506_place, model.getPlace(), model::setPlace);
        else if (id == R.id.b2506_location)
            editText(R.string.bm2506_location, model.getLocation(), model::setLocation);
        else if (id == R.id.b2506_sample_type)
            chooseText(R.string.bm2506_sample_type, BM2506Model.SAMPLE_TYPES, model.getSampleType(), model::setSampleType);
        else if (id == R.id.b2506_grade)
            editText(R.string.bm2506_grade, model.getGrade(), model::setGrade);
        else if (id == R.id.b2506_flight)
            chooseFlight(R.string.bm2506_flight, model.getFlightNo(), flight -> {
                model.setFlightId(flight.getId());
                model.setFlightNo(flight.getFlightCode());
            }, flightNo -> {
                model.setFlightId(null);
                model.setFlightNo(flightNo);
            });
        else if (id == R.id.b2506_retention_seal)
            editText(R.string.bm2506_retention_seal, model.getRetentionSealNo(), model::setRetentionSealNo);
        else if (id == R.id.b2506_delivery_seal)
            editText(R.string.bm2506_delivery_seal, model.getDeliveringSealNo(), model::setDeliveringSealNo);
        else if (id == R.id.b2506_deliverer)
            editText(R.string.bm2506_deliverer, model.getDelivererName(), model::setDelivererName);
        else if (id == R.id.b2506_recipient)
            editText(R.string.bm2506_recipient, model.getRecipientName(), model::setRecipientName);
    }

    @Override
    protected void saveModel() {
        DataHelper.postBM2506(model);
    }
}
