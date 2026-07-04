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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
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

    private Spinner spinnerDevices;
    private View viewStatusDot;
    private TextView tvStatusOn;
    
    private List<BluetoothDevice> pairedDevices = new ArrayList<>();
    private List<ShortcutItem> shortcutsList = new ArrayList<>();
    private ItemTouchHelper itemTouchHelper;
    private long lastToastTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        settingsManager = new SettingsManager(this);
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

    private void bindViews() {
        findViewById(R.id.btn_theme).setOnClickListener(v -> 
            Toast.makeText(this, "Theme switching is PRO feature.", Toast.LENGTH_SHORT).show());
        findViewById(R.id.btn_refresh_bar).setOnClickListener(v -> refreshDeviceList());

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
    }

    private void setupNavigation() {
        navShortcuts.setOnClickListener(v -> selectTab(0));
        navInput.setOnClickListener(v -> selectTab(1));
        navScripting.setOnClickListener(v -> selectTab(2));
        navSettings.setOnClickListener(v -> selectTab(3));
        selectTab(0);
    }

    private void selectTab(int index) {
        screenShortcuts.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        screenInput.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        screenScripting.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        screenSettings.setVisibility(index == 3 ? View.VISIBLE : View.GONE);

        pillShortcuts.setBackgroundResource(index == 0 ? R.drawable.bottom_nav_pill : 0);
        pillInput.setBackgroundResource(index == 1 ? R.drawable.bottom_nav_pill : 0);
        pillScripting.setBackgroundResource(index == 2 ? R.drawable.bottom_nav_pill : 0);
        pillSettings.setBackgroundResource(index == 3 ? R.drawable.bottom_nav_pill : 0);

        int primaryColor = ContextCompat.getColor(this, R.color.text_primary);
        int secondaryColor = ContextCompat.getColor(this, R.color.text_secondary);

        tvNavShortcuts.setTextColor(index == 0 ? primaryColor : secondaryColor);
        tvNavInput.setTextColor(index == 1 ? primaryColor : secondaryColor);
        tvNavScripting.setTextColor(index == 2 ? primaryColor : secondaryColor);
        tvNavSettings.setTextColor(index == 3 ? primaryColor : secondaryColor);

        if (index == 1 && mouseController == null) setupInputScreenComponents();
        if (index == 2) setupScriptingScreenComponents();
        if (index == 3) setupSettingsScreenComponents();
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

        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            startActivity(discoverableIntent);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startHidService();
        } else {
            Toast.makeText(this, "Bluetooth permissions required.", Toast.LENGTH_LONG).show();
        }
    }

    private void showConnectionToast() {
        runOnUiThread(() -> {
            long now = System.currentTimeMillis();
            if (now - lastToastTime > 3000) {
                Toast.makeText(this, "No device connected. Please select a target device.", Toast.LENGTH_SHORT).show();
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
        names.add("Select device...");

        for (BluetoothDevice dev : pairedDevices) {
            @SuppressLint("MissingPermission")
            String name = dev.getName() != null ? dev.getName() : dev.getAddress();
            names.add(name);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDevices.setAdapter(adapter);

        spinnerDevices.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0 && position <= pairedDevices.size()) {
                    BluetoothDevice selected = pairedDevices.get(position - 1);
                    hidKeyboard.connectToDevice(selected);
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
                shortcutAdapter.onItemMove(viewHolder.getAdapterPosition(), target.getAdapterPosition());
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
            Toast.makeText(this, "Executing: " + item.name, Toast.LENGTH_SHORT).show();
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
        Button executeBtn = findViewById(R.id.btn_execute_script);
        EditText etScript = findViewById(R.id.et_ducky_script);
        TextView tvStatus = findViewById(R.id.tv_script_status);

        Button libBtn = findViewById(R.id.btn_script_library);
        Button saveBtn = findViewById(R.id.btn_script_save);

        executeBtn.setOnClickListener(v -> {
            if (!checkConnectionOrWarn()) return;
            String script = etScript.getText().toString();
            if (script.isEmpty()) return;
            executeBtn.setEnabled(false);
            tvStatus.setText("Status: Executing...");
            duckyInterpreter.execute(script, new DuckyInterpreter.InterpreterListener() {
                @Override
                public void onScriptFinished() {
                    runOnUiThread(() -> {
                        executeBtn.setEnabled(true);
                        tvStatus.setText("Status: Completed");
                    });
                }
                @Override
                public void onScriptError(String error) {
                    runOnUiThread(() -> {
                        executeBtn.setEnabled(true);
                        tvStatus.setText("Status: Error - " + error);
                    });
                }
                @Override
                public void onCommandExecuted(String cmd) {
                    runOnUiThread(() -> tvStatus.setText("Command: " + cmd));
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
                R.array.target_os_array, android.R.layout.simple_spinner_item);
        osAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        osSpinner.setAdapter(osAdapter);
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
                R.array.keyboard_layouts_array, android.R.layout.simple_spinner_item);
        layoutAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        layoutSpinner.setAdapter(layoutAdapter);
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
        discoverableBtn.setOnClickListener(v -> {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        });
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

    private void showAddShortcutDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Shortcut Card");

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_shortcut, null);
        EditText etName = view.findViewById(R.id.dialog_et_name);
        EditText etScript = view.findViewById(R.id.dialog_et_script);
        Spinner spIcon = view.findViewById(R.id.dialog_sp_icon);
        View pinBtn = view.findViewById(R.id.dialog_btn_pin);
        View delBtn = view.findViewById(R.id.dialog_btn_delete);

        pinBtn.setVisibility(View.GONE);
        delBtn.setVisibility(View.GONE);

        setupIconSpinner(spIcon, "bolt");
        builder.setView(view);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String name = etName.getText().toString();
            String script = etScript.getText().toString();
            String icon = spIcon.getSelectedItem().toString().toLowerCase();

            if (!name.isEmpty()) {
                String id = UUID.randomUUID().toString();
                shortcutsList.add(new ShortcutItem(id, name, script, icon));
                shortcutAdapter.notifyItemInserted(shortcutsList.size() - 1);
                shortcutHelper.saveShortcuts(shortcutsList);
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showEditShortcutDialog(ShortcutItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Shortcut Card");

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_shortcut, null);
        EditText etName = view.findViewById(R.id.dialog_et_name);
        EditText etScript = view.findViewById(R.id.dialog_et_script);
        Spinner spIcon = view.findViewById(R.id.dialog_sp_icon);
        Button pinBtn = view.findViewById(R.id.dialog_btn_pin);
        Button delBtn = view.findViewById(R.id.dialog_btn_delete);

        etName.setText(item.name);
        etScript.setText(item.script);
        setupIconSpinner(spIcon, item.iconName);

        builder.setView(view);
        AlertDialog dialog = builder.create();

        dialog.setButton(DialogInterface.BUTTON_POSITIVE, "Save", (d, which) -> {
            item.name = etName.getText().toString();
            item.script = etScript.getText().toString();
            item.iconName = spIcon.getSelectedItem().toString().toLowerCase();

            int idx = shortcutsList.indexOf(item);
            if (idx != -1) {
                shortcutAdapter.notifyItemChanged(idx);
                shortcutHelper.saveShortcuts(shortcutsList);
            }
        });

        dialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", (d, w) -> {});

        pinBtn.setOnClickListener(v -> {
            boolean success = shortcutHelper.pinShortcutToHomeScreen(item);
            Toast.makeText(this, success ? "Shortcut pinned to launcher home screen!" : "Failed or unsupported by launcher.", Toast.LENGTH_SHORT).show();
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

        dialog.show();
    }

    private void setupIconSpinner(Spinner spinner, String selectedIcon) {
        String[] icons = {"Bolt", "Lock", "Terminal", "Key", "Web", "Keyboard"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, icons);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        int selectIdx = 0;
        for (int i = 0; i < icons.length; i++) {
            if (icons[i].toLowerCase().equals(selectedIcon)) {
                selectIdx = i;
                break;
            }
        }
        spinner.setSelection(selectIdx);
    }

    private void showSaveScriptDialog(String script) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Save Script to Library");

        EditText etName = new EditText(this);
        etName.setHint("Enter script name");
        etName.setPadding(32, 32, 32, 32);
        builder.setView(etName);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String name = etName.getText().toString().trim();
            if (!name.isEmpty()) {
                SharedPreferences libPrefs = getSharedPreferences("script_library", MODE_PRIVATE);
                libPrefs.edit().putString(name, script).apply();
                Toast.makeText(this, "Script saved successfully!", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showLibraryDialog(EditText editor) {
        SharedPreferences libPrefs = getSharedPreferences("script_library", MODE_PRIVATE);
        Map<String, ?> all = libPrefs.getAll();
        List<String> names = new ArrayList<>(all.keySet());

        if (names.isEmpty()) {
            Toast.makeText(this, "Library is empty. Save some scripts first!", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Script");

        String[] nameArray = names.toArray(new String[0]);
        builder.setItems(nameArray, (dialog, which) -> {
            String name = nameArray[which];
            String script = libPrefs.getString(name, "");
            editor.setText(script);
        });
        builder.show();
    }

    private void showHelpDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Ducky Script Reference");
        builder.setMessage("COMMANDS:\n\n" +
                "STRING <text>\n- Types the specified text onto host machine.\n\n" +
                "DELAY <ms>\n- Pauses execution for specified milliseconds.\n\n" +
                "ENTER / TAB / SPACE / BACKSPACE\n- Presses those keys.\n\n" +
                "GUI / WINDOWS <key>\n- Presses key combo (e.g. GUI r to run command prompt).\n\n" +
                "CTRL / CONTROL <key>\n- Presses key with Control modifier.\n\n" +
                "ALT <key>\n- Presses key with Alt modifier.\n\n" +
                "REM / //\n- Comments are ignored.");
        builder.setPositiveButton("Dismiss", null);
        builder.show();
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
                        Toast.makeText(this, "Executing: " + (name != null ? name : "Launcher Macro"), Toast.LENGTH_SHORT).show();
                        duckyInterpreter.execute(script, null);
                    }
                }, 1000);
            }
        }
    }

    @Override
    public void onRegistrationStatusChanged(boolean isRegistered) {
        runOnUiThread(() -> {
            if (isRegistered) {
                tvStatusOn.setText("ON");
                tvStatusOn.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                viewStatusDot.setBackgroundColor(ContextCompat.getColor(this, R.color.status_green));
            } else {
                tvStatusOn.setText("OFF");
                tvStatusOn.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                viewStatusDot.setBackgroundColor(ContextCompat.getColor(this, R.color.status_red));
            }
        });
    }

    @Override
    public void onConnectionStatusChanged(BluetoothDevice device, int state) {
        runOnUiThread(() -> {
            if (state == BluetoothProfile.STATE_CONNECTED) {
                viewStatusDot.setBackgroundColor(ContextCompat.getColor(this, R.color.status_green));
                tvStatusOn.setText("CONNECTED");
            } else if (state == BluetoothProfile.STATE_CONNECTING) {
                viewStatusDot.setBackgroundColor(ContextCompat.getColor(this, R.color.status_yellow));
                tvStatusOn.setText("CONNECTING");
            } else {
                viewStatusDot.setBackgroundColor(ContextCompat.getColor(this, R.color.status_red));
                tvStatusOn.setText("ON");
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