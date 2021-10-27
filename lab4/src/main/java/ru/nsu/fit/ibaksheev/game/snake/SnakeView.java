package ru.nsu.fit.ibaksheev.game.snake;

import io.reactivex.rxjava3.core.Observable;
import me.ippolitov.fit.snakes.SnakesProto;

public interface SnakeView {
    void setState(SnakesProto.GameState state);
    Observable<String> onControl();
}
