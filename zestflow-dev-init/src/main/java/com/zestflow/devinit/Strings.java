package com.zestflow.devinit;

final class Strings {

    private Strings() {
    }

    static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    static boolean isNotBlank(String value) {
        return !isBlank(value);
    }
}
