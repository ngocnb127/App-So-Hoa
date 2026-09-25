package com.megatech.fms;

import android.view.View;
import android.widget.Spinner;

import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.model.BM2506Model;

import java.util.Date;

public class B2506NewItemFragement extends BMFormItemFragment<BM2506Model> {

    public B2506NewItemFragement() {
        super(new BM2506Model());
        FMSApplication app = FMSApplication.getApplication();
        model.setTime(new Date());
        model.setTruckId(app.getTruckId());
        model.setOperatorId(app.getUser().getUserId());
        model.setOperatorName(app.getUser().getUserName());
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
        bindUserSpinner((Spinner) view.findViewById(R.id.b2506_operator), model.getOperatorId(), user -> {
            model.setOperatorId(user.getId());
            model.setOperatorName(user.getName());
        });
    }

    @Override
    protected void onFieldClick(int id) {
        if (id == R.id.b2506_sample_no)
            editText(R.string.bm2506_sample_no, model.getSampleNo(), model::setSampleNo);
        else if (id == R.id.b2506_time)
            pickTime(model.getTime(), model::setTime);
        else if (id == R.id.b2506_place)
            editText(R.string.bm2506_place, model.getPlace(), model::setPlace);
        else if (id == R.id.b2506_location)
            editText(R.string.bm2506_location, model.getLocation(), model::setLocation);
        else if (id == R.id.b2506_sample_type)
            editText(R.string.bm2506_sample_type, model.getSampleType(), model::setSampleType);
        else if (id == R.id.b2506_grade)
            editText(R.string.bm2506_grade, model.getGrade(), model::setGrade);
        else if (id == R.id.b2506_flight)
            selectFlight(flight -> {
                model.setFlightId(flight.getId());
                model.setFlightCode(flight.getFlightCode());
                model.setAircraftCode(flight.getAircraftCode());
            });
        else if (id == R.id.b2506_retention_seal)
            editText(R.string.bm2506_retention_seal, model.getRetentionSealNo(), model::setRetentionSealNo);
        else if (id == R.id.b2506_delivery_seal)
            editText(R.string.bm2506_delivery_seal, model.getDeliverySealNo(), model::setDeliverySealNo);
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
