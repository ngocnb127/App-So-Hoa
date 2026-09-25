package com.megatech.fms;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.megatech.fms.data.AppDatabase;
import com.megatech.fms.data.BM7501Repository;
import com.megatech.fms.helpers.BM7501Prefill;
import com.megatech.fms.helpers.BM7501Printer;
import com.megatech.fms.helpers.BM7501Signatures;
import com.megatech.fms.helpers.BM7501Validator;
import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.helpers.Logger;
import com.megatech.fms.helpers.ZebraWorker;
import com.megatech.fms.model.BM7501Model;
import com.megatech.fms.model.BM7501Model.Additive;
import com.megatech.fms.model.BM7501Model.AdditivePresence;
import com.megatech.fms.model.BM7501Model.DefuelMethod;
import com.megatech.fms.model.BM7501Model.DefuelReason;
import com.megatech.fms.model.BM7501Model.HandlingOption;
import com.megatech.fms.model.BM7501Model.MicrobialKit;
import com.megatech.fms.model.BM7501Model.MicrobialResult;
import com.megatech.fms.model.BM7501Model.QcCheck;
import com.megatech.fms.model.BM7501Model.TriState;
import com.megatech.fms.model.RefuelItemData;
import com.megatech.fms.model.TruckModel;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Màn hình nhập BM 75.01 — phiếu yêu cầu hút nhiên liệu.
 *
 * <p>Mở theo MỘT mẻ hút (intent {@code REFUEL_UNIQUE_ID}), vì quan hệ đã chốt là
 * một phiếu ↔ một mẻ. Nút in nằm trong màn hình này chứ không nằm ở danh sách, để luôn
 * biết đang in cho chuyến nào.
 *
 * <p>Màn hình chỉ có đúng các ô của biểu mẫu giấy, theo đúng thứ tự A → B → C. Những gì app
 * đã biết (số phiếu, ngày giờ, hãng, sân bay, tàu bay, phương tiện, giờ hút, số lượng, nhiệt độ,
 * tỷ trọng, đại diện SKYPEC) được điền sẵn qua
 * {@link BM7501Prefill}; nhân viên chỉ nhập phần khách hàng khai và kết quả KTCL.
 *
 * <p>Nút in chỉ hiện khi {@code BuildConfig.THERMAL_PRINTER} — nhập/lưu thì bản nào cũng làm được.
 */
public class B7501Activity extends UserBaseActivity implements View.OnClickListener {

    public static final String EXTRA_REFUEL_UNIQUE_ID = "REFUEL_UNIQUE_ID";

    private static final String TIME_PATTERN = "HH:mm dd/MM/yyyy";
    private static final String LOG_TAG = "BM7501";

    private BM7501Repository repository;
    private BM7501Model model;
    private String refuelUniqueId;

