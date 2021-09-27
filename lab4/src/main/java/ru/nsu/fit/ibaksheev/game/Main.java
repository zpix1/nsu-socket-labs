package ru.nsu.fit.ibaksheev.game;

import static me.ippolitov.fit.snakes.SnakesProto.*;

public class Main {
    public static void main(String[] args) {
        var t = GameConfig.newBuilder()
                .setWidth(10)
                .setHeight(10)
                // Все остальные параметры имеют значения по умолчанию
                .build();
        System.out.println(t);
    }
}
