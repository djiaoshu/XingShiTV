package com.xingshi.tv;

import android.view.KeyEvent;

enum InputAction {
    OPEN_MENU,
    CLOSE_MENU,
    CHANNEL_UP,
    CHANNEL_DOWN,
    SOURCE_PREV,
    SOURCE_NEXT,
    CONFIRM,
    BACK,
    OPEN_MANAGEMENT,
    PLAY_PAUSE,
    DIGIT_0,
    DIGIT_1,
    DIGIT_2,
    DIGIT_3,
    DIGIT_4,
    DIGIT_5,
    DIGIT_6,
    DIGIT_7,
    DIGIT_8,
    DIGIT_9;

    static InputAction fromKeyCode(int keyCode) {
        int digit = digitForKeyCode(keyCode);
        if (digit >= 0) {
            return digitAction(digit);
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_CHANNEL_UP:
                return CHANNEL_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
                return CHANNEL_DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return SOURCE_PREV;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return SOURCE_NEXT;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                return CONFIRM;
            case KeyEvent.KEYCODE_BACK:
                return BACK;
            case KeyEvent.KEYCODE_MENU:
                return OPEN_MANAGEMENT;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                return PLAY_PAUSE;
            default:
                return null;
        }
    }

    static boolean isHandledKey(int keyCode) {
        return fromKeyCode(keyCode) != null;
    }

    boolean isDigit() {
        return ordinal() >= DIGIT_0.ordinal() && ordinal() <= DIGIT_9.ordinal();
    }

    int digitValue() {
        return isDigit() ? ordinal() - DIGIT_0.ordinal() : -1;
    }

    boolean allowsRepeatNavigation() {
        return this == CHANNEL_UP || this == CHANNEL_DOWN;
    }

    private static InputAction digitAction(int digit) {
        switch (digit) {
            case 0:
                return DIGIT_0;
            case 1:
                return DIGIT_1;
            case 2:
                return DIGIT_2;
            case 3:
                return DIGIT_3;
            case 4:
                return DIGIT_4;
            case 5:
                return DIGIT_5;
            case 6:
                return DIGIT_6;
            case 7:
                return DIGIT_7;
            case 8:
                return DIGIT_8;
            case 9:
                return DIGIT_9;
            default:
                return null;
        }
    }

    private static int digitForKeyCode(int keyCode) {
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            return keyCode - KeyEvent.KEYCODE_0;
        }
        if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9) {
            return keyCode - KeyEvent.KEYCODE_NUMPAD_0;
        }
        return -1;
    }
}
