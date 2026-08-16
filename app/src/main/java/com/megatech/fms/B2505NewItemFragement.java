package com.megatech.fms;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.DialogFragment;

import com.megatech.fms.databinding.B2505NewBinding;
import com.megatech.fms.helpers.BM2505Factory;
import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.model.AirportsModel;
import com.megatech.fms.model.BM2505ContainerModel;
import com.megatech.fms.model.BM2505Model;
import com.megatech.fms.model.FlightModel;
import com.megatech.fms.model.TruckModel;
import com.megatech.fms.model.UserModel;
import com.megatech.fms.view.FlightArrayAdapter;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class B2505NewItemFragement extends DialogFragment {

    private static final String ARG_EDIT_MODE = "BM2505_EDIT_MODE";

    private int mYear, mMonth, mDay, mHour, mMinute;

    Locale locale = Locale.getDefault();
    NumberFormat numberFormat = NumberFormat.getInstance(locale);
    Dialog dlg;
    BM2505Model model;
    View rootView;
    B2505NewBinding binding;

    // ==== MASTER DATA: fragment tự tải, không phụ thuộc list bất đồng bộ của activity ====
    private final List<UserModel> userList = new ArrayList<>();
    private final List<BM2505ContainerModel> containers = new ArrayList<>();
    private final List<AirportsModel> airports = new ArrayList<>();
    private final List<TruckModel> trucks = new ArrayList<>();
    private List<FlightModel> flights = new ArrayList<>();

    private boolean isEditMode = false;
    private boolean isSaving = false;
    private boolean masterDataLoaded = false;

    /**
     * Fragment phải có constructor rỗng để Android tái tạo được sau khi đổi cấu hình.
     * Dữ liệu luôn đi qua arguments/savedInstanceState.
     */
    public B2505NewItemFragement() {
    }

    /**
     * Thêm mới: args mang ngữ cảnh (TRUCK_ID, FLIGHT_ID, FLIGHT_CODE, AIRCRAFT_CODE, AIRPORT_ID...).
     */
    public static B2505NewItemFragement newInstance(@Nullable Bundle args) {
        B2505NewItemFragement frag = new B2505NewItemFragement();
        frag.setArguments(args != null ? new Bundle(args) : new Bundle());
        return frag;
    }

    /**
     * Sửa bản ghi đã có.
     */
    public static B2505NewItemFragement newInstance(BM2505Model model) {
        B2505NewItemFragement frag = new B2505NewItemFragement();
        Bundle args = new Bundle();
        args.putSerializable(BM2505Factory.ARG_MODEL, model);
        args.putBoolean(ARG_EDIT_MODE, true);
        frag.setArguments(args);
        return frag;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        dlg = super.onCreateDialog(savedInstanceState);
        return dlg;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        isEditMode = args != null && args.getBoolean(ARG_EDIT_MODE, false);

        if (savedInstanceState != null && savedInstanceState.containsKey(BM2505Factory.ARG_MODEL)) {
            // khôi phục đúng dữ liệu người dùng đang nhập dở
            model = (BM2505Model) savedInstanceState.getSerializable(BM2505Factory.ARG_MODEL);
            isEditMode = savedInstanceState.getBoolean(ARG_EDIT_MODE, isEditMode);
        } else if (args != null && args.containsKey(BM2505Factory.ARG_MODEL)) {
            model = (BM2505Model) args.getSerializable(BM2505Factory.ARG_MODEL);
        }

        if (model == null)
            model = BM2505Factory.createNew(args);

        if (model.getTime() == null)
            model.setTime(new Date());
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(BM2505Factory.ARG_MODEL, model);
        outState.putBoolean(ARG_EDIT_MODE, isEditMode);
    }

    public void findViews(View v) {
        try {
            if (v instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) v;
                for (int i = 0; i < vg.getChildCount(); i++) {
                    View child = vg.getChildAt(i);
                    // recursively call this method
                    findViews(child);
                }
            } else if (v instanceof TextView) {

                v.setOnClickListener(this::onClick);
                //do whatever you want ...
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DataBindingUtil.inflate(inflater, R.layout.b2505_new, container, false);
        binding.setMItem(this.model);
        rootView = binding.getRoot();
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        findViews(view);

        setSaveEnabled(false);
        loadMasterData(view);
    }

    /**
     * Tải toàn bộ master data cần cho form: sân bay, người thực hiện, chuyến bay,
     * loại bồn và danh sách xe (để xác định sân bay của xe tra nạp).
     */
    private void loadMasterData(final View view) {

        new AsyncTask<Void, Void, Void>() {
            List<UserModel> usersResult;
            List<FlightModel> flightsResult;
            List<BM2505ContainerModel> containersResult;
            List<AirportsModel> airportsResult;
            List<TruckModel> trucksResult;

            @Override
            protected Void doInBackground(Void... voids) {
                usersResult = DataHelper.getUsers();
                flightsResult = DataHelper.getFlights();
                containersResult = DataHelper.getBM2505ContainerList();
                airportsResult = DataHelper.getAirports();
                trucksResult = BuildConfig.FHS ? DataHelper.getFHSTrucks() : DataHelper.getTrucks();
                return null;
            }

            @Override
            protected void onPostExecute(Void unused) {
                if (!isAdded()) return;

                userList.clear();
                if (usersResult != null) userList.addAll(usersResult);

                containers.clear();
                if (containersResult != null) containers.addAll(containersResult);

                airports.clear();
                if (airportsResult != null) airports.addAll(airportsResult);

                trucks.clear();
                if (trucksResult != null) trucks.addAll(trucksResult);

                flights = flightsResult != null ? flightsResult : new ArrayList<FlightModel>();

                masterDataLoaded = true;
                setupAirportSpinner(view);
                setupOperatorSpinner(view);
                setupContainerSpinner(view);
                setSaveEnabled(true);
                binding.invalidateAll();
            }
        }.execute();
    }

    // =========================
    // SPINNERS
    // =========================

    /**
     * Sân bay do hệ thống xác định (ưu tiên sân bay của xe tra nạp), người dùng không được đổi:
     * ô sân bay chỉ hiển thị, không gắn listener và luôn ở trạng thái disable.
     */
    private void setupAirportSpinner(View view) {
        Spinner spinner = view.findViewById(R.id.b2505_new_airport);
        if (spinner == null) return;

        TruckModel currentTruck = BM2505Factory.findTruck(trucks, model.getTruckId());
        BM2505Factory.AirportResolution resolution = BM2505Factory.resolveAirport(
                isEditMode, model.getAirportId(), currentTruck,
                BM2505Factory.getAccountAirportId(), airports);

        final List<AirportsModel> options = new ArrayList<>();

        if (resolution.resolved) {
            model.setAirportId(resolution.airportId);
            if (resolution.airportName != null && !resolution.airportName.trim().isEmpty())
                model.setAirportName(resolution.airportName);

            String display = model.getAirportName() != null && !model.getAirportName().trim().isEmpty()
                    ? model.getAirportName()
                    : String.valueOf(resolution.airportId);
            options.add(createAirportOption(resolution.airportId, display));
        } else {
            // không xác định được sân bay: để trống và chặn ở bước validate, không cho tự chọn
            model.setAirportId(null);
            model.setAirportName(null);
            options.add(createAirportOption(0, getString(R.string.bm2505_airport_undetermined)));

            if (resolution.truckAirportNotAllowed)
                showError(R.string.bm2505_truck_airport_not_allowed);
        }

        ArrayAdapter<AirportsModel> adapter = new ArrayAdapter<>(requireContext(),
                R.layout.support_simple_spinner_dropdown_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);

        spinner.setOnItemSelectedListener(null);
        spinner.setAdapter(adapter);
        spinner.setSelection(0, false);
        spinner.setEnabled(false);
        spinner.setClickable(false);
    }

    private void setupOperatorSpinner(View view) {
        Spinner spinner = view.findViewById(R.id.b2505_new_operator);
        if (spinner == null) return;

        final List<UserModel> options = new ArrayList<>(userList);
        int selectedIndex = indexOfUser(options, model.getOperatorId());

        if (selectedIndex < 0) {
            // không tìm thấy nhân viên tương ứng: yêu cầu chọn, không lấy phần tử đầu
            UserModel placeholder = new UserModel();
            placeholder.setId(0);
            placeholder.setName(getString(R.string.select_user));
            options.add(0, placeholder);
            selectedIndex = 0;
            model.setOperatorId(0);
            model.setOperatorName(null);
        }

        ArrayAdapter<UserModel> adapter = new ArrayAdapter<>(requireContext(),
                R.layout.support_simple_spinner_dropdown_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);

        bindSpinner(spinner, adapter, selectedIndex, new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View v, int i, long l) {
                Object item = adapterView.getItemAtPosition(i);
                if (!(item instanceof UserModel)) return;
                UserModel user = (UserModel) item;
                if (user.getId() == null || user.getId() <= 0) {
                    model.setOperatorId(0);
                    model.setOperatorName(null);
                    return;
                }
                model.setOperatorId(user.getId());
                model.setOperatorName(user.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
    }

    private void setupContainerSpinner(View view) {
        Spinner spinner = view.findViewById(R.id.bm2505_container_list);
        if (spinner == null) return;

        final List<BM2505ContainerModel> options = new ArrayList<>(containers);
        int selectedIndex = indexOfContainer(options, model.getContainerId());

        if (selectedIndex < 0) {
            BM2505ContainerModel placeholder = new BM2505ContainerModel();
            placeholder.setId(0);
            placeholder.setName(getString(R.string.bm2505_container_type));
            options.add(0, placeholder);
            selectedIndex = 0;
            model.setContainerId(0);
            model.setContainerName(null);
        }

        ArrayAdapter<BM2505ContainerModel> adapter = new ArrayAdapter<>(requireContext(),
                R.layout.support_simple_spinner_dropdown_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);

        bindSpinner(spinner, adapter, selectedIndex, new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View v, int i, long l) {
                Object item = adapterView.getItemAtPosition(i);
                if (!(item instanceof BM2505ContainerModel)) return;
                BM2505ContainerModel container = (BM2505ContainerModel) item;
                if (container.getId() == null || container.getId() <= 0) {
                    model.setContainerId(0);
                    model.setContainerName(null);
                    return;
                }
                model.setContainerId(container.getId());
                model.setContainerName(container.getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
    }

    /**
     * Gắn adapter, đồng bộ selection rồi mới gắn listener (post) để lần bind đầu tiên
     * không ghi đè model bằng phần tử đầu danh sách.
     */
    private <T> void bindSpinner(final Spinner spinner, ArrayAdapter<T> adapter, int selectedIndex,
                                 final AdapterView.OnItemSelectedListener listener) {
        spinner.setOnItemSelectedListener(null);
        spinner.setAdapter(adapter);
        if (selectedIndex >= 0 && selectedIndex < adapter.getCount())
            spinner.setSelection(selectedIndex, false);

        spinner.post(new Runnable() {
            @Override
            public void run() {
                if (!isAdded()) return;
                spinner.setOnItemSelectedListener(listener);
            }
        });
    }

    private AirportsModel createAirportOption(int id, String name) {
        AirportsModel airport = new AirportsModel();
        airport.setId(id);
        airport.setName(name);
        return airport;
    }

    private int indexOfUser(List<UserModel> list, int userId) {
        if (userId <= 0) return -1;
        for (int i = 0; i < list.size(); i++) {
            UserModel u = list.get(i);
            if (u != null && u.getId() != null && u.getId() == userId)
                return i;
        }
        return -1;
    }

    private int indexOfContainer(List<BM2505ContainerModel> list, int containerId) {
        if (containerId <= 0) return -1;
        for (int i = 0; i < list.size(); i++) {
            BM2505ContainerModel c = list.get(i);
            if (c != null && c.getId() != null && c.getId() == containerId)
                return i;
        }
        return -1;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null)
            getDialog().getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    public void onClick(View view) {
        int id = view.getId();
        switch (id) {
            case R.id.rad_flight:
                model.setReportType(0);
                binding.invalidateAll();
                break;
            case R.id.rad_other:
                model.setReportType(1);
                binding.invalidateAll();
                break;
            case R.id.b2505_new_back:
                dismissDialog();
                break;
            case R.id.b2505_new_save:
                save();
                break;
            case R.id.b2505_new_time:
                showTimeDialog(id);
                break;
            case R.id.b2505_new_flight:
                openFlightSelect();
                break;
            case R.id.bm2505_new_depot:
                m_Title = getString(R.string.update_depot);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;
            case R.id.b2505_new_tank_no:
                m_Title = getString(R.string.update_tank_no);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;
            case R.id.b2505_new_hose_pressure:
                m_Title = getString(R.string.update_hose_presure);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;
            case R.id.b2505_new_pressure_diff:
                m_Title = getString(R.string.update_pressure_diff);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;
            case R.id.b2505_new_appearance_cb:
                findFormView(R.id.b2505_new_appearance).setVisibility(View.GONE);
                model.setAppearanceCheck("C&B");
                binding.invalidateAll();
                break;
            case R.id.b2505_new_appearance_other:
            case R.id.b2505_new_appearance:
                findFormView(R.id.b2505_new_appearance).setVisibility(View.VISIBLE);
                m_Title = getString(R.string.update_appearance_check);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;
            case R.id.b2505_new_rtc_no:
                m_Title = getString(R.string.update_rtc_no);
                showEditDialog(id, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                break;
            case R.id.b2505_new_temperature:
                m_Title = getString(R.string.update_temparature);
                showEditDialog(id, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                break;
            case R.id.b2505_new_density:
                m_Title = getString(R.string.update_density);
                showEditDialog(id, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_NORMAL);
                break;
            case R.id.b2505_new_density15:
                m_Title = getString(R.string.update_density15);
                showEditDialog(id, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                break;
            case R.id.b2505_new_density_diff:
                m_Title = getString(R.string.update_density_diff);
                showEditDialog(id, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                break;
            case R.id.b2505_new_note:
                m_Title = getString(R.string.update_note);
                showEditDialog(id, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                break;
            case R.id.b2505_new_max_flowrate:
                m_Title = getString(R.string.update_max_flowrate);
                showEditDialog(id, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                break;
        }
    }

    // =========================
    // SAVE
    // =========================

    private void save() {

        if (isSaving) return;
        if (!masterDataLoaded) return;

        Integer error = validate();
        if (error != null) {
            showError(error);
            return;
        }

        isSaving = true;
        setSaveEnabled(false);

        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... voids) {
                return DataHelper.postBM2505(model);
            }

            @Override
            protected void onPostExecute(Boolean savedLocally) {
                super.onPostExecute(savedLocally);

                if (savedLocally == null || !savedLocally) {
                    // ghi local thất bại: giữ dialog để người dùng thử lại
                    isSaving = false;
                    setSaveEnabled(true);
                    showError(R.string.bm2505_save_failed);
                    return;
                }

                notifySaved();

                if (isAdded())
                    Toast.makeText(requireContext(), R.string.bm2505_saved_pending_sync, Toast.LENGTH_LONG).show();

                dismissDialog();
            }
        }.execute();
    }

    /**
     * @return id chuỗi lỗi, null nếu hợp lệ.
     */
    private Integer validate() {

        if (model.getAirportId() == null || model.getAirportId() <= 0)
            return R.string.bm2505_airport_required;

        if (model.getReportType() == 0) {
            boolean hasFlight = model.getFlightId() > 0
                    || (model.getFlightCode() != null && !model.getFlightCode().trim().isEmpty());
            if (!hasFlight) return R.string.bm2505_flight_required;
        } else if (model.getContainerId() <= 0) {
            return R.string.bm2505_container_required;
        }

        if (model.getOperatorId() <= 0)
            return R.string.bm2505_operator_required;

        if (model.getTemperature() != 0 && (model.getTemperature() < -50 || model.getTemperature() > 100))
            return R.string.bm2505_invalid_temperature;

        if (model.getDensity15() != 0 && (model.getDensity15() < 0.6 || model.getDensity15() > 1.0))
            return R.string.bm2505_invalid_density15;

        if (model.getMaxFlowRate() < 0)
            return R.string.bm2505_invalid_max_flowrate;

        return null;
    }

    private void notifySaved() {
        Activity activity = getActivity();
        if (activity instanceof OnBM2505SavedListener)
            ((OnBM2505SavedListener) activity).onBM2505Saved(model);
    }

    private void setSaveEnabled(boolean enabled) {
        View saveButton = findFormView(R.id.b2505_new_save);
        if (saveButton != null) saveButton.setEnabled(enabled);
    }

    private View findFormView(int id) {
        View root = rootView != null ? rootView : getView();
        return root != null ? root.findViewById(id) : null;
    }

    private void dismissDialog() {
        dismissAllowingStateLoss();
    }

    private void showError(int messageId) {
        Activity activity = getActivity();
        if (activity instanceof BaseActivity)
            ((BaseActivity) activity).showErrorMessage(messageId);
        else if (activity != null)
            Toast.makeText(activity, messageId, Toast.LENGTH_LONG).show();
    }

    // =========================
    // FLIGHT / TIME / EDIT DIALOGS
    // =========================

    private void openFlightSelect() {
        final Dialog flightDlg = new Dialog(requireActivity());
        flightDlg.setTitle(R.string.app_name);
        flightDlg.setContentView(R.layout.flight_select_dialog);
        SearchView searchView = flightDlg.findViewById(R.id.flight_dlg_search);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                ListView lvAirline = flightDlg.findViewById(R.id.list_airline);
                FlightArrayAdapter adapter = (FlightArrayAdapter) lvAirline.getAdapter();
                adapter.getFilter().filter(query);
                adapter.notifyDataSetChanged();
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                ListView lvAirline = flightDlg.findViewById(R.id.list_airline);
                FlightArrayAdapter adapter = (FlightArrayAdapter) lvAirline.getAdapter();
                adapter.getFilter().filter(newText);
                adapter.notifyDataSetChanged();

                return false;
            }
        });
        final ListView lvAirline = flightDlg.findViewById(R.id.list_airline);
        lvAirline.setAdapter(new FlightArrayAdapter(requireActivity(), flights));
        lvAirline.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                FlightModel flightModel = (FlightModel) parent.getItemAtPosition(position);
                model.setFlightCode(flightModel.getFlightCode());
                model.setAircraftCode(flightModel.getAircraftCode());
                model.setFlightId(flightModel.getId());
                FlightArrayAdapter adapter = (FlightArrayAdapter) lvAirline.getAdapter();
                adapter.getFilter().filter("");
                flightDlg.dismiss();

                binding.invalidateAll();
            }

        });
        flightDlg.show();
    }

    private void showTimeDialog(int id) {

        final Date date = new Date();

        if (id == R.id.b2505_new_time && model.getTime() != null)
            date.setTime(model.getTime().getTime());

        final Calendar c = Calendar.getInstance();
        c.setTime(date);
        mYear = c.get(Calendar.YEAR);
        mMonth = c.get(Calendar.MONTH);
        mDay = c.get(Calendar.DAY_OF_MONTH);
        mHour = c.get(Calendar.HOUR_OF_DAY);
        mMinute = c.get(Calendar.MINUTE);

        DatePickerDialog datePickerDialog = new DatePickerDialog(requireContext(), new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                c.set(year, month, dayOfMonth);
                TimePickerDialog timePickerDialog = new TimePickerDialog(requireContext(),
                        new TimePickerDialog.OnTimeSetListener() {
                            @Override
                            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                                c.set(Calendar.MINUTE, minute);
                                c.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                updateTime(id, c);
                            }
                        }, mHour, mMinute, false);
                timePickerDialog.show();
            }
        }, mYear, mMonth, mDay);

        datePickerDialog.show();
    }

    private void updateTime(int id, Calendar c) {

        if (id == R.id.b2505_new_time)
            this.model.setTime(c.getTime());
        binding.invalidateAll();
    }

    private String m_Text = "";
    private String m_Title = "";

    private void showEditDialog(final int id, int inputType) {
        showEditDialog(id, inputType, ".*");
    }

    private void showEditDialog(final int id, int inputType, String pattern) {
        showEditDialog(id, inputType, pattern, false);
    }

    private void showEditDialog(final int id, int inputType, String pattern, final boolean required) {

        Context context = getActivity();
        if (context == null) return;

        final AlertDialog.Builder builder = new AlertDialog.Builder(context);

        builder.setTitle(m_Title);
        final EditText input = new EditText(context);
        input.setInputType(inputType);

        input.setTypeface(Typeface.DEFAULT);

        View currentView = findFormView(id);
        if (currentView instanceof TextView)
            input.setText(((TextView) currentView).getText());

        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setGravity(Gravity.CENTER_HORIZONTAL);
        if ((inputType & InputType.TYPE_NUMBER_FLAG_DECIMAL) > 0)
            input.setKeyListener(DigitsKeyListener.getInstance("0123456789,."));

        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                return actionId == EditorInfo.IME_ACTION_DONE;
            }
        });
        builder.setView(input);

        builder.setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });

        if (!required) {
            builder.setNegativeButton(R.string.back, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });
        }
        final AlertDialog dialog = builder.create();
        dialog.setCancelable(!required);

        dialog.show();

        input.requestFocus();
        int textLength = input.getText().length();
        if (id == R.id.b2505_new_density15 && textLength > 2)
            input.setSelection(2, textLength);
        else
            input.setSelection(0, textLength);

        final String finalPattern = pattern;
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (doUpdateResult())
                    dialog.dismiss();
            }

            private boolean doUpdateResult() {

                m_Text = input.getText().toString().trim();
                if (required && m_Text.isEmpty()) {
                    return false;
                }
                Pattern regex = Pattern.compile(finalPattern);
                Matcher matcher = regex.matcher(m_Text);
                if (!matcher.find()) {
                    return false;
                }
                try {
                    double d;
                    switch (id) {

                        case R.id.bm2505_new_depot:
                            model.setDepot(m_Text);
                            break;
                        case R.id.b2505_new_tank_no:
                            model.setTankNo(m_Text);
                            break;
                        case R.id.b2505_new_rtc_no:
                            model.setRTCNo(m_Text);
                            break;
                        case R.id.b2505_new_temperature:
                            d = parseNumber(m_Text);
                            model.setTemperature(d);
                            break;
                        case R.id.b2505_new_density:
                            d = parseNumber(m_Text);
                            model.setDensity(d);
                            break;
                        case R.id.b2505_new_density15:
                            d = parseNumber(m_Text);
                            model.setDensity15(d);
                            break;
                        case R.id.b2505_new_density_diff:
                            d = parseNumber(m_Text);
                            model.setDensityDiff(d);
                            break;
                        case R.id.b2505_new_appearance_other:
                        case R.id.b2505_new_appearance:
                            model.setAppearanceCheck(m_Text);
                            break;
                        case R.id.b2505_new_pressure_diff:
                            model.setPressureDiff(m_Text);
                            break;
                        case R.id.b2505_new_hose_pressure:
                            model.setHosePressure(m_Text);
                            break;
                        case R.id.b2505_new_note:
                            model.setNote(m_Text);
                            break;
                        case R.id.b2505_new_max_flowrate:
                            d = parseNumber(m_Text);
                            model.setMaxFlowRate(d);
                            break;

                    }
                } catch (ParseException | NumberFormatException ex) {
                    showError(R.string.invalid_number_format);
                    return false;
                }
                binding.invalidateAll();
                return true;
            }
        });
    }

    private double parseNumber(String text) throws ParseException {
        Number number = numberFormat.parse(text);
        if (number == null) throw new ParseException(text, 0);
        return number.doubleValue();
    }
}
