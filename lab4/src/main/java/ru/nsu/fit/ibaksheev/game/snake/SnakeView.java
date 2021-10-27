package ru.nsu.fit.ibaksheev.game.snake;

import io.reactivex.rxjava3.core.Observable;
import me.ippolitov.fit.snakes.SnakesProto;

public interface SnakeView {
    interface Control {
        int getPlayerId();
        SnakesProto.Direction getDirection();
    }

    void setState(SnakesProto.GameState state);

    Observable<Control> getControlObservable();
}
