package com.harishun.hidinjector;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements 
        BluetoothHidKeyboard.OnHidStatusListener, 
        ShortcutAdapter.OnShortcutClickListener {

    private static final int PERMISSION_REQUEST_CODE = 101;

    private BluetoothHidKeyboard hidKeyboard;
    private ShortcutManagerHelper shortcutHelper;
    private ShortcutAdapter shortcutAdapter;
    private DuckyInterpreter duckyInterpreter;
    private MouseController mouseController;
    private KeyboardInputHandler keyboardInputHandler;
    private SettingsManager settingsManager;

    private View screenShortcuts, screenInput, screenScripting, screenSettings;
    private LinearLayout navShortcuts, navInput, navScripting, navSettings;
    private FrameLayout pillShortcuts, pillInput, pillScripting, pillSettings;
    private TextView tvNavShortcuts, tvNavInput, tvNavScripting, tvNavSettings;
    private ImageView ivNavShortcuts, ivNavInput, ivNavScripting, ivNavSettings;

    private Spinner spinnerDevices;
    private View viewStatusDot;
    private TextView tvStatusOn;
    
    private List<BluetoothDevice> pairedDevices = new ArrayList<>();
    private List<ShortcutItem> shortcutsList = new ArrayList<>();
    private ItemTouchHelper itemTouchHelper;
    private long lastToastTime = 0;
    private int currentSelectedTab = 0;
    private boolean isSpinnerProgrammaticChange = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        settingsManager = new SettingsManager(this);
        if (settingsManager.isNightMode()) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }

        if (savedInstanceState != null) {
            currentSelectedTab = savedInstanceState.getInt("saved_tab_index", 0);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        shortcutHelper = new ShortcutManagerHelper(this);
        shortcutsList = shortcutHelper.getShortcuts();

        bindViews();
        setupNavigation();

        if (hasBluetoothPermissions()) {
            startHidService();
        } else {
            requestBluetoothPermissions();
        }

        handleShortcutIntent(getIntent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasBluetoothPermissions()) {
            if (hidKeyboard == null) {
                startHidService();
            } else if (!hidKeyboard.isServiceReady()) {
                hidKeyboard.setupService();
                refreshDeviceList();
            } else {
                refreshDeviceList();
                if (hidKeyboard.isConnected()) {
                    viewStatusDot.setBackgroundColor(ContextCompat.getColor(this, R.color.status_green));
                    tvStatusOn.setText(R.string.status_connected);
                    tvStatusOn.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                } else {
                    autoConnectLastDevice();
                }
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("saved_tab_index", currentSelectedTab);
    }

    private void bindViews() {
        ImageView btnTheme = findViewById(R.id.btn_theme);
        if (btnTheme != null) {
            boolean isNight = settingsManager.isNightMode();
            btnTheme.setImageResource(isNight ? R.drawable.light_mode_24 : R.drawable.dark_mode_24);
            btnTheme.setColorFilter(isNight ? Color.parseColor("#FBBF24") : Color.WHITE);
            btnTheme.setOnClickListener(v -> toggleTheme());
        }

        View refreshBtn = findViewById(R.id.btn_refresh_bar);
        if (refreshBtn != null) {
            refreshBtn.setOnClickListener(v -> refreshAllSystems());
        }

        spinnerDevices = findViewById(R.id.spinner_devices);
        viewStatusDot = findViewById(R.id.view_status_dot);
        tvStatusOn = findViewById(R.id.tv_status_on);

        screenShortcuts = findViewById(R.id.screen_shortcuts);
        screenInput = findViewById(R.id.screen_input);
        screenScripting = findViewById(R.id.screen_scripting);
        screenSettings = findViewById(R.id.screen_settings);

        navShortcuts = findViewById(R.id.nav_shortcuts);
        navInput = findViewById(R.id.nav_input);
        navScripting = findViewById(R.id.nav_scripting);
        navSettings = findViewById(R.id.nav_settings);

        pillShortcuts = findViewById(R.id.container_shortcuts_pill);
        pillInput = findViewById(R.id.container_input_pill);
        pillScripting = findViewById(R.id.container_scripting_pill);
        pillSettings = findViewById(R.id.container_settings_pill);

        tvNavShortcuts = findViewById(R.id.tv_nav_shortcuts);
        tvNavInput = findViewById(R.id.tv_nav_input);
        tvNavScripting = findViewById(R.id.tv_nav_scripting);
        tvNavSettings = findViewById(R.id.tv_nav_settings);

        ivNavShortcuts = findViewById(R.id.iv_nav_shortcuts);
        ivNavInput = findViewById(R.id.iv_nav_input);
        ivNavScripting = findViewById(R.id.iv_nav_scripting);
        ivNavSettings = findViewById(R.id.iv_nav_settings);
    }

    private void toggleTheme() {
        boolean isNight = !settingsManager.isNightMode();
        settingsManager.setNightMode(isNight);
        if (isNight) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
        recreate();
    }

    private void setupNavigation() {
        navShortcuts.setOnClickListener(v -> selectTab(0));
        navInput.setOnClickListener(v -> selectTab(1));
        navScripting.setOnClickListener(v -> selectTab(2));
        navSettings.setOnClickListener(v -> selectTab(3));
        int initialTab = currentSelectedTab;
        currentSelectedTab = -1;
        selectTab(initialTab);
    }

    private void selectTab(int index) {
        boolean isInitial = (currentSelectedTab == -1);
        currentSelectedTab = index;
        screenShortcuts.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        screenInput.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        screenScripting.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        screenSettings.setVisibility(index == 3 ? View.VISIBLE : View.GONE);

        FrameLayout[] pills = new FrameLayout[]{pillShortcuts, pillInput, pillScripting, pillSettings};
        TextView[] labels = new TextView[]{tvNavShortcuts, tvNavInput, tvNavScripting, tvNavSettings};
        ImageView[] icons = new ImageView[]{ivNavShortcuts, ivNavInput, ivNavScripting, ivNavSettings};

        int iconActiveColor = ContextCompat.getColor(this, R.color.nav_selected_icon);
        int textActiveColor = ContextCompat.getColor(this, R.color.text_primary);
        int inactiveColor = ContextCompat.getColor(this, R.color.text_secondary);
        float floatUpDistance = -dpToPx(7);

        for (int i = 0; i < 4; i++) {
            final FrameLayout pill = pills[i];
            final TextView label = labels[i];
            final ImageView icon = icons[i];
            boolean isSelected = (i == index);

            if (label != null) {
                label.setTextColor(isSelected ? textActiveColor : inactiveColor);
                label.setTypeface(null, isSelected ? Typeface.BOLD : Typeface.NORMAL);
            }
            if (icon != null) {
                icon.setColorFilter(isSelected ? iconActiveColor : inactiveColor);
            }

            if (pill != null) {
                if (isSelected) {
                    pill.setBackgroundResource(R.drawable.bottom_nav_pill);
                    if (!isInitial) {
                        pill.animate()
                                .translationY(floatUpDistance)
                                .scaleX(1.15f)
                                .scaleY(1.15f)
                                .setDuration(220)
                                .setInterpolator(new OvershootInterpolator(1.3f))
                                .start();
                    } else {
                        pill.setTranslationY(floatUpDistance);
                        pill.setScaleX(1.15f);
                        pill.setScaleY(1.15f);
                    }
                } else {
                    if (!isInitial) {
                        pill.animate()
                                .translationY(0f)
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(180)
                                .setInterpolator(new AccelerateDecelerateInterpolator())
                                .withEndAction(() -> pill.setBackgroundResource(0))
                                .start();
                    } else {
                        pill.setTranslationY(0f);
                        pill.setScaleX(1.0f);
                        pill.setScaleY(1.0f);
                        pill.setBackgroundResource(0);
                    }
                }
            }
        }

        if (index == 1 && mouseController == null) setupInputScreenComponents();
        if (index == 2) setupScriptingScreenComponents();
        if (index == 3) setupSettingsScreenComponents();
    }

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private boolean hasBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE},
                    PERMISSION_REQUEST_CODE);
        }
    }

    private void startHidService() {
        hidKeyboard = new BluetoothHidKeyboard(this, this);
        hidKeyboard.setupService();
        duckyInterpreter = new DuckyInterpreter(hidKeyboard);

        refreshDeviceList();
        setupShortcutsGrid();
    }

    private void refreshAllSystems() {
        Toast.makeText(this, R.string.toast_refreshing, Toast.LENGTH_SHORT).show();
        if (hidKeyboard != null) {
            hidKeyboard.cleanup();
        }
        hidKeyboard = new BluetoothHidKeyboard(this, this);
        hidKeyboard.setupService();
        duckyInterpreter = new DuckyInterpreter(hidKeyboard);

        // Rebind input screen controllers to the new service instance
        if (mouseController != null || keyboardInputHandler != null) {
            setupInputScreenComponents();
        }

        refreshDeviceList();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startHidService();
        } else {
            Toast.makeText(this, R.string.toast_bt_permission_required, Toast.LENGTH_LONG).show();
        }
    }

    private void showConnectionToast() {
        runOnUiThread(() -> {
            long now = System.currentTimeMillis();
            if (now - lastToastTime > 3000) {
                Toast.makeText(this, R.string.toast_no_device, Toast.LENGTH_SHORT).show();
                lastToastTime = now;
            }
        });
    }

    private boolean checkConnectionOrWarn() {
        if (hidKeyboard == null || !hidKeyboard.isConnected()) {
            showConnectionToast();
            return false;
        }
        return true;
    }

    private void refreshDeviceList() {
        if (hidKeyboard == null) return;
        pairedDevices = hidKeyboard.getPairedDevices();
        List<String> names = new ArrayList<>();
        names.add(getString(R.string.select_device));

        String lastAddress = settingsManager.getLastConnectedDeviceAddress();
        int autoConnectPosition = -1;

        for (int i = 0; i < pairedDevices.size(); i++) {
            BluetoothDevice dev = pairedDevices.get(i);
            @SuppressLint("MissingPermission")
            String name = dev.getName() != null ? dev.getName() : dev.getAddress();
            names.add(name);

            if (lastAddress != null && dev.getAddress().equalsIgnoreCase(lastAddress)) {
                autoConnectPosition = i + 1;
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.item_spinner_dropdown, R.id.tv_dropdown_text, names) {
            @NonNull
            @Override
            public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                ImageView icon = view.findViewById(R.id.iv_dropdown_icon);
                if (icon != null) {
                    icon.setVisibility(position == 0 ? View.GONE : View.VISIBLE);
                }
                return view;
            }
        };
        isSpinnerProgrammaticChange = true;
        spinnerDevices.setAdapter(adapter);
        spinnerDevices.setPopupBackgroundResource(R.drawable.dialog_card_background);
        spinnerDevices.post(() -> {
            int w = spinnerDevices.getWidth();
            if (w > 0) {
                spinnerDevices.setDropDownWidth(w);
                spinnerDevices.setDropDownHorizontalOffset(-spinnerDevices.getPaddingLeft());
            }
            isSpinnerProgrammaticChange = false;
        });

        if (autoConnectPosition != -1) {
            spinnerDevices.setSelection(autoConnectPosition);
        }

        spinnerDevices.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isSpinnerProgrammaticChange) {
                    return;
                }
                if (position == 0) {
                    if (hidKeyboard != null) {
                        hidKeyboard.disconnect();
                    }
                } else if (position > 0 && position <= pairedDevices.size()) {
                    BluetoothDevice selected = pairedDevices.get(position - 1);
                    if (hidKeyboard != null) {
                        hidKeyboard.connectToDevice(selected);
                    }
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupShortcutsGrid() {
        RecyclerView rvShortcuts = findViewById(R.id.rv_shortcuts);
        rvShortcuts.setLayoutManager(new GridLayoutManager(this, 2));

        shortcutAdapter = new ShortcutAdapter(this, shortcutsList, shortcutHelper, this);
        rvShortcuts.setAdapter(shortcutAdapter);

        ItemTouchHelper.SimpleCallback simpleCallback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.START | ItemTouchHelper.END, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                if (target.getItemViewType() == 1) return false;
                shortcutAdapter.onItemMove(viewHolder.getBindingAdapterPosition(), target.getBindingAdapterPosition());
                return true;
            }
            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}
        };

        itemTouchHelper = new ItemTouchHelper(simpleCallback);
        itemTouchHelper.attachToRecyclerView(rvShortcuts);
    }

    @Override
    public void onShortcutClick(ShortcutItem item) {
        if (!checkConnectionOrWarn()) return;
        if (duckyInterpreter != null) {
            Toast.makeText(this, getString(R.string.toast_executing, item.name), Toast.LENGTH_SHORT).show();
            duckyInterpreter.execute(item.script, null);
        }
    }

    @Override
    public void onShortcutDoublePress(ShortcutItem item) {
        showEditShortcutDialog(item);
    }

    @Override
    public void onAddShortcutClick() {
        showAddShortcutDialog();
    }

    @Override
    public void onStartDrag(RecyclerView.ViewHolder viewHolder) {
        if (itemTouchHelper != null && viewHolder.getItemViewType() == 0) {
            itemTouchHelper.startDrag(viewHolder);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupInputScreenComponents() {
        if (hidKeyboard == null) return;
        mouseController = new MouseController(hidKeyboard, settingsManager, this::showConnectionToast);
        keyboardInputHandler = new KeyboardInputHandler(hidKeyboard, this::showConnectionToast);

        View trackpad = findViewById(R.id.trackpad_area);
        mouseController.setupTrackpad(trackpad);

        Button left = findViewById(R.id.btn_mouse_left);
        Button middle = findViewById(R.id.btn_mouse_middle);
        Button right = findViewById(R.id.btn_mouse_right);

        left.setOnTouchListener((v, e) -> handleMouseButtonTouch(e, (byte) 0x01));
        right.setOnTouchListener((v, e) -> handleMouseButtonTouch(e, (byte) 0x02));
        middle.setOnTouchListener((v, e) -> handleMouseButtonTouch(e, (byte) 0x04));

        EditText etRealtime = findViewById(R.id.et_realtime_keyboard);
        keyboardInputHandler.setupKeyboard(etRealtime);
    }

    private boolean handleMouseButtonTouch(MotionEvent e, byte buttonMask) {
        if (!checkConnectionOrWarn()) return false;
        if (mouseController == null) return false;
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            mouseController.sendMouseButton(true, buttonMask);
            return true;
        } else if (e.getAction() == MotionEvent.ACTION_UP) {
            mouseController.sendMouseButton(false, buttonMask);
            return true;
        }
        return false;
    }

    private void setupScriptingScreenComponents() {
        View executeBtn = findViewById(R.id.btn_execute_script);
        EditText etScript = findViewById(R.id.et_ducky_script);
        TextView tvStatus = findViewById(R.id.tv_script_status);

        View libBtn = findViewById(R.id.btn_script_library);
        View saveBtn = findViewById(R.id.btn_script_save);

        executeBtn.setOnClickListener(v -> {
            if (!checkConnectionOrWarn()) return;
            String script = etScript.getText().toString();
            if (script.isEmpty()) return;
            executeBtn.setEnabled(false);
            tvStatus.setText(R.string.status_executing);
            duckyInterpreter.execute(script, new DuckyInterpreter.InterpreterListener() {
                @Override
                public void onScriptFinished() {
                    runOnUiThread(() -> {
                        executeBtn.setEnabled(true);
                        tvStatus.setText(R.string.status_completed);
                    });
                }
                @Override
                public void onScriptError(String error) {
                    runOnUiThread(() -> {
                        executeBtn.setEnabled(true);
                        tvStatus.setText(getString(R.string.status_error_format, error));
                    });
                }
                @Override
                public void onCommandExecuted(String cmd) {
                    runOnUiThread(() -> tvStatus.setText(getString(R.string.command_exec_format, cmd)));
                }
            });
        });

        saveBtn.setOnClickListener(v -> {
            String script = etScript.getText().toString();
            if (!script.isEmpty()) showSaveScriptDialog(script);
        });

        libBtn.setOnClickListener(v -> showLibraryDialog(etScript));
    }

    private void setupSettingsScreenComponents() {
        Spinner osSpinner = findViewById(R.id.spinner_settings_os);
        Spinner layoutSpinner = findViewById(R.id.spinner_settings_layout);
        SeekBar sensitivitySeek = findViewById(R.id.seekbar_sensitivity);
        TextView tvSensVal = findViewById(R.id.tv_sensitivity_value);
        Button helpBtn = findViewById(R.id.btn_settings_help);
        Button discoverableBtn = findViewById(R.id.btn_settings_discoverable);

        ArrayAdapter<CharSequence> osAdapter = ArrayAdapter.createFromResource(this,
                R.array.target_os_array, R.layout.item_spinner_text);
        osAdapter.setDropDownViewResource(R.layout.item_spinner_text_dropdown);
        osSpinner.setAdapter(osAdapter);
        osSpinner.setPopupBackgroundResource(R.drawable.dialog_card_background);
        osSpinner.post(() -> {
            int w = osSpinner.getWidth();
            if (w > 0) {
                osSpinner.setDropDownWidth(w);
                osSpinner.setDropDownHorizontalOffset(-osSpinner.getPaddingLeft());
            }
        });
        osSpinner.setSelection(getTargetOSIndex(settingsManager.getTargetOS()));
        osSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                settingsManager.setTargetOS(osSpinner.getSelectedItem().toString());
            }
            @Override
            public void onNothingSelected(AdapterView<?> p) {}
        });

        ArrayAdapter<CharSequence> layoutAdapter = ArrayAdapter.createFromResource(this,
                R.array.keyboard_layouts_array, R.layout.item_spinner_text);
        layoutAdapter.setDropDownViewResource(R.layout.item_spinner_text_dropdown);
        layoutSpinner.setAdapter(layoutAdapter);
        layoutSpinner.setPopupBackgroundResource(R.drawable.dialog_card_background);
        layoutSpinner.post(() -> {
            int w = layoutSpinner.getWidth();
            if (w > 0) {
                layoutSpinner.setDropDownWidth(w);
                layoutSpinner.setDropDownHorizontalOffset(-layoutSpinner.getPaddingLeft());
            }
        });
        layoutSpinner.setSelection(getLayoutIndex(settingsManager.getKeyboardLayout()));
        layoutSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                settingsManager.setKeyboardLayout(layoutSpinner.getSelectedItem().toString());
            }
            @Override
            public void onNothingSelected(AdapterView<?> p) {}
        });

        float sens = settingsManager.getSensitivity();
        sensitivitySeek.setProgress(Math.round((sens - 0.5f) * 10f));
        tvSensVal.setText(String.valueOf(sens));
        sensitivitySeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float actualSens = 0.5f + (progress / 10f);
                tvSensVal.setText(String.valueOf(actualSens));
                settingsManager.setSensitivity(actualSens);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        helpBtn.setOnClickListener(v -> showHelpDialog());
        discoverableBtn.setOnClickListener(v -> makeDiscoverable());
    }

    @SuppressLint("MissingPermission")
    private void makeDiscoverable() {
        if (hasBluetoothPermissions()) {
            try {
                Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
                startActivity(discoverableIntent);
            } catch (Exception e) {
                Toast.makeText(this, R.string.toast_discoverable_failed, Toast.LENGTH_SHORT).show();
            }
        } else {
            requestBluetoothPermissions();
        }
    }

    private int getTargetOSIndex(String os) {
        if (os.equals("macOS")) return 1;
        if (os.equals("Linux")) return 2;
        return 0;
    }

    private int getLayoutIndex(String l) {
        if (l.contains("UK")) return 1;
        if (l.contains("French")) return 2;
        if (l.contains("German")) return 3;
        return 0;
    }

    private static class IconOption {
        final String name;
        final String key;
        final int iconRes;

        IconOption(String name, String key, int iconRes) {
            this.name = name;
            this.key = key;
            this.iconRes = iconRes;
        }
    }

    private void applyBlurAndDimToDialog(AlertDialog dialog) {
        if (dialog != null && dialog.getWindow() != null) {
            Window window = dialog.getWindow();
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0.65f);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                window.getAttributes().setBlurBehindRadius(35);
            }
        }
    }

    private void showAddShortcutDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_shortcut, null);
        ImageView closeBtn = view.findViewById(R.id.dialog_btn_close);
        EditText etName = view.findViewById(R.id.dialog_et_name);
        EditText etScript = view.findViewById(R.id.dialog_et_script);
        Spinner spIcon = view.findViewById(R.id.dialog_sp_icon);
        View pinBtn = view.findViewById(R.id.dialog_btn_pin);
        View delBtn = view.findViewById(R.id.dialog_btn_delete);
        View saveBtn = view.findViewById(R.id.dialog_btn_save);

        pinBtn.setVisibility(View.GONE);
        delBtn.setVisibility(View.GONE);

        setupIconSpinner(spIcon, "bolt");
        builder.setView(view);

        AlertDialog dialog = builder.create();

        closeBtn.setOnClickListener(v -> dialog.dismiss());

        saveBtn.setOnClickListener(v -> {
            String name = etName.getText().toString();
            String script = etScript.getText().toString();
            IconOption selected = (IconOption) spIcon.getSelectedItem();
            String icon = selected != null ? selected.key : "bolt";

            if (!name.isEmpty()) {
                String id = UUID.randomUUID().toString();
                shortcutsList.add(new ShortcutItem(id, name, script, icon));
                shortcutAdapter.notifyItemInserted(shortcutsList.size() - 1);
                shortcutHelper.saveShortcuts(shortcutsList);
            }
            dialog.dismiss();
        });

        applyBlurAndDimToDialog(dialog);
        dialog.show();
    }

    private void showEditShortcutDialog(ShortcutItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_shortcut, null);
        ImageView closeBtn = view.findViewById(R.id.dialog_btn_close);
        EditText etName = view.findViewById(R.id.dialog_et_name);
        EditText etScript = view.findViewById(R.id.dialog_et_script);
        Spinner spIcon = view.findViewById(R.id.dialog_sp_icon);
        View pinBtn = view.findViewById(R.id.dialog_btn_pin);
        View delBtn = view.findViewById(R.id.dialog_btn_delete);
        View saveBtn = view.findViewById(R.id.dialog_btn_save);

        etName.setText(item.name);
        etScript.setText(item.script);
        setupIconSpinner(spIcon, item.iconName);

        builder.setView(view);
        AlertDialog dialog = builder.create();

        closeBtn.setOnClickListener(v -> dialog.dismiss());

        saveBtn.setOnClickListener(v -> {
            item.name = etName.getText().toString();
            item.script = etScript.getText().toString();
            IconOption selected = (IconOption) spIcon.getSelectedItem();
            item.iconName = selected != null ? selected.key : "bolt";

            int idx = shortcutsList.indexOf(item);
            if (idx != -1) {
                shortcutAdapter.notifyItemChanged(idx);
                shortcutHelper.saveShortcuts(shortcutsList);
            }
            dialog.dismiss();
        });

        pinBtn.setOnClickListener(v -> {
            boolean success = shortcutHelper.pinShortcutToHomeScreen(item);
            Toast.makeText(this, success ? getString(R.string.toast_pinned_success) : getString(R.string.toast_pinned_failure), Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        delBtn.setOnClickListener(v -> {
            int idx = shortcutsList.indexOf(item);
            if (idx != -1) {
                shortcutsList.remove(idx);
                shortcutAdapter.notifyItemRemoved(idx);
                shortcutHelper.saveShortcuts(shortcutsList);
            }
            dialog.dismiss();
        });

        applyBlurAndDimToDialog(dialog);
        dialog.show();
    }

    private void setupIconSpinner(Spinner spinner, String selectedIcon) {
        List<IconOption> options = new ArrayList<>();
        options.add(new IconOption("Bolt", "bolt", R.drawable.ic_shortcut_bolt));
        options.add(new IconOption("Lock", "lock", R.drawable.ic_shortcut_lock));
        options.add(new IconOption("Terminal", "terminal", R.drawable.ic_shortcut_terminal));
        options.add(new IconOption("Key", "key", R.drawable.ic_shortcut_key));
        options.add(new IconOption("Web", "web", R.drawable.ic_shortcut_web));
        options.add(new IconOption("Keyboard", "keyboard", R.drawable.ic_shortcut_keyboard));
        options.add(new IconOption("Rocket", "rocket", R.drawable.ic_shortcut_rocket));
        options.add(new IconOption("Folder", "folder", R.drawable.ic_shortcut_folder));
        options.add(new IconOption("Shield", "shield", R.drawable.ic_shortcut_shield));
        options.add(new IconOption("Power", "power", R.drawable.ic_shortcut_power));
        options.add(new IconOption("Media", "media", R.drawable.ic_shortcut_media));
        options.add(new IconOption("Clipboard", "clipboard", R.drawable.ic_shortcut_clipboard));
        options.add(new IconOption("Laptop", "laptop", R.drawable.ic_shortcut_laptop));
        options.add(new IconOption("Code", "code", R.drawable.ic_shortcut_code));

        ArrayAdapter<IconOption> adapter = new ArrayAdapter<IconOption>(this, R.layout.item_icon_spinner, options) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                return createCustomView(position, convertView, parent);
            }

            @Override
            public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
                return createCustomView(position, convertView, parent);
            }

            private View createCustomView(int position, View convertView, ViewGroup parent) {
                View row = convertView;
                if (row == null) {
                    row = LayoutInflater.from(getContext()).inflate(R.layout.item_icon_spinner, parent, false);
                }
                IconOption item = getItem(position);
                if (item != null) {
                    ImageView iv = row.findViewById(R.id.iv_spinner_icon);
                    TextView tv = row.findViewById(R.id.tv_spinner_name);
                    iv.setImageResource(item.iconRes);
                    tv.setText(item.name);
                }
                return row;
            }
        };

        spinner.setAdapter(adapter);
        spinner.setPopupBackgroundResource(R.drawable.dialog_card_background);
        spinner.post(() -> {
            int w = spinner.getWidth();
            if (w > 0) {
                spinner.setDropDownWidth(w);
                spinner.setDropDownHorizontalOffset(-spinner.getPaddingLeft());
            }
        });

        int selectIdx = 0;
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).key.equalsIgnoreCase(selectedIcon)) {
                selectIdx = i;
                break;
            }
        }
        spinner.setSelection(selectIdx);
    }

    private void showSaveScriptDialog(String script) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_save_script, null);
        EditText etName = view.findViewById(R.id.dialog_et_script_save_name);
        ImageView closeBtn = view.findViewById(R.id.dialog_btn_close_save);
        Button saveBtn = view.findViewById(R.id.dialog_btn_confirm_save_script);

        builder.setView(view);
        AlertDialog dialog = builder.create();

        closeBtn.setOnClickListener(v -> dialog.dismiss());
        saveBtn.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (!name.isEmpty()) {
                SharedPreferences libPrefs = getSharedPreferences("script_library", MODE_PRIVATE);
                libPrefs.edit().putString(name, script).apply();
                Toast.makeText(this, R.string.toast_save_success, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });

        applyBlurAndDimToDialog(dialog);
        dialog.show();
    }

    private void showLibraryDialog(EditText editor) {
        SharedPreferences libPrefs = getSharedPreferences("script_library", MODE_PRIVATE);
        Map<String, ?> all = libPrefs.getAll();
        List<String> names = new ArrayList<>(all.keySet());

        if (names.isEmpty()) {
            Toast.makeText(this, R.string.toast_library_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_select_script, null);
        ImageView closeBtn = view.findViewById(R.id.dialog_btn_close_library);
        ListView listView = view.findViewById(R.id.lv_script_library);

        builder.setView(view);
        AlertDialog dialog = builder.create();

        closeBtn.setOnClickListener(v -> dialog.dismiss());

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.item_script_library, names) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View row = convertView;
                if (row == null) {
                    row = LayoutInflater.from(getContext()).inflate(R.layout.item_script_library, parent, false);
                }
                String name = getItem(position);
                TextView tvName = row.findViewById(R.id.tv_script_library_name);
                ImageView btnDelete = row.findViewById(R.id.btn_delete_library_script);

                if (name != null) {
                    tvName.setText(name);
                }

                row.setOnClickListener(v -> {
                    if (name != null) {
                        String script = libPrefs.getString(name, "");
                        editor.setText(script);
                    }
                    dialog.dismiss();
                });

                btnDelete.setOnClickListener(v -> {
                    if (name != null) {
                        libPrefs.edit().remove(name).apply();
                        remove(name);
                        notifyDataSetChanged();
                        Toast.makeText(MainActivity.this, getString(R.string.toast_deleted, name), Toast.LENGTH_SHORT).show();
                        if (isEmpty()) {
                            dialog.dismiss();
                            Toast.makeText(MainActivity.this, R.string.toast_library_empty, Toast.LENGTH_SHORT).show();
                        }
                    }
                });

                return row;
            }
        };

        listView.setAdapter(adapter);
        applyBlurAndDimToDialog(dialog);
        dialog.show();
    }

    private void showHelpDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_help, null);
        ImageView closeBtn = view.findViewById(R.id.dialog_btn_close_help);
        Button dismissBtn = view.findViewById(R.id.dialog_btn_dismiss_help);

        builder.setView(view);
        AlertDialog dialog = builder.create();

        closeBtn.setOnClickListener(v -> dialog.dismiss());
        dismissBtn.setOnClickListener(v -> dialog.dismiss());

        applyBlurAndDimToDialog(dialog);
        dialog.show();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleShortcutIntent(intent);
    }

    private void handleShortcutIntent(Intent intent) {
        if (intent == null) return;
        if ("com.harishun.hidinjector.ACTION_EXECUTE_SHORTCUT".equals(intent.getAction())) {
            String script = intent.getStringExtra("extra_script");
            String name = intent.getStringExtra("extra_name");
            if (script != null) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (checkConnectionOrWarn() && duckyInterpreter != null) {
                        Toast.makeText(this, getString(R.string.toast_executing, (name != null ? name : getString(R.string.launcher_macro))), Toast.LENGTH_SHORT).show();
                        duckyInterpreter.execute(script, null);
                    }
                }, 1000);
            }
        }
    }

    private void autoConnectLastDevice() {
        if (hidKeyboard == null || hidKeyboard.isConnected() || !hidKeyboard.isServiceReady()) return;
        String lastAddress = settingsManager.getLastConnectedDeviceAddress();
        if (lastAddress == null) return;

        for (BluetoothDevice dev : pairedDevices) {
            if (dev.getAddress().equalsIgnoreCase(lastAddress)) {
                hidKeyboard.connectToDevice(dev);
                break;
            }
        }
    }

    @Override
    public void onRegistrationStatusChanged(boolean isRegistered) {
        runOnUiThread(() -> {
            if (isRegistered) {
                if (hidKeyboard != null && hidKeyboard.isConnected()) {
                    tvStatusOn.setText(R.string.status_connected);
                    tvStatusOn.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                    viewStatusDot.setBackgroundColor(ContextCompat.getColor(this, R.color.status_green));
                } else {
                    tvStatusOn.setText(R.string.status_on);
                    tvStatusOn.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                    viewStatusDot.setBackgroundColor(ContextCompat.getColor(this, R.color.status_green));
                    autoConnectLastDevice();
                }
            } else {
                tvStatusOn.setText(R.string.status_off);
                tvStatusOn.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                viewStatusDot.setBackgroundColor(ContextCompat.getColor(this, R.color.status_red));
            }
        });
    }

    @Override
    public void onConnectionStatusChanged(BluetoothDevice device, int state) {
        if (state == BluetoothProfile.STATE_CONNECTED && device != null) {
            settingsManager.setLastConnectedDeviceAddress(device.getAddress());
        }
        runOnUiThread(() -> {
            if (state == BluetoothProfile.STATE_CONNECTED) {
                viewStatusDot.setBackgroundColor(ContextCompat.getColor(this, R.color.status_green));
                tvStatusOn.setText(R.string.status_connected);
                tvStatusOn.setTextColor(ContextCompat.getColor(this, R.color.text_primary));

                if (device != null && spinnerDevices != null) {
                    isSpinnerProgrammaticChange = true;
                    for (int i = 0; i < pairedDevices.size(); i++) {
                        if (pairedDevices.get(i).getAddress().equalsIgnoreCase(device.getAddress())) {
                            spinnerDevices.setSelection(i + 1);
                            break;
                        }
                    }
                    isSpinnerProgrammaticChange = false;
                }
            } else if (state == BluetoothProfile.STATE_CONNECTING) {
                viewStatusDot.setBackgroundColor(ContextCompat.getColor(this, R.color.status_yellow));
                tvStatusOn.setText(R.string.status_connecting);
                tvStatusOn.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            } else {
                // STATE_DISCONNECTED: Service remains ON and ready, waiting for device selection
                if (hidKeyboard != null && hidKeyboard.isServiceReady()) {
                    viewStatusDot.setBackgroundColor(ContextCompat.getColor(this, R.color.status_green));
                    tvStatusOn.setText(R.string.status_on);
                    tvStatusOn.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                } else {
                    viewStatusDot.setBackgroundColor(ContextCompat.getColor(this, R.color.status_red));
                    tvStatusOn.setText(R.string.status_off);
                    tvStatusOn.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                }

                if (spinnerDevices != null) {
                    isSpinnerProgrammaticChange = true;
                    spinnerDevices.setSelection(0);
                    isSpinnerProgrammaticChange = false;
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (hidKeyboard != null) {
            hidKeyboard.cleanup();
        }
    }
}