    private ZebraWorker zebra;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_b7501);

        repository = new BM7501Repository(AppDatabase.getInstance(this).bm7501Dao());

        Intent intent = getIntent();
        refuelUniqueId = intent == null ? null : intent.getStringExtra(EXTRA_REFUEL_UNIQUE_ID);
        if (refuelUniqueId == null || refuelUniqueId.isEmpty()) {
            showErrorMessage(R.string.bm7501_missing_refuel);
            finish();
            return;
        }

        setupConditionalRows();

        Button btnPrint = findViewById(R.id.btnPrint7501);
        btnPrint.setVisibility(BuildConfig.THERMAL_PRINTER ? View.VISIBLE : View.GONE);

        loadOrCreate();
    }

    /**
     * Ô phụ chỉ hiện khi lựa chọn đòi hỏi, để phiếu không dài vô ích:
     * "ghi rõ" của lý do khác, "ghi rõ" của thiết bị khác, thời hạn lưu trữ, ghi chú vấn đề.
     * Các ô phụ gia loại trừ nhau đúng như biểu mẫu (có phụ gia ≠ không dùng ≠ không xác định).
     */
    private void setupConditionalRows() {
        ((RadioGroup) findViewById(R.id.f_reason)).setOnCheckedChangeListener((g, id) ->
                showRow(R.id.row_reasonOther, id == R.id.f_reason_other));
        ((RadioGroup) findViewById(R.id.f_custMicroKit)).setOnCheckedChangeListener((g, id) ->
                showRow(R.id.row_custKitOther, id == R.id.f_custKit_other));
        ((RadioGroup) findViewById(R.id.f_handling)).setOnCheckedChangeListener((g, id) -> {
            showRow(R.id.row_storage, id == R.id.f_handling_storage);
            showRow(R.id.row_handlingNote, id == R.id.f_handling_despite);
        });

        exclusiveAdditive(R.id.f_add_none, R.id.f_add_undet);
        exclusiveAdditive(R.id.f_add_undet, R.id.f_add_none);
        for (int id : new int[]{R.id.f_add_fsii, R.id.f_add_biocide, R.id.f_add_aquarius}) {
            ((CheckBox) findViewById(id)).setOnCheckedChangeListener((v, checked) -> {
                if (!checked) return;
                setChecked(R.id.f_add_none, false);
                setChecked(R.id.f_add_undet, false);
            });
        }
    }

    private void exclusiveAdditive(int id, int otherId) {
        ((CheckBox) findViewById(id)).setOnCheckedChangeListener((CompoundButton v, boolean checked) -> {
            if (!checked) return;
            setChecked(otherId, false);
            setChecked(R.id.f_add_fsii, false);
            setChecked(R.id.f_add_biocide, false);
            setChecked(R.id.f_add_aquarius, false);
        });
    }

    private void showRow(int id, boolean visible) {
        findViewById(id).setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------------------------ nạp dữ liệu

    private void loadOrCreate() {
        setProgressDialog();
        new Thread(() -> {
            BM7501Model loaded = null;
            boolean duplicated = false;
            try {
                duplicated = repository.hasMultipleActive(refuelUniqueId);
                loaded = repository.getActive(refuelUniqueId);

                RefuelItemData item = DataHelper.getRefuelItem(refuelUniqueId);
                if (loaded == null) {
                    BM7501Model created = BM7501Prefill.fromRefuelItem(item);
                    created.setRefuelItemUniqueId(refuelUniqueId);
                    created.setEnteredByUserId(currentUser == null ? 0 : currentUser.getUserId());
                    created.setEnteredByUserName(currentUser == null ? null : currentUser.getUserName());
                    created.setTruckId(currentApp.getTruckId());
                    loaded = repository.createOrGetActive(created);
                    inherit(loaded, item);
                    repository.savePayload(loaded);
                } else if (loaded.isEditable() && inherit(loaded, item)) {
                    // Phiếu lập trước khi mẻ hút kết thúc: điền bù giờ, số lượng, tỷ trọng
                    // vừa có, thay vì bắt nhân viên gõ lại.
                    repository.savePayload(loaded);
                }

                // Số phiếu của mẻ hút có thể được cấp sau lúc lập phiếu.
                if (loaded != null && item != null) {
                    repository.fillLocalNumber(loaded, item.getReceiptNumber());
                }
            } catch (Exception ex) {
                Logger.appendLog(LOG_TAG, "loadOrCreate: " + ex.getMessage());
            }

            final BM7501Model result = loaded;
            final boolean hasDuplicate = duplicated;
            runOnUiThread(() -> {
                closeProgressDialog();
                if (result == null) {
                    showErrorMessage(R.string.error_loading_item);
                    finish();
                    return;
                }
                model = result;
                bindToForm();
                if (hasDuplicate) {
                    // Bất biến "mỗi mẻ một phiếu hiệu lực" bị vi phạm: khoá ký/in,
                    // không tự chọn bừa một revision.
                    findViewById(R.id.btnPrint7501).setEnabled(false);
                    findViewById(R.id.btnSave7501).setEnabled(false);
                    showErrorMessage(R.string.bm7501_multiple_active);
                    Logger.appendLog(LOG_TAG, "Nhiều phiếu hiệu lực cho mẻ " + refuelUniqueId);
                }
            });
        }, "BM7501-Load").start();
    }

    /** Kế thừa từ mẻ hút, phiên đăng nhập và phiếu trước của cùng hãng. */
    private boolean inherit(BM7501Model m, RefuelItemData item) {
        boolean changed = BM7501Prefill.fillMissing(m, item);
        changed |= BM7501Prefill.fillSession(m,
                currentUser == null ? 0 : currentUser.getAirportId(),
                currentUser == null ? null : currentUser.getAirport(),
                currentUser == null ? null : currentUser.getUserName());
        return changed;
    }

    // ------------------------------------------------------------------ model -> form

    private void bindToForm() {
        setText(R.id.f_customerRepName, model.getCustomerRepName());
        setText(R.id.f_customerTitle, model.getCustomerTitle());
        setText(R.id.f_customerTel, model.getCustomerTel());
        setText(R.id.f_customerFax, model.getCustomerFax());
        setText(R.id.f_aircraftType, model.getAircraftType());
        setText(R.id.f_aircraftReg, model.getAircraftReg());

        checkRadio(R.id.f_reason, model.getReason() == DefuelReason.LOAD_ADJUSTMENT ? R.id.f_reason_load
                : model.getReason() == DefuelReason.MAINTENANCE ? R.id.f_reason_maint
                : model.getReason() == DefuelReason.OTHER ? R.id.f_reason_other : -1);
        setText(R.id.f_reasonOther, model.getReasonOther());

        checkRadio(R.id.f_tankDrain, Boolean.TRUE.equals(model.getTankDrainSampled()) ? R.id.f_tankDrain_yes
                : Boolean.FALSE.equals(model.getTankDrainSampled()) ? R.id.f_tankDrain_no : -1);

        checkRadio(R.id.f_custMicroKit, kitId(model.getCustomerMicrobialKit(), true));
        setText(R.id.f_custMicroKitOther, model.getCustomerMicrobialKitOther());
        checkRadio(R.id.f_custMicroResult, resultId(model.getCustomerMicrobialResult(), true));

        List<Additive> additives = model.getAdditives();
        boolean present = model.getAdditivePresence() == AdditivePresence.PRESENT;
        setChecked(R.id.f_add_fsii, present && additives != null && additives.contains(Additive.FSII));
        setChecked(R.id.f_add_biocide, present && additives != null && additives.contains(Additive.BIOCIDE));
        setChecked(R.id.f_add_aquarius, present && additives != null && additives.contains(Additive.AQUARIUS_WMA));
        setChecked(R.id.f_add_none, model.getAdditivePresence() == AdditivePresence.NONE);
        setChecked(R.id.f_add_undet, model.getAdditivePresence() == AdditivePresence.UNDETERMINED);

        setText(R.id.f_prevLocation1, model.getPrevLocation1());
        setText(R.id.f_prevGrade1, model.getPrevGrade1());
        setText(R.id.f_prevLocation2, model.getPrevLocation2());
        setText(R.id.f_prevGrade2, model.getPrevGrade2());

        checkRadio(R.id.f_vac, model.getVac() == QcCheck.SATISFY ? R.id.f_vac_ok
                : model.getVac() == QcCheck.NOT_SATISFY ? R.id.f_vac_no : -1);
        checkRadio(R.id.f_cwd, model.getCwd() == QcCheck.SATISFY ? R.id.f_cwd_ok
                : model.getCwd() == QcCheck.NOT_SATISFY ? R.id.f_cwd_no : -1);
        setNumber(R.id.f_densityKgM3, model.getDensityKgM3());
        setNumber(R.id.f_conductivityPsM, model.getConductivityPsM());
        checkRadio(R.id.f_skypecMicroKit, kitId(model.getSkypecMicrobialKit(), false));
        checkRadio(R.id.f_skypecMicroResult, resultId(model.getSkypecMicrobialResult(), false));

        setText(R.id.f_defuellerTruckNo, model.getDefuellerTruckNo());
        setText(R.id.f_startTime, formatDate(model.getStartTime()));
        setText(R.id.f_endTime, formatDate(model.getEndTime()));
        checkRadio(R.id.f_method, model.getMethod() == DefuelMethod.AIRCRAFT_PUMP ? R.id.f_method_ac
                : model.getMethod() == DefuelMethod.REFUELLER_PUMP ? R.id.f_method_ref
                : model.getMethod() == DefuelMethod.BOTH ? R.id.f_method_both : -1);

        // Phiếu cũ chỉ có một cờ chung: coi như đã thống nhất cả hai tín hiệu chuẩn.
        boolean legacy = model.isSignalsBriefed()
                && !model.isSignalThumbUp() && !model.isSignalCrossArms();
        setChecked(R.id.f_signalThumb, model.isSignalThumbUp() || legacy);
        setChecked(R.id.f_signalCross, model.isSignalCrossArms() || legacy);
        setText(R.id.f_otherSignal, model.getOtherSignal());

        setNumber(R.id.f_expectedKg, model.getExpectedKg());
        setNumber(R.id.f_actualKg, model.getActualKg());
        setNumber(R.id.f_actualTempC, model.getActualTempC());
        setNumber(R.id.f_actualDensityKgM3, model.getActualDensityKgM3());
        setNumber(R.id.f_gallon, model.getGallon());
        setNumber(R.id.f_liter, model.getLiter());

        checkRadio(R.id.f_refuellable, Boolean.TRUE.equals(model.getRefuellableWithoutTest())
                ? R.id.f_refuellable_yes
                : Boolean.FALSE.equals(model.getRefuellableWithoutTest()) ? R.id.f_refuellable_no : -1);
        checkRadio(R.id.f_handling, handlingId(model.getHandling()));
        setText(R.id.f_storageFrom, formatDate(model.getStorageFrom()));
        setText(R.id.f_storageTo, formatDate(model.getStorageTo()));
        setText(R.id.f_handlingNote, model.getHandlingNote());
        setText(R.id.f_skypecRepName, model.getSkypecRepName());

        updateSignatureLabels();
        updateStatusLine();
        setFormEnabled(model.isEditable());
    }

    private void updateStatusLine() {
        String number = isBlank(model.getLocalNumber())
                ? getString(R.string.bm7501_no_number_yet) : model.getLocalNumber();
        ((TextView) findViewById(R.id.bm7501_status)).setText(getString(
                model.isExported() ? R.string.bm7501_status_exported : R.string.bm7501_status_line,
                number));

        ((TextView) findViewById(R.id.bm7501_context)).setText(getString(R.string.bm7501_context_line,
                model.getDate() == null ? "—" : formatDate(model.getDate()),
                isBlank(model.getAirlineName()) ? "—" : model.getAirlineName(),
                isBlank(model.getAirportName()) ? "—" : model.getAirportName()));
    }

    // ------------------------------------------------------------------ form -> model

    private void bindFromForm() {
        model.setCustomerRepName(fieldText(R.id.f_customerRepName));
        model.setCustomerTitle(fieldText(R.id.f_customerTitle));
        model.setCustomerTel(fieldText(R.id.f_customerTel));
        model.setCustomerFax(fieldText(R.id.f_customerFax));
        model.setAircraftType(fieldText(R.id.f_aircraftType));
        model.setAircraftReg(fieldText(R.id.f_aircraftReg));

        int reason = checkedId(R.id.f_reason);
        model.setReason(reason == R.id.f_reason_load ? DefuelReason.LOAD_ADJUSTMENT
                : reason == R.id.f_reason_maint ? DefuelReason.MAINTENANCE
                : reason == R.id.f_reason_other ? DefuelReason.OTHER : null);
        model.setReasonOther(fieldText(R.id.f_reasonOther));

        int drain = checkedId(R.id.f_tankDrain);
        model.setTankDrainSampled(drain == R.id.f_tankDrain_yes ? Boolean.TRUE
                : drain == R.id.f_tankDrain_no ? Boolean.FALSE : null);

        MicrobialKit custKit = kitAt(checkedId(R.id.f_custMicroKit));
        MicrobialResult custResult = resultAt(checkedId(R.id.f_custMicroResult));
        model.setCustomerMicrobialKit(custKit);
        model.setCustomerMicrobialKitOther(fieldText(R.id.f_custMicroKitOther));
        model.setCustomerMicrobialResult(custResult);
        // Biểu mẫu không hỏi riêng "hãng đã kiểm tra chưa": có khai thiết bị/kết quả nghĩa là có.
        model.setCustomerMicrobialTestPerformed(
                custKit != null || custResult != null ? TriState.YES : TriState.NO);

        List<Additive> additives = new ArrayList<>();
        if (isChecked(R.id.f_add_fsii)) additives.add(Additive.FSII);
        if (isChecked(R.id.f_add_biocide)) additives.add(Additive.BIOCIDE);
        if (isChecked(R.id.f_add_aquarius)) additives.add(Additive.AQUARIUS_WMA);
        model.setAdditives(additives);
        model.setAdditivePresence(!additives.isEmpty() ? AdditivePresence.PRESENT
                : isChecked(R.id.f_add_none) ? AdditivePresence.NONE
                : isChecked(R.id.f_add_undet) ? AdditivePresence.UNDETERMINED : null);

        model.setPrevLocation1(fieldText(R.id.f_prevLocation1));
        model.setPrevGrade1(fieldText(R.id.f_prevGrade1));
        model.setPrevLocation2(fieldText(R.id.f_prevLocation2));
        model.setPrevGrade2(fieldText(R.id.f_prevGrade2));

        int vac = checkedId(R.id.f_vac);
        model.setVac(vac == R.id.f_vac_ok ? QcCheck.SATISFY
                : vac == R.id.f_vac_no ? QcCheck.NOT_SATISFY : null);
        int cwd = checkedId(R.id.f_cwd);
        model.setCwd(cwd == R.id.f_cwd_ok ? QcCheck.SATISFY
                : cwd == R.id.f_cwd_no ? QcCheck.NOT_SATISFY : null);

        model.setDensityKgM3(getNumber(R.id.f_densityKgM3));
        model.setConductivityPsM(getNumber(R.id.f_conductivityPsM));
        model.setSkypecMicrobialKit(kitAt(checkedId(R.id.f_skypecMicroKit)));
        model.setSkypecMicrobialResult(resultAt(checkedId(R.id.f_skypecMicroResult)));

        model.setDefuellerTruckNo(fieldText(R.id.f_defuellerTruckNo));
        model.setStartTime(parseDate(fieldText(R.id.f_startTime)));
        model.setEndTime(parseDate(fieldText(R.id.f_endTime)));

        int method = checkedId(R.id.f_method);
        model.setMethod(method == R.id.f_method_ac ? DefuelMethod.AIRCRAFT_PUMP
                : method == R.id.f_method_ref ? DefuelMethod.REFUELLER_PUMP
                : method == R.id.f_method_both ? DefuelMethod.BOTH : null);
        model.setSignalThumbUp(isChecked(R.id.f_signalThumb));
        model.setSignalCrossArms(isChecked(R.id.f_signalCross));
        model.setSignalsBriefed(model.isSignalThumbUp() || model.isSignalCrossArms());
        model.setOtherSignal(fieldText(R.id.f_otherSignal));

        model.setExpectedKg(getNumber(R.id.f_expectedKg));
        model.setActualKg(getNumber(R.id.f_actualKg));
        model.setActualTempC(getNumber(R.id.f_actualTempC));
        model.setActualDensityKgM3(getNumber(R.id.f_actualDensityKgM3));
        model.setGallon(getNumber(R.id.f_gallon));
        model.setLiter(getNumber(R.id.f_liter));

        int refuellable = checkedId(R.id.f_refuellable);
        model.setRefuellableWithoutTest(refuellable == R.id.f_refuellable_yes ? Boolean.TRUE
                : refuellable == R.id.f_refuellable_no ? Boolean.FALSE : null);
        model.setHandling(handlingAt(checkedId(R.id.f_handling)));
        model.setStorageFrom(parseDate(fieldText(R.id.f_storageFrom)));
        model.setStorageTo(parseDate(fieldText(R.id.f_storageTo)));
        model.setHandlingNote(fieldText(R.id.f_handlingNote));
        model.setSkypecRepName(fieldText(R.id.f_skypecRepName));
    }

    // ------------------------------------------------------------------ hành động

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btnBack) {
            save(false);
            finish();
        } else if (id == R.id.btnSave7501) {
            save(true);
        } else if (id == R.id.btnPrint7501) {
            printForm();
        } else if (id == R.id.btnExport7501) {
            exportForm();
        } else if (id == R.id.btnSignSeller) {
            openSign(REQ_SIGN_SELLER);
        } else if (id == R.id.btnSignBuyer) {
            openSign(REQ_SIGN_BUYER);
        } else if (id == R.id.btnPrevUndetermined) {
            fillUndetermined();
        } else if (id == R.id.f_startTime || id == R.id.f_endTime
                || id == R.id.f_storageFrom || id == R.id.f_storageTo) {
            pickDateTime(id);
        }
    }

    /** Lối tắt cho trường hợp phổ biến nhất của mục A4: hãng không xác định được loại nhiên liệu. */
    private void fillUndetermined() {
        String text = getString(R.string.bm7501_undetermined);
        if (isBlank(fieldText(R.id.f_prevGrade1))) setText(R.id.f_prevGrade1, text);
        if (isBlank(fieldText(R.id.f_prevGrade2))) setText(R.id.f_prevGrade2, text);
        if (isBlank(fieldText(R.id.f_prevLocation1))) setText(R.id.f_prevLocation1, text);
        if (isBlank(fieldText(R.id.f_prevLocation2))) setText(R.id.f_prevLocation2, text);
    }

    /**
     * Chọn ngày rồi chọn giờ. Gõ tay "HH:mm dd/MM/yyyy" giữa sân đỗ vừa chậm vừa dễ sai định dạng,
     * mà sai thì ô giờ âm thầm thành rỗng.
     */
    private void pickDateTime(int fieldId) {
        if (model != null && !model.isEditable()) return;

        Calendar c = Calendar.getInstance();
        Date current = parseDate(fieldText(fieldId));
        if (current != null) c.setTime(current);

        new DatePickerDialog(this, (dateView, year, month, day) -> {
            c.set(Calendar.YEAR, year);
            c.set(Calendar.MONTH, month);
            c.set(Calendar.DAY_OF_MONTH, day);
            new TimePickerDialog(this, (timeView, hour, minute) -> {
                c.set(Calendar.HOUR_OF_DAY, hour);
                c.set(Calendar.MINUTE, minute);
                c.set(Calendar.SECOND, 0);
                setText(fieldId, formatDate(c.getTime()));
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void save(boolean showResult) {
        if (model == null) return;
        bindFromForm();

        new Thread(() -> {
            final BM7501Repository.WriteResult result = repository.savePayload(model);
            runOnUiThread(() -> {
                if (result == BM7501Repository.WriteResult.CONFLICT) {
                    // Có bản ghi mới hơn: đọc lại, tuyệt đối không ghi đè.
                    showErrorMessage(R.string.bm7501_conflict);
                    loadOrCreate();
                    return;
                }
                if (result == BM7501Repository.WriteResult.INVALID_STATE) {
                    showErrorMessage(R.string.bm7501_locked);
                    return;
                }
                if (showResult) {
                    showValidationSummary();
                }
            });
        }, "BM7501-Save").start();
    }

    /** Cho nhân viên biết còn thiếu gì, nhưng không chặn việc lưu nháp. */
    private void showValidationSummary() {
        List<BM7501Validator.Finding> findings = BM7501Validator.validateForSigning(model);
        List<BM7501Validator.Finding> errors = BM7501Validator.errorsOnly(findings);

        if (errors.isEmpty()) {
            List<BM7501Validator.Finding> warnings = BM7501Validator.warningsOnly(findings);
            if (warnings.isEmpty()) {
                showInfoMessage(R.string.bm7501_saved_complete);
            } else {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle(R.string.bm7501_saved_complete)
                        .setMessage(missingText(warnings))
                        .setPositiveButton(R.string.ok, null)
                        .show();
            }
            return;
        }

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.bm7501_saved_incomplete)
                .setMessage(missingText(errors))
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    /** Liệt kê tối đa 8 mục còn thiếu, đủ để sửa mà không tràn màn hình. */
    private String missingText(List<BM7501Validator.Finding> errors) {
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (BM7501Validator.Finding f : errors) {
            if (shown++ >= 8) {
                sb.append("\n… và ").append(errors.size() - 8).append(" mục khác");
                break;
            }
            sb.append("\n• ").append(f.getMessage());
        }
        return sb.toString();
    }

    /**
     * Xuất phiếu — chốt sổ. Khác nút In: In cho phép phiếu còn thiếu (in trước, ghi tay sau),
     * còn Xuất là chốt nên phải đủ thông tin và phải có số phiếu, vì số đó đi vào danh sách mẻ
     * hút và đi lên hệ thống.
     */
    private void exportForm() {
        if (model == null) return;
        if (!model.isEditable()) {
            showErrorMessage(R.string.bm7501_locked_exported);
            return;
        }
        bindFromForm();

        List<BM7501Validator.Finding> errors =
                BM7501Validator.errorsOnly(BM7501Validator.validateForSigning(model));
        if (!errors.isEmpty()) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.bm7501_export_incomplete)
                    .setMessage(missingText(errors))
                    .setPositiveButton(R.string.ok, null)
                    .show();
            return;
        }
        if (isBlank(model.getLocalNumber())) {
            showErrorMessage(R.string.bm7501_export_no_number);
            return;
        }

        // Thiếu chữ ký không chặn (hai bên có thể ký tay trên giấy sau khi in), nhưng phải
        // hỏi lại — chốt sổ xong là không quay lại ký trên máy được nữa.
        List<BM7501Validator.Finding> warnings =
                BM7501Validator.warningsOnly(BM7501Validator.validateForSigning(model));
        if (!warnings.isEmpty()) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.bm7501_export_warning)
                    .setMessage(missingText(warnings) + "\n\n"
                            + getString(R.string.bm7501_export_confirm))
                    .setPositiveButton(R.string.bm7501_export_anyway, (d, w) -> doExport())
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }

        showConfirmMessage(R.string.bm7501_export_confirm, () -> {
            doExport();
            return null;
        });
    }

    private void doExport() {
        setProgressDialog();
        new Thread(() -> {
            final BM7501Repository.WriteResult result =
                    repository.export(model, currentUser == null ? 0 : currentUser.getUserId());
            runOnUiThread(() -> {
                closeProgressDialog();
                if (result == BM7501Repository.WriteResult.OK) {
                    showInfoMessage(R.string.bm7501_exported_ok);
                    updateStatusLine();
                    setFormEnabled(false);
                } else if (result == BM7501Repository.WriteResult.CONFLICT) {
                    showErrorMessage(R.string.bm7501_conflict);
                    loadOrCreate();
                } else {
                    showErrorMessage(R.string.bm7501_export_failed);
                }
            });
        }, "BM7501-Export").start();
    }

    private void printForm() {
        if (model == null) return;
        if (!BuildConfig.THERMAL_PRINTER) {
            showInfoMessage(R.string.printer_nhiet);
            return;
        }
        bindFromForm();

        // Thiếu thông tin thì nhắc, nhưng vẫn cho in: ngoài sân đỗ có khi phải in trước
        // rồi hai bên ghi nốt bằng tay.
        List<BM7501Validator.Finding> errors =
                BM7501Validator.errorsOnly(BM7501Validator.validateForSigning(model));
        if (errors.isEmpty()) {
            doPrint();
            return;
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.bm7501_print_incomplete)
                .setMessage(missingText(errors))
                .setPositiveButton(R.string.bm7501_print_anyway, (d, w) -> doPrint())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void doPrint() {
        final BM7501Printer.Options options = new BM7501Printer.Options()
                .zq520(currentApp.getSetting() != null
                        && currentApp.getSetting().getThermalPrinterType()
                            == TruckModel.THERMAL_PRINTER_TYPE.ZQ520);
        if (currentUser != null && currentUser.getInvoiceName() != null) {
            options.companyName(currentUser.getInvoiceName());
        }

        if (zebra == null) {
            zebra = new ZebraWorker(this);
            zebra.setStateListener(new ZebraWorker.ZebraStateListener() {
                @Override
                public void onConnectionError() {
                    runOnUiThread(() -> {
                        closeProgressDialog();
                        showErrorMessage(R.string.print_bm7501_no_printer);
                    });
                }

                @Override
                public void onError() {
                    runOnUiThread(() -> {
                        closeProgressDialog();
                        showErrorMessage(R.string.print_bm7501_no_printer);
                    });
                }

                @Override
                public void onSuccess() {
                    runOnUiThread(() -> closeProgressDialog());
                }
            });
        }

        setProgressDialog();
        new Thread(() -> {
            // Phiếu đã xuất là bản chốt: in chỉ đọc, không ghi đè nội dung.
            if (model.isEditable()) repository.savePayload(model);
            zebra.print7501(model, options);
        }, "BM7501-Print").start();
    }

    @Override
    protected void onPause() {
        // Nhân viên nhập giữa sân đỗ: rời màn hình lúc nào cũng phải giữ được nội dung.
        if (model != null && model.isEditable()) {
            bindFromForm();
            new Thread(() -> repository.savePayload(model), "BM7501-Autosave").start();
        }
        super.onPause();
    }

    // ------------------------------------------------------------------ chữ ký

    private static final int REQ_SIGN_SELLER = 7502;
    private static final int REQ_SIGN_BUYER = 7503;

    private void openSign(int requestCode) {
        if (model == null) return;
        if (!model.isEditable()) {
            showErrorMessage(R.string.bm7501_locked_exported);
            return;
        }
        bindFromForm();
        startActivityForResult(new Intent(this, ReceiptSignActivity.class), requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null
                || (requestCode != REQ_SIGN_SELLER && requestCode != REQ_SIGN_BUYER)) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }

        final String source = data.getStringExtra("signature_file");
        final String slot = requestCode == REQ_SIGN_SELLER
                ? BM7501Signatures.SLOT_SKYPEC
                : BM7501Signatures.SLOT_CUSTOMER_FINAL;

        new Thread(() -> {
            BM7501Signatures.Stored stored = null;
            try {
                // Ảnh ký nằm ở bộ nhớ ngoài dạng file tạm; chép ngay vào vùng riêng của app
                // kèm checksum, vì đây là chứng từ pháp lý.
                stored = BM7501Signatures.store(this, model.getUniqueId(), slot, source);
            } catch (Exception ex) {
                Logger.appendLog(LOG_TAG, "Lưu chữ ký lỗi: " + ex.getMessage());
            }

            final BM7501Signatures.Stored result = stored;
            runOnUiThread(() -> {
                if (result == null) {
                    showErrorMessage(R.string.bm7501_sign_save_failed);
                    return;
                }
                if (BM7501Signatures.SLOT_SKYPEC.equals(slot)) {
                    model.setSkypecSignaturePath(result.getPath());
                    model.setSkypecSignatureSha256(result.getSha256());
                } else {
                    model.setCustomerFinalSignaturePath(result.getPath());
                    model.setCustomerFinalSignatureSha256(result.getSha256());
                }
                updateSignatureLabels();
                new Thread(() -> repository.savePayload(model), "BM7501-SaveSign").start();
            });
        }, "BM7501-StoreSign").start();
    }

    private void updateSignatureLabels() {
        showSignature(R.id.lbl_sign_seller, R.id.img_sign_seller,
                R.string.bm7501_sign_label_skypec, model.getSkypecSignaturePath());
        showSignature(R.id.lbl_sign_buyer, R.id.img_sign_buyer,
                R.string.bm7501_sign_label_customer, model.getCustomerFinalSignaturePath());
    }

    /**
     * Hiện luôn ảnh chữ ký vừa ký. Chỉ ghi chữ "đã ký" thì nhân viên không kiểm được là đã ký
     * đúng người hay nét ký có bị mất khi lưu hay không.
     */
    private void showSignature(int labelId, int imageId, int labelRes, String path) {
        ((TextView) findViewById(labelId)).setText(getString(
                isBlank(path) ? R.string.bm7501_unsigned : R.string.bm7501_signed,
                getString(labelRes)));

        ImageView image = findViewById(imageId);
        Bitmap bitmap = isBlank(path) ? null : BitmapFactory.decodeFile(path);
        if (bitmap == null) {
            image.setImageDrawable(null);
            image.setVisibility(View.GONE);
            return;
        }
        image.setImageBitmap(bitmap);
        image.setVisibility(View.VISIBLE);
    }

    /**
     * Khoá ô nhập. Phiếu bình thường KHÔNG bao giờ bị khoá — sửa và in lại là chuyện thường.
     * Chỉ dùng cho phiếu đã huỷ/vô hiệu hoá và cho trường hợp dữ liệu bất thường
     * (một mẻ có nhiều phiếu hiệu lực).
     */
    private void setFormEnabled(boolean enabled) {
        findViewById(R.id.btnSave7501).setEnabled(enabled);
        findViewById(R.id.btnExport7501).setEnabled(enabled);
        setEnabledDeep(findViewById(R.id.bm7501_form), enabled);
        // Nút In luôn bật: phiếu đã xuất vẫn phải in lại được.
        findViewById(R.id.btnPrint7501).setEnabled(true);
    }

    private static void setEnabledDeep(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                setEnabledDeep(group.getChildAt(i), enabled);
            }
        }
    }

    // ------------------------------------------------------------------ tiện ích view

    private void setText(int id, String value) {
        ((EditText) findViewById(id)).setText(value == null ? "" : value);
    }

    private String fieldText(int id) {
        String s = ((EditText) findViewById(id)).getText().toString().trim();
        return s.isEmpty() ? null : s;
    }

    private void setNumber(int id, Double value) {
        ((EditText) findViewById(id)).setText(
                value == null ? "" : String.format(Locale.US, "%s", trimZero(value)));
    }

    private static String trimZero(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(v);
    }

    private Double getNumber(int id) {
        String s = fieldText(id);
        if (s == null) return null;
        try {
            return Double.parseDouble(s.replace(",", "."));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void setChecked(int id, boolean checked) {
        ((CheckBox) findViewById(id)).setChecked(checked);
    }

    private boolean isChecked(int id) {
        return ((CheckBox) findViewById(id)).isChecked();
    }

    /** {@code buttonId} = -1 nghĩa là chưa chọn gì — bỏ chọn cả nhóm. */
    private void checkRadio(int groupId, int buttonId) {
        RadioGroup group = findViewById(groupId);
        if (buttonId == -1) {
            group.clearCheck();
        } else {
            group.check(buttonId);
        }
    }

    private int checkedId(int groupId) {
        return ((RadioGroup) findViewById(groupId)).getCheckedRadioButtonId();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    // ------------------------------------------------------------------ ánh xạ enum

    private static int kitId(MicrobialKit kit, boolean customerSection) {
        if (kit == null) return -1;
        switch (kit) {
            case HY_LITE: return customerSection ? R.id.f_custKit_hylite : R.id.f_skKit_hylite;
            case MICROB_MONITOR2: return customerSection ? R.id.f_custKit_mm2 : R.id.f_skKit_mm2;
            case FUELSTAT: return customerSection ? R.id.f_custKit_fuelstat : R.id.f_skKit_fuelstat;
            // Biểu mẫu chỉ có ô "Khác" ở mục A.
            case OTHER: return customerSection ? R.id.f_custKit_other : -1;
            default: return -1;
        }
    }

    private static MicrobialKit kitAt(int id) {
        if (id == R.id.f_custKit_hylite || id == R.id.f_skKit_hylite) return MicrobialKit.HY_LITE;
        if (id == R.id.f_custKit_mm2 || id == R.id.f_skKit_mm2) return MicrobialKit.MICROB_MONITOR2;
        if (id == R.id.f_custKit_fuelstat || id == R.id.f_skKit_fuelstat) return MicrobialKit.FUELSTAT;
        if (id == R.id.f_custKit_other) return MicrobialKit.OTHER;
        return null;
    }

    private static int resultId(MicrobialResult r, boolean customerSection) {
        if (r == null) return -1;
        switch (r) {
            case NORMAL: return customerSection ? R.id.f_custRes_normal : R.id.f_skRes_normal;
            case WARNING: return customerSection ? R.id.f_custRes_warning : R.id.f_skRes_warning;
            case ACTION: return customerSection ? R.id.f_custRes_action : R.id.f_skRes_action;
            default: return -1;
        }
    }

    private static MicrobialResult resultAt(int id) {
        if (id == R.id.f_custRes_normal || id == R.id.f_skRes_normal) return MicrobialResult.NORMAL;
        if (id == R.id.f_custRes_warning || id == R.id.f_skRes_warning) return MicrobialResult.WARNING;
        if (id == R.id.f_custRes_action || id == R.id.f_skRes_action) return MicrobialResult.ACTION;
        return null;
    }

    private static int handlingId(HandlingOption h) {
        if (h == null) return -1;
        switch (h) {
            case STORAGE: return R.id.f_handling_storage;
            case SAME_AIRCRAFT: return R.id.f_handling_same;
            case OTHER_AIRCRAFT_SAME_AIRLINE: return R.id.f_handling_other_ac;
            case AUTHORIZE_SKYPEC: return R.id.f_handling_authorize;
            case REFUEL_DESPITE_ISSUE: return R.id.f_handling_despite;
            default: return -1;
        }
    }

    private static HandlingOption handlingAt(int id) {
        if (id == R.id.f_handling_storage) return HandlingOption.STORAGE;
        if (id == R.id.f_handling_same) return HandlingOption.SAME_AIRCRAFT;
        if (id == R.id.f_handling_other_ac) return HandlingOption.OTHER_AIRCRAFT_SAME_AIRLINE;
        if (id == R.id.f_handling_authorize) return HandlingOption.AUTHORIZE_SKYPEC;
        if (id == R.id.f_handling_despite) return HandlingOption.REFUEL_DESPITE_ISSUE;
        return null;
    }

    private static String formatDate(Date d) {
        return d == null ? "" : new SimpleDateFormat(TIME_PATTERN, Locale.US).format(d);
    }

    private static Date parseDate(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try {
            return new SimpleDateFormat(TIME_PATTERN, Locale.US).parse(s.trim());
        } catch (ParseException ex) {
            return null;
        }
    }
}
