package com.harishun.hidinjector;

import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

public class MouseController {
    private static final int LONG_PRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout();
    private final BluetoothHidKeyboard hidKeyboard;
    private final SettingsManager settingsManager;
    private final Runnable onDisconnected;
    private float previousX = 0f;
    private float previousY = 0f;
    private float accumulatorX = 0f;
    private float accumulatorY = 0f;
    private float scrollAccumulatorY = 0f;
    private float previousMultiY = 0f;
    private boolean isMultiTouchActive = false;

    private float downX = 0f;
    private float downY = 0f;
    private boolean isMoved = false;
    private boolean hasPerformedLongPress = false;
    private int maxPointerCount = 1;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable longPressRunnable;

    public MouseController(BluetoothHidKeyboard keyboard, SettingsManager settingsManager, Runnable onDisconnected) {
        this.hidKeyboard = keyboard;
        this.settingsManager = settingsManager;
        this.onDisconnected = onDisconnected;
    }

    public void setupTrackpad(View trackpadView) {
        final int touchSlop = ViewConfiguration.get(trackpadView.getContext()).getScaledTouchSlop();

        trackpadView.setOnTouchListener((v, event) -> {
            if (!hidKeyboard.isConnected()) {
                if (onDisconnected != null) onDisconnected.run();
                return true;
            }

            int pointerCount = event.getPointerCount();
            if (pointerCount > maxPointerCount) {
                maxPointerCount = pointerCount;
            }

            float x = event.getX();
            float y = event.getY();

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    previousX = x;
                    previousY = y;
                    downX = x;
                    downY = y;
                    accumulatorX = 0f;
                    accumulatorY = 0f;
                    scrollAccumulatorY = 0f;
                    isMoved = false;
                    hasPerformedLongPress = false;
                    maxPointerCount = 1;
                    isMultiTouchActive = false;

                    longPressRunnable = () -> {
                        if (!isMoved && maxPointerCount == 1) {
                            hasPerformedLongPress = true;
                            // 1-Finger Long Press -> Right Click
                            sendMouseButton(true, (byte) 0x02);
                            handler.postDelayed(() -> sendMouseButton(false, (byte) 0x02), 50);
                        }
                    };
                    handler.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT);
                    break;

                case MotionEvent.ACTION_POINTER_DOWN:
                    if (longPressRunnable != null) {
                        handler.removeCallbacks(longPressRunnable);
                    }
                    if (event.getPointerCount() >= 2) {
                        float sumY = 0;
                        int count = event.getPointerCount();
                        for (int i = 0; i < count; i++) {
                            sumY += event.getY(i);
                        }
                        previousMultiY = sumY / count;
                        isMultiTouchActive = true;
                    }
                    break;

                case MotionEvent.ACTION_POINTER_UP:
                    isMultiTouchActive = false;
                    break;

                case MotionEvent.ACTION_MOVE:
                    float dx = x - previousX;
                    float dy = y - previousY;

                    float totalDx = Math.abs(x - downX);
                    float totalDy = Math.abs(y - downY);

                    if (totalDx > touchSlop || totalDy > touchSlop) {
                        if (!isMoved) {
                            isMoved = true;
                            if (longPressRunnable != null) {
                                handler.removeCallbacks(longPressRunnable);
                            }
                        }
                    }

                    if (pointerCount == 1) {
                        isMultiTouchActive = false;
                        // Single finger -> Mouse motion
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
                    } else if (pointerCount >= 2) {
                        // Two or more fingers -> Fast responsive Trackpad Scroll Wheel
                        float sumY = 0;
                        for (int i = 0; i < pointerCount; i++) {
                            sumY += event.getY(i);
                        }
                        float avgY = sumY / pointerCount;

                        if (!isMultiTouchActive) {
                            previousMultiY = avgY;
                            isMultiTouchActive = true;
                        } else {
                            float dyMulti = previousMultiY - avgY; // Positive when scrolling UP
                            scrollAccumulatorY += dyMulti * 1.5f;

                            int scrollSteps = (int) scrollAccumulatorY;
                            if (scrollSteps != 0) {
                                byte wheel = (byte) Math.max(-127, Math.min(127, scrollSteps));
                                hidKeyboard.transmitMouseReport((byte) 0x00, (byte) 0, (byte) 0, wheel);
                                scrollAccumulatorY -= scrollSteps;
                            }
                            previousMultiY = avgY;
                        }
                    }

                    previousX = x;
                    previousY = y;
                    break;

                case MotionEvent.ACTION_UP:
                    if (longPressRunnable != null) {
                        handler.removeCallbacks(longPressRunnable);
                    }

                    if (!isMoved) {
                        if (maxPointerCount == 1 && !hasPerformedLongPress) {
                            // 1-Finger Short Tap -> Left Click
                            sendMouseButton(true, (byte) 0x01);
                            handler.postDelayed(() -> sendMouseButton(false, (byte) 0x01), 50);
                        } else if (maxPointerCount == 2) {
                            // 2-Finger Tap -> Right Click
                            sendMouseButton(true, (byte) 0x02);
                            handler.postDelayed(() -> sendMouseButton(false, (byte) 0x02), 50);
                        } else if (maxPointerCount >= 3) {
                            // 3-Finger Tap -> Middle Click
                            sendMouseButton(true, (byte) 0x04);
                            handler.postDelayed(() -> sendMouseButton(false, (byte) 0x04), 50);
                        }
                    }
                    v.performClick();
                    break;

                case MotionEvent.ACTION_CANCEL:
                    if (longPressRunnable != null) {
                        handler.removeCallbacks(longPressRunnable);
                    }
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
