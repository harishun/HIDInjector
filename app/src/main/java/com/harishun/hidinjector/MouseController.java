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
        final int swipeThreshold = touchSlop * 3;

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
                    } else if (pointerCount == 2) {
                        // Two fingers -> Trackpad Scroll Wheel
                        scrollAccumulatorY += dy * 0.5f;
                        int scrollY = (int) scrollAccumulatorY;
                        if (scrollY != 0) {
                            // Negative dy = swipe up = scroll up (+1), positive dy = swipe down = scroll down (-1)
                            byte wheel = (byte) Math.max(-127, Math.min(127, -scrollY));
                            hidKeyboard.transmitMouseReport((byte) 0x00, (byte) 0, (byte) 0, wheel);
                            scrollAccumulatorY -= scrollY;
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
                    } else if (maxPointerCount >= 3) {
                        // 3-Finger Swipe Gestures
                        float deltaX = x - downX;
                        float deltaY = y - downY;

                        if (Math.abs(deltaX) > Math.abs(deltaY)) {
                            if (deltaX > swipeThreshold) {
                                // 3-Finger Swipe Right -> Alt + Tab
                                hidKeyboard.sendKeyWithModifier((byte) 0x04, 't');
                            } else if (deltaX < -swipeThreshold) {
                                // 3-Finger Swipe Left -> Alt + Shift + Tab
                                hidKeyboard.transmitReport((byte) 0x06, (byte) 0x2B);
                                handler.postDelayed(() -> hidKeyboard.transmitReport((byte) 0x00, (byte) 0x00), 50);
                            }
                        } else {
                            if (deltaY < -swipeThreshold) {
                                // 3-Finger Swipe Up -> Win + Tab (Task View)
                                hidKeyboard.transmitReport((byte) 0x08, (byte) 0x2B);
                                handler.postDelayed(() -> hidKeyboard.transmitReport((byte) 0x00, (byte) 0x00), 50);
                            } else if (deltaY > swipeThreshold) {
                                // 3-Finger Swipe Down -> Win + D (Show Desktop)
                                hidKeyboard.sendKeyWithModifier((byte) 0x08, 'd');
                            }
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
