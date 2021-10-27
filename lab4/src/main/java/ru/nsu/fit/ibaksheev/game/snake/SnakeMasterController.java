package ru.nsu.fit.ibaksheev.game.snake;

import io.reactivex.rxjava3.core.Observable;
import me.ippolitov.fit.snakes.SnakesProto;

public class SnakeMasterController {
    public SnakeMasterController(Observable<SnakeView.Control> controlObservable) {
    }

    public SnakesProto.GameState getNextState(SnakesProto.GameState oldState) {
        var newStateBuilder = SnakesProto.GameState.newBuilder(oldState);

        newStateBuilder.clearSnakes();
        for (var player : oldState.getPlayers().getPlayersList()) {
            newStateBuilder.addSnakes(
                    SnakesProto.GameState.Snake.newBuilder()
                            .addPoints(
                                    SnakesProto.GameState.Coord.newBuilder()
                                            .setX(player.getId())
                                            .setY(player.getId())
                                            .build()
                            )
                            .setState(SnakesProto.GameState.Snake.SnakeState.ALIVE)
                            .setHeadDirection(SnakesProto.Direction.UP)
                            .setPlayerId(0)
                            .build()
            );
        }

        return newStateBuilder.build();
    }
}
