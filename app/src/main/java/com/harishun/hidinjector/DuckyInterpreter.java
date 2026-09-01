package com.harishun.hidinjector;

import android.os.Handler;
import android.os.Looper;

public class DuckyInterpreter {
    public interface InterpreterListener {
        void onScriptFinished();
        void onScriptError(String error);
        void onCommandExecuted(String command);
    }

    private final BluetoothHidKeyboard hidKeyboard;

    public DuckyInterpreter(BluetoothHidKeyboard keyboard) {
        this.hidKeyboard = keyboard;
    }

    public void execute(String script, InterpreterListener listener) {
        if (script == null || script.trim().isEmpty()) {
            if (listener != null) listener.onScriptFinished();
            return;
        }

        String[] lines = script.split("\n");
        long delayOffset = 0;
        Handler handler = new Handler(Looper.getMainLooper());

        for (int i = 0; i < lines.length; i++) {
            final String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("//") || line.startsWith("REM")) {
                continue;
            }

            String[] parts = line.split(" ", 2);
            final String command = parts[0].toUpperCase();
            final String arg = parts.length > 1 ? parts[1] : "";

            if (listener != null) {
                handler.postDelayed(() -> listener.onCommandExecuted(line), delayOffset);
            }

            switch (command) {
                case "STRING":
                    handler.postDelayed(() -> hidKeyboard.sendText(arg), delayOffset);
                    delayOffset += arg.length() * 40;
                    break;

                case "DELAY":
                    try {
                        long ms = Long.parseLong(arg);
                        delayOffset += ms;
                    } catch (NumberFormatException e) {
                        delayOffset += 500;
                    }
                    break;

                case "ENTER":
                    handler.postDelayed(() -> hidKeyboard.sendText("\n"), delayOffset);
                    delayOffset += 40;
                    break;

                case "SPACE":
                    handler.postDelayed(() -> hidKeyboard.sendText(" "), delayOffset);
                    delayOffset += 40;
                    break;

                case "TAB":
                    handler.postDelayed(() -> hidKeyboard.sendText("\t"), delayOffset);
                    delayOffset += 40;
                    break;

                case "BACKSPACE":
                    handler.postDelayed(() -> hidKeyboard.sendText("\b"), delayOffset);
                    delayOffset += 40;
                    break;

                case "GUI":
                case "WINDOWS":
                    {
                        final String cleanArg = arg.trim();
                        if (!cleanArg.isEmpty()) {
                            final char key = cleanArg.toLowerCase().charAt(0);
                            handler.postDelayed(() -> hidKeyboard.sendKeyWithModifier((byte) 0x08, key), delayOffset);
                        } else {
                            handler.postDelayed(() -> hidKeyboard.sendModifierOnly((byte) 0x08), delayOffset);
                        }
                    }
                    delayOffset += 40;
                    break;

                case "CTRL":
                case "CONTROL":
                    {
                        final String cleanArg = arg.trim();
                        if (!cleanArg.isEmpty()) {
                            final char key = cleanArg.toLowerCase().charAt(0);
                            handler.postDelayed(() -> hidKeyboard.sendKeyWithModifier((byte) 0x01, key), delayOffset);
                        } else {
                            handler.postDelayed(() -> hidKeyboard.sendModifierOnly((byte) 0x01), delayOffset);
                        }
                    }
                    delayOffset += 40;
                    break;

                case "ALT":
                    {
                        final String cleanArg = arg.trim();
                        if (!cleanArg.isEmpty()) {
                            final char key = cleanArg.toLowerCase().charAt(0);
                            handler.postDelayed(() -> hidKeyboard.sendKeyWithModifier((byte) 0x04, key), delayOffset);
                        } else {
                            handler.postDelayed(() -> hidKeyboard.sendModifierOnly((byte) 0x04), delayOffset);
                        }
                    }
                    delayOffset += 40;
                    break;
            }
        }

        if (listener != null) {
            handler.postDelayed(listener::onScriptFinished, delayOffset);
        }
    }
}
