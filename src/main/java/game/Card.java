package game;

import java.io.Serializable;

public class Card implements Serializable {

    private final int value;
    private final String name;
    private final Color color;

    Card(int value, Color color) {
        this.value = value;
        this.color = color;
        this.name = "" + value + " " + color;
    }

    public String getName() {
        return name;
    }

    public Color getColor() {
        return color;
    }

    public int getValue() {
        return value;
    }
}

