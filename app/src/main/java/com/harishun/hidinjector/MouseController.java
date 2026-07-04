package com.harishun.hidinjector;

import android.view.MotionEvent;
import android.view.View;

public class MouseController {
    private final BluetoothHidKeyboard hidKeyboard;
    private final SettingsManager settingsManager;
    private final Runnable onDisconnected;
    private float previousX = 0f;
    private float previousY = 0f;
    private float accumulatorX = 0f;
    private float accumulatorY = 0f;

    public MouseController(BluetoothHidKeyboard keyboard, SettingsManager settingsManager, Runnable onDisconnected) {
        this.hidKeyboard = keyboard;
        this.settingsManager = settingsManager;
        this.onDisconnected = onDisconnected;
    }

    public void setupTrackpad(View trackpadView) {
        trackpadView.setOnTouchListener((v, event) -> {
            if (!hidKeyboard.isConnected()) {
                if (onDisconnected != null) onDisconnected.run();
                return true;
            }

            float x = event.getX();
            float y = event.getY();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    previousX = x;
                    previousY = y;
                    accumulatorX = 0f;
                    accumulatorY = 0f;
                    break;

                case MotionEvent.ACTION_MOVE:
                    float dx = x - previousX;
                    float dy = y - previousY;

                    float sensitivity = settingsManager.getSensitivity();
                    accumulatorX += dx * sensitivity;
                    accumulatorY += dy * sensitivity;

                    int moveX = (int) accumulatorX;
                    int moveY = (int) accumulatorY;

                    if (moveX != 0 || moveY != 0) {
                        byte byteX = (byte) Math.max(-127, Math.min(127, moveX));
                        byte byteY = (byte) Math.max(-127, Math.min(127, moveY));
                        hidKeyboard.transmitMouseReport((byte) 0x00, byteX, byteY);

                        accumulatorX -= moveX;
                        accumulatorY -= moveY;
                    }

                    previousX = x;
                    previousY = y;
                    break;
            }
            return true;
        });
    }

    public void sendMouseButton(boolean isPressed, byte buttonMask) {
        if (!hidKeyboard.isConnected()) {
            if (onDisconnected != null) onDisconnected.run();
            return;
        }

        byte buttons = 0x00;
        if (isPressed) {
            buttons = buttonMask;
        }
        hidKeyboard.transmitMouseReport(buttons, (byte) 0, (byte) 0);
    }
}
