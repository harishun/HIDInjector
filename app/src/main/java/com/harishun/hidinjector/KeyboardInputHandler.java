package com.harishun.hidinjector;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

public class KeyboardInputHandler {
    private final BluetoothHidKeyboard hidKeyboard;
    private final Runnable onDisconnected;
    private boolean isEditing = false;

    public KeyboardInputHandler(BluetoothHidKeyboard keyboard, Runnable onDisconnected) {
        this.hidKeyboard = keyboard;
        this.onDisconnected = onDisconnected;
    }

    public void setupKeyboard(EditText editText) {
        editText.setText("");
        editText.setHint(R.string.hint_realtime_keyboard);

        editText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                if (editText.getText().toString().isEmpty()) {
                    isEditing = true;
                    editText.setText(" ");
                    editText.setSelection(1);
                    isEditing = false;
                }
            } else {
                if (" ".equals(editText.getText().toString())) {
                    isEditing = true;
                    editText.setText("");
                    isEditing = false;
                }
            }
        });

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (isEditing) return;
                isEditing = true;

                if (!hidKeyboard.isConnected()) {
                    if (onDisconnected != null) onDisconnected.run();
                    editText.setText(" ");
                    editText.setSelection(1);
                    isEditing = false;
                    return;
                }

                String text = s.toString();
                if (text.length() == 0) {
                    hidKeyboard.sendText("\b");
                    editText.setText(" ");
                    editText.setSelection(1);
                } else if (text.length() == 1) {
                    char c = text.charAt(0);
                    if (c != ' ') {
                        hidKeyboard.sendText(String.valueOf(c));
                        editText.setText(" ");
                        editText.setSelection(1);
                    }
                } else if (text.length() == 2) {
                    char newChar = text.charAt(1);
                    hidKeyboard.sendText(String.valueOf(newChar));
                    editText.setText(" ");
                    editText.setSelection(1);
                } else if (text.length() > 2) {
                    String typed = text.startsWith(" ") ? text.substring(1) : text;
                    hidKeyboard.sendText(typed);
                    editText.setText(" ");
                    editText.setSelection(1);
                }

                isEditing = false;
            }
        });

        editText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || 
                actionId == EditorInfo.IME_ACTION_SEND || 
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                if (!hidKeyboard.isConnected()) {
                    if (onDisconnected != null) onDisconnected.run();
                    return true;
                }
                hidKeyboard.sendText("\n");
                return true;
            }
            return false;
        });
    }
}
