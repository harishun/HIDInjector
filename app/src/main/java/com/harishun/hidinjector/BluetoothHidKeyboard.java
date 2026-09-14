package com.harishun.hidinjector;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BluetoothHidKeyboard {
    private static final String TAG = "HID_Keyboard";

    public interface OnHidStatusListener {
        void onRegistrationStatusChanged(boolean isRegistered);
        void onConnectionStatusChanged(BluetoothDevice device, int state);
    }

    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;
    private BluetoothHidDevice hidDeviceService;
    private BluetoothDevice connectedHostDevice;
    private BluetoothDevice pendingDevice = null;
    private int currentConnectionState = BluetoothProfile.STATE_DISCONNECTED;
    final private OnHidStatusListener statusListener;
    private boolean isAppRegistered = false;

    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());


    // Standard Combo Keyboard & Mouse Descriptor Map
    private static final byte[] HID_REPORT_DESCRIPTOR = new byte[] {
            // Keyboard
            (byte) 0x05, 0x01,       // USAGE_PAGE (Generic Desktop)
            (byte) 0x09, 0x06,       // USAGE (Keyboard)
            (byte) 0xa1, 0x01,       // COLLECTION (Application)
            (byte) 0x85, 0x01,       //   REPORT_ID (1)
            (byte) 0x05, 0x07,       //   USAGE_PAGE (Keyboard)
            (byte) 0x19, (byte) 0xe0,//   USAGE_MINIMUM (Keyboard LeftControl)
            (byte) 0x29, (byte) 0xe7,//   USAGE_MAXIMUM (Keyboard Right GUI)
            (byte) 0x15, 0x00,       //   LOGICAL_MINIMUM (0)
            (byte) 0x25, 0x01,       //   LOGICAL_MAXIMUM (1)
            (byte) 0x75, 0x01,       //   REPORT_SIZE (1)
            (byte) 0x95, 0x08,       //   REPORT_COUNT (8)
            (byte) 0x81, 0x02,       //   INPUT (Data,Var,Abs) - Modifier Byte
            (byte) 0x95, 0x01,       //   REPORT_COUNT (1)
            (byte) 0x75, 0x08,       //   REPORT_SIZE (8)
            (byte) 0x81, 0x03,       //   INPUT (Const,Var,Abs) - Reserved Byte
            (byte) 0x95, 0x06,       //   REPORT_COUNT (6)
            (byte) 0x75, 0x08,       //   REPORT_SIZE (8)
            (byte) 0x15, 0x00,       //   LOGICAL_MINIMUM (0)
            (byte) 0x25, 0x65,       //   LOGICAL_MAXIMUM (101)
            (byte) 0x05, 0x07,       //   USAGE_PAGE (Keyboard)
            (byte) 0x19, 0x00,       //   USAGE_MINIMUM (Reserved)
            (byte) 0x29, 0x65,       //   USAGE_MAXIMUM (Keyboard Application)
            (byte) 0x81, 0x00,       //   INPUT (Data,Ary,Abs) - 6 Key Array bytes
            (byte) 0xc0,             // END_COLLECTION

            // Mouse
            (byte) 0x05, 0x01,       // USAGE_PAGE (Generic Desktop)
            (byte) 0x09, 0x02,       // USAGE (Mouse)
            (byte) 0xa1, 0x01,       // COLLECTION (Application)
            (byte) 0x85, 0x02,       //   REPORT_ID (2)
            (byte) 0x09, 0x01,       //   USAGE (Pointer)
            (byte) 0xa1, 0x00,       //   COLLECTION (Physical)
            (byte) 0x05, 0x09,       //     USAGE_PAGE (Button)
            (byte) 0x19, 0x01,       //     USAGE_MINIMUM (Button 1)
            (byte) 0x29, 0x03,       //     USAGE_MAXIMUM (Button 3)
            (byte) 0x15, 0x00,       //     LOGICAL_MINIMUM (0)
            (byte) 0x25, 0x01,       //     LOGICAL_MAXIMUM (1)
            (byte) 0x95, 0x03,       //     REPORT_COUNT (3)
            (byte) 0x75, 0x01,       //     REPORT_SIZE (1)
            (byte) 0x81, 0x02,       //     INPUT (Data,Var,Abs)
            (byte) 0x95, 0x01,       //     REPORT_COUNT (1)
            (byte) 0x75, 0x05,       //     REPORT_SIZE (5)
            (byte) 0x81, 0x03,       //     INPUT (Const,Var,Abs)
            (byte) 0x05, 0x01,       //     USAGE_PAGE (Generic Desktop)
            (byte) 0x09, 0x30,       //     USAGE (X)
            (byte) 0x09, 0x31,       //     USAGE (Y)
            (byte) 0x09, 0x38,       //     USAGE (Wheel)
            (byte) 0x15, (byte) 0x81,//     LOGICAL_MINIMUM (-127)
            (byte) 0x25, 0x7f,       //     LOGICAL_MAXIMUM (127)
            (byte) 0x75, 0x08,       //     REPORT_SIZE (8)
            (byte) 0x95, 0x03,       //     REPORT_COUNT (3)
            (byte) 0x81, 0x06,       //     INPUT (Data,Var,Rel)
            (byte) 0xc0,             //   END_COLLECTION
            (byte) 0xc0              // END_COLLECTION
    };

    public BluetoothHidKeyboard(Context context, OnHidStatusListener listener) {
        this.context = context.getApplicationContext();
        this.statusListener = listener;
        BluetoothManager bluetoothManager = (BluetoothManager) this.context.getSystemService(Context.BLUETOOTH_SERVICE);
        this.bluetoothAdapter = bluetoothManager != null ? bluetoothManager.getAdapter() : null;
    }

    @SuppressLint("MissingPermission")
    public void setupService() {
        if (bluetoothAdapter == null) return;
        if (hidDeviceService != null && isAppRegistered) return;

        if (hidDeviceService != null) {
            registerHidApp();
            return;
        }

        bluetoothAdapter.getProfileProxy(context, new BluetoothProfile.ServiceListener() {
            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    hidDeviceService = (BluetoothHidDevice) proxy;
                    registerHidApp();
                }
            }

            @Override
            public void onServiceDisconnected(int profile) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    timeoutHandler.removeCallbacksAndMessages(null);
                    hidDeviceService = null;
                    isAppRegistered = false;
                    connectedHostDevice = null;
                    pendingDevice = null;
                    currentConnectionState = BluetoothProfile.STATE_DISCONNECTED;
                    if (statusListener != null) statusListener.onRegistrationStatusChanged(false);
                }
            }
        }, BluetoothProfile.HID_DEVICE);
    }


    @SuppressLint("MissingPermission")
    private void registerHidApp() {
        BluetoothHidDeviceAppSdpSettings sdpSettings = new BluetoothHidDeviceAppSdpSettings(
                "HID Injector",
                "Virtual HID Input Device",
                "HID Injector",
                (byte) 0xC0, // Combo Keyboard + Mouse subclass
                HID_REPORT_DESCRIPTOR
        );

        BluetoothHidDevice.Callback callback = new BluetoothHidDevice.Callback() {
            @Override
            public void onAppStatusChanged(BluetoothDevice pluggedDevice, boolean registered) {
                super.onAppStatusChanged(pluggedDevice, registered);
                Log.d(TAG, "onAppStatusChanged: registered=" + registered + ", pluggedDevice=" + (pluggedDevice != null ? pluggedDevice.getAddress() : "null"));
                isAppRegistered = registered;
                if (registered && pluggedDevice != null) {
                    connectedHostDevice = pluggedDevice;
                    currentConnectionState = BluetoothProfile.STATE_CONNECTED;
                }
                if (statusListener != null) {
                    statusListener.onRegistrationStatusChanged(registered);
                    if (pluggedDevice != null) {
                        statusListener.onConnectionStatusChanged(pluggedDevice, BluetoothProfile.STATE_CONNECTED);
                    }
                }
            }

            @Override
            public void onConnectionStateChanged(BluetoothDevice device, int state) {
                super.onConnectionStateChanged(device, state);
                Log.d(TAG, "onConnectionStateChanged: device=" + (device != null ? device.getAddress() : "null") + " state=" + state);

                if (state == BluetoothProfile.STATE_CONNECTED) {
                    timeoutHandler.removeCallbacksAndMessages(null);
                    connectedHostDevice = device;
                    pendingDevice = null;
                    currentConnectionState = BluetoothProfile.STATE_CONNECTED;
                    if (statusListener != null) {
                        statusListener.onConnectionStatusChanged(device, BluetoothProfile.STATE_CONNECTED);
                    }
                } else if (state == BluetoothProfile.STATE_CONNECTING) {
                    if (device != null && (device.equals(pendingDevice) || pendingDevice == null)) {
                        currentConnectionState = BluetoothProfile.STATE_CONNECTING;
                        if (statusListener != null) {
                            statusListener.onConnectionStatusChanged(device, BluetoothProfile.STATE_CONNECTING);
                        }
                    }
                } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                    if (device != null && device.equals(connectedHostDevice)) {
                        connectedHostDevice = null;
                    }

                    if (device != null && device.equals(pendingDevice)) {
                        timeoutHandler.removeCallbacksAndMessages(null);
                        pendingDevice = null;
                        currentConnectionState = BluetoothProfile.STATE_DISCONNECTED;
                        if (!isAppRegistered && hidDeviceService != null) {
                            registerHidApp();
                        }
                        if (statusListener != null) {
                            statusListener.onConnectionStatusChanged(device, BluetoothProfile.STATE_DISCONNECTED);
                        }
                    } else if (pendingDevice != null) {
                        // The aborted previous device finished disconnecting; immediately connect to the new pendingDevice!
                        Log.i(TAG, "Aborted device " + (device != null ? device.getAddress() : "null") + " disconnected. Fast-dispatching connect to pending: " + pendingDevice.getAddress());
                        try {
                            hidDeviceService.connect(pendingDevice);
                        } catch (Exception e) {
                            Log.e(TAG, "Error connecting to pending device after disconnect", e);
                        }
                    } else if (connectedHostDevice == null) {
                        currentConnectionState = BluetoothProfile.STATE_DISCONNECTED;
                        if (!isAppRegistered && hidDeviceService != null) {
                            registerHidApp();
                        }
                        if (statusListener != null) {
                            statusListener.onConnectionStatusChanged(device, BluetoothProfile.STATE_DISCONNECTED);
                        }
                    } else {
                        Log.i(TAG, "Ignored stale disconnect event from previously aborted device: " + (device != null ? device.getAddress() : "null"));
                    }
                }
            }

            @Override
            public void onGetReport(BluetoothDevice device, byte type, byte id, int bufferSize) {
                super.onGetReport(device, type, id, bufferSize);
                Log.d(TAG, "onGetReport: device=" + device.getAddress() + " type=" + type + " id=" + id + " bufferSize=" + bufferSize);
                if (type == BluetoothHidDevice.REPORT_TYPE_INPUT) {
                    if (id == 1) {
                        hidDeviceService.replyReport(device, type, id, new byte[8]);
                    } else if (id == 2) {
                        hidDeviceService.replyReport(device, type, id, new byte[4]);
                    } else {
                        hidDeviceService.replyReport(device, type, id, new byte[8]);
                    }
                } else {
                    hidDeviceService.replyReport(device, type, id, new byte[0]);
                }
            }

            @Override
            public void onSetReport(BluetoothDevice device, byte type, byte id, byte[] data) {
                super.onSetReport(device, type, id, data);
                Log.d(TAG, "onSetReport: device=" + device.getAddress() + " type=" + type + " id=" + id);
            }

            @Override
            public void onSetProtocol(BluetoothDevice device, byte protocol) {
                super.onSetProtocol(device, protocol);
                Log.d(TAG, "onSetProtocol: device=" + device.getAddress() + " protocol=" + protocol);
            }

            @Override
            public void onInterruptData(BluetoothDevice device, byte reportId, byte[] data) {
                super.onInterruptData(device, reportId, data);
                Log.d(TAG, "onInterruptData: device=" + device.getAddress() + " reportId=" + reportId);
            }
        };

        // Pass null for QoS settings to let Android choose default settings
        hidDeviceService.registerApp(sdpSettings, null, null, context.getMainExecutor(), callback);
    }

    @SuppressLint("MissingPermission")
    public List<BluetoothDevice> getPairedDevices() {
        List<BluetoothDevice> devicesList = new ArrayList<>();
        if (bluetoothAdapter != null) {
            Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();
            if (bondedDevices != null) {
                devicesList.addAll(bondedDevices);
            }
        }
        return devicesList;
    }

    @SuppressLint("MissingPermission")
    public void connectToDevice(BluetoothDevice targetDevice) {
        if (targetDevice == null || hidDeviceService == null) {
            return;
        }

        if (!isAppRegistered) {
            registerHidApp();
        }

        // 1. Immediately abort any prior timeouts from previous connection attempts
        timeoutHandler.removeCallbacksAndMessages(null);

        // 2. Check if already connected to this exact target device
        try {
            int targetState = hidDeviceService.getConnectionState(targetDevice);
            if (targetState == BluetoothProfile.STATE_CONNECTED) {
                connectedHostDevice = targetDevice;
                pendingDevice = null;
                currentConnectionState = BluetoothProfile.STATE_CONNECTED;
                if (statusListener != null) {
                    statusListener.onConnectionStatusChanged(targetDevice, BluetoothProfile.STATE_CONNECTED);
                }
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking target state", e);
        }

        // 3. If in the middle of connecting to another device or already connected to another device:
        // Seamlessly abort previous attempt and switch to this new device!
        boolean needDelay = false;
        BluetoothDevice previousDevice = connectedHostDevice != null ? connectedHostDevice : pendingDevice;
        if (previousDevice != null && !previousDevice.equals(targetDevice)) {
            Log.i(TAG, "Switching target mid-flight from " + previousDevice.getAddress() + " to " + targetDevice.getAddress());
            try {
                hidDeviceService.disconnect(previousDevice);
                needDelay = true;
            } catch (Exception e) {
                Log.e(TAG, "Error disconnecting previous device", e);
            }
        }

        // 4. Update state to CONNECTING for the newly selected target device
        final BluetoothDevice currentAttemptDevice = targetDevice;
        pendingDevice = currentAttemptDevice;
        connectedHostDevice = null;
        currentConnectionState = BluetoothProfile.STATE_CONNECTING;
        if (statusListener != null) {
            statusListener.onConnectionStatusChanged(currentAttemptDevice, BluetoothProfile.STATE_CONNECTING);
        }

        // 5. Arm timeout watchdog strictly scoped to this target device attempt
        timeoutHandler.postDelayed(() -> {
            if (currentConnectionState == BluetoothProfile.STATE_CONNECTING && currentAttemptDevice.equals(pendingDevice)) {
                Log.w(TAG, "Connection attempt to " + currentAttemptDevice.getAddress() + " timed out.");
                if (hidDeviceService != null) {
                    try {
                        hidDeviceService.disconnect(currentAttemptDevice);
                    } catch (Exception ignored) {}
                }
                currentConnectionState = BluetoothProfile.STATE_DISCONNECTED;
                connectedHostDevice = null;
                pendingDevice = null;
                if (!isAppRegistered && hidDeviceService != null) {
                    registerHidApp();
                }
                if (statusListener != null) {
                    statusListener.onConnectionStatusChanged(null, BluetoothProfile.STATE_DISCONNECTED);
                }
            }
        }, 7000);

        // 6. Dispatch connect to the new device
        Runnable dispatchConnect = () -> {
            if (hidDeviceService != null && currentAttemptDevice.equals(pendingDevice)) {
                try {
                    Log.i(TAG, "Dispatching connect to: " + currentAttemptDevice.getAddress());
                    hidDeviceService.connect(currentAttemptDevice);
                } catch (Exception e) {
                    Log.e(TAG, "Error calling hidDeviceService.connect", e);
                    timeoutHandler.removeCallbacksAndMessages(null);
                    currentConnectionState = BluetoothProfile.STATE_DISCONNECTED;
                    pendingDevice = null;
                    connectedHostDevice = null;
                    if (!isAppRegistered && hidDeviceService != null) {
                        registerHidApp();
                    }
                    if (statusListener != null) {
                        statusListener.onConnectionStatusChanged(currentAttemptDevice, BluetoothProfile.STATE_DISCONNECTED);
                    }
                }
            }
        };

        if (needDelay) {
            new Handler(Looper.getMainLooper()).postDelayed(dispatchConnect, 150);
        } else {
            dispatchConnect.run();
        }
    }

    @SuppressLint("MissingPermission")
    public BluetoothDevice getConnectedDevice() {
        if (hidDeviceService != null) {
            try {
                List<BluetoothDevice> connectedList = hidDeviceService.getConnectedDevices();
                if (connectedList != null && !connectedList.isEmpty()) {
                    connectedHostDevice = connectedList.get(0);
                    currentConnectionState = BluetoothProfile.STATE_CONNECTED;
                    return connectedHostDevice;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error querying getConnectedDevices", e);
            }

            if (connectedHostDevice != null) {
                try {
                    int state = hidDeviceService.getConnectionState(connectedHostDevice);
                    if (state == BluetoothProfile.STATE_CONNECTED) {
                        currentConnectionState = BluetoothProfile.STATE_CONNECTED;
                        return connectedHostDevice;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error checking getConnectionState", e);
                }
            }
        }
        return connectedHostDevice;
    }

    public void sendText(String text) {
        BluetoothDevice target = getConnectedDevice();
        if (hidDeviceService == null || target == null || text == null) {
            Log.w(TAG, "sendText aborted: service=" + (hidDeviceService != null) + " target=" + (target != null ? target.getAddress() : "null"));
            return;
        }

        List<byte[]> pipeline = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            byte[] hidCode = getHidCodeForChar(c);
            if (hidCode != null) {
                pipeline.add(hidCode);
            }
        }

        Handler handler = new Handler(Looper.getMainLooper());
        long delayOffset = 0;

        for (final byte[] keystroke : pipeline) {
            handler.postDelayed(() -> transmitReport(keystroke[0], keystroke[1]), delayOffset);
            delayOffset += 25;
            handler.postDelayed(() -> transmitReport((byte) 0x00, (byte) 0x00), delayOffset);
            delayOffset += 25;
        }
    }

    private byte[] getHidCodeForChar(char c) {
        // [Modifier, Keycode]
        if (c >= 'a' && c <= 'z') {
            return new byte[]{0x00, (byte) (0x04 + (c - 'a'))};
        } else if (c >= 'A' && c <= 'Z') {
            return new byte[]{0x02, (byte) (0x04 + (c - 'A'))};
        } else if (c >= '1' && c <= '9') {
            return new byte[]{0x00, (byte) (0x1E + (c - '1'))};
        } else if (c == '0') {
            return new byte[]{0x00, 0x27};
        }

        switch (c) {
            case ' ': return new byte[]{0x00, 0x2C};
            case '\n': return new byte[]{0x00, 0x28};
            case '\b': return new byte[]{0x00, 0x2A};
            case '\t': return new byte[]{0x00, 0x2B};
            case '!': return new byte[]{0x02, 0x1E};
            case '@': return new byte[]{0x02, 0x1F};
            case '#': return new byte[]{0x02, 0x20};
            case '$': return new byte[]{0x02, 0x21};
            case '%': return new byte[]{0x02, 0x22};
            case '^': return new byte[]{0x02, 0x23};
            case '&': return new byte[]{0x02, 0x24};
            case '*': return new byte[]{0x02, 0x25};
            case '(': return new byte[]{0x02, 0x26};
            case ')': return new byte[]{0x02, 0x27};
            case '-': return new byte[]{0x00, 0x2D};
            case '_': return new byte[]{0x02, 0x2D};
            case '=': return new byte[]{0x00, 0x2E};
            case '+': return new byte[]{0x02, 0x2E};
            case '[': return new byte[]{0x00, 0x2F};
            case '{': return new byte[]{0x02, 0x2F};
            case ']': return new byte[]{0x00, 0x30};
            case '}': return new byte[]{0x02, 0x30};
            case '\\': return new byte[]{0x00, 0x31};
            case '|': return new byte[]{0x02, 0x31};
            case ';': return new byte[]{0x00, 0x33};
            case ':': return new byte[]{0x02, 0x33};
            case '\'': return new byte[]{0x00, 0x34};
            case '"': return new byte[]{0x02, 0x34};
            case '`': return new byte[]{0x00, 0x35};
            case '~': return new byte[]{0x02, 0x35};
            case ',': return new byte[]{0x00, 0x36};
            case '<': return new byte[]{0x02, 0x36};
            case '.': return new byte[]{0x00, 0x37};
            case '>': return new byte[]{0x02, 0x37};
            case '/': return new byte[]{0x00, 0x38};
            case '?': return new byte[]{0x02, 0x38};
            default: return null;
        }
    }

    public void sendHelloWorld() {
        sendText("Hello World!");
    }

    @SuppressLint("MissingPermission")
    public boolean transmitReport(byte modifier, byte keycode) {
        BluetoothDevice target = getConnectedDevice();
        if (hidDeviceService == null || target == null) {
            Log.w(TAG, "transmitReport aborted: service=" + (hidDeviceService != null) + " target=" + (target != null ? target.getAddress() : "null"));
            return false;
        }

        byte[] report = new byte[8];
        report[0] = modifier;
        report[1] = 0x00;
        report[2] = keycode;

        boolean success = hidDeviceService.sendReport(target, 1, report);
        Log.d(TAG, "sendReport (Keyboard) [mod=" + modifier + ", key=" + keycode + "] -> " + success);
        return success;
    }

    @SuppressLint("MissingPermission")
    public void disconnect() {
        timeoutHandler.removeCallbacksAndMessages(null);
        if (hidDeviceService != null) {
            BluetoothDevice devToDisconnect = connectedHostDevice != null ? connectedHostDevice : pendingDevice;
            if (devToDisconnect != null) {
                try {
                    hidDeviceService.disconnect(devToDisconnect);
                } catch (Exception e) {
                    Log.e(TAG, "Error disconnecting", e);
                }
            }
        }
        connectedHostDevice = null;
        pendingDevice = null;
        currentConnectionState = BluetoothProfile.STATE_DISCONNECTED;
        if (!isAppRegistered && hidDeviceService != null) {
            registerHidApp();
        }
        if (statusListener != null) {
            statusListener.onConnectionStatusChanged(null, BluetoothProfile.STATE_DISCONNECTED);
        }
    }

    @SuppressLint("MissingPermission")
    public void cleanup() {
        timeoutHandler.removeCallbacksAndMessages(null);
        if (hidDeviceService != null) {
            if (isAppRegistered) {
                hidDeviceService.unregisterApp();
                isAppRegistered = false;
            }
            bluetoothAdapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDeviceService);
            hidDeviceService = null;
        }
        connectedHostDevice = null;
        pendingDevice = null;
        currentConnectionState = BluetoothProfile.STATE_DISCONNECTED;
    }

    @SuppressLint("MissingPermission")
    public boolean transmitMouseReport(byte buttons, byte x, byte y, byte wheel) {
        BluetoothDevice target = getConnectedDevice();
        if (hidDeviceService == null || target == null) {
            Log.w(TAG, "transmitMouseReport aborted: service=" + (hidDeviceService != null) + " target=" + (target != null ? target.getAddress() : "null"));
            return false;
        }

        byte[] report = new byte[4];
        report[0] = buttons;
        report[1] = x;
        report[2] = y;
        report[3] = wheel;

        boolean success = hidDeviceService.sendReport(target, 2, report);
        return success;
    }

    public boolean transmitMouseReport(byte buttons, byte x, byte y) {
        return transmitMouseReport(buttons, x, y, (byte) 0);
    }

    @SuppressLint("MissingPermission")
    public void sendKeyWithModifier(byte modifier, char character) {
        byte[] code = getHidCodeForChar(character);
        if (code != null) {
            byte effectiveMod = (byte) (modifier | code[0]);
            transmitReport(effectiveMod, code[1]);
            new Handler(Looper.getMainLooper()).postDelayed(() -> transmitReport((byte) 0x00, (byte) 0x00), 30);
        }
    }

    @SuppressLint("MissingPermission")
    public void sendModifierOnly(byte modifier) {
        transmitReport(modifier, (byte) 0x00);
        new Handler(Looper.getMainLooper()).postDelayed(() -> transmitReport((byte) 0x00, (byte) 0x00), 30);
    }

    public boolean isConnected() {
        return getConnectedDevice() != null;
    }

    public boolean isAppRegistered() {
        return isAppRegistered;
    }

    public boolean isServiceReady() {
        return hidDeviceService != null && isAppRegistered;
    }
}