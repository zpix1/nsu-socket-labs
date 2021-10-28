package ru.nsu.fit.ibaksheev.game.snake;

import lombok.AllArgsConstructor;
import lombok.Data;
import me.ippolitov.fit.snakes.SnakesProto;

public interface SnakeView {
    @AllArgsConstructor
    @Data
    class Control {
        private Integer playerId;
        private SnakesProto.Direction direction;
    }

    void setState(SnakesProto.GameState state);
}
