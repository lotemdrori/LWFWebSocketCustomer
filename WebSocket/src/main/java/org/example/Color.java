package org.example;

public enum Color {
        green(0),
        amber(1),
        red(2),
        clear(3);

    private final int value;

    Color(int val) {
        this.value=val;
    }

    public int getVal(){
        return value;
    }
}
