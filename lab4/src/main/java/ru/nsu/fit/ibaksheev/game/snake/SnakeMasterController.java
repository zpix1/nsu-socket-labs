package ru.nsu.fit.ibaksheev.game.snake;

import io.reactivex.rxjava3.core.Observable;
import me.ippolitov.fit.snakes.SnakesProto;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class SnakeMasterController {
    private Map<Integer, SnakesProto.Direction> steerChoices;

    public SnakeMasterController(Observable<SnakeView.Control> controlObservable) {
        steerChoices = new HashMap<>();
        controlObservable.subscribe(control -> {
            System.out.println(control);
            steerChoices.put(control.getPlayerId(), control.getDirection());
        });
    }

    private static SnakesProto.GameState.Coord movePoint(SnakesProto.GameState.Coord oldPoint, SnakesProto.Direction direction) {
        return switch (direction) {
            case UP -> SnakesProto.GameState.Coord.newBuilder().setX(oldPoint.getX()).setY(oldPoint.getY() - 1).build();
            case DOWN -> SnakesProto.GameState.Coord.newBuilder().setX(oldPoint.getX()).setY(oldPoint.getY() + 1).build();
            case LEFT -> SnakesProto.GameState.Coord.newBuilder().setX(oldPoint.getX() - 1).setY(oldPoint.getY()).build();
            case RIGHT -> SnakesProto.GameState.Coord.newBuilder().setX(oldPoint.getX() + 1).setY(oldPoint.getY()).build();
        };
    }

    public static SnakesProto.GameState.Coord normalizeCoord(SnakesProto.GameState.Coord coord, int width, int height) {
        return SnakesProto.GameState.Coord.newBuilder()
                .setX((coord.getX() + width) % width)
                .setY((coord.getY() + height) % height)
                .build();
    }

    public static SnakesProto.GameState.Snake moveSnake(SnakesProto.GameState.Snake oldSnake, SnakesProto.Direction direction, int width, int height) {
        var newSnake = SnakesProto.GameState.Snake.newBuilder(oldSnake);
        newSnake.clearPoints();
        var newPoint = movePoint(oldSnake.getPoints(0), direction);
        newSnake.addPoints(normalizeCoord(newPoint, width, height));
        for (var idx = 1; idx < oldSnake.getPointsCount() - 1; idx++) {
            newSnake.addPoints(normalizeCoord(oldSnake.getPoints(idx), width, height));
        }
        return newSnake.build();
    }

    public SnakesProto.GameState getNextState(SnakesProto.GameState oldState) {
        var newStateBuilder = SnakesProto.GameState.newBuilder(oldState);

        var players = oldState.getPlayers().getPlayersList().stream().collect(
                Collectors.toMap(
                        SnakesProto.GamePlayer::getId,
                        e -> e
                )
        );
        var snakes = oldState.getSnakesList().stream().collect(
                Collectors.toMap(
                        SnakesProto.GameState.Snake::getPlayerId,
                        e -> e
                )
        );

        for (var playerId : players.keySet()) {
            if (!snakes.containsKey(playerId)) {
                var player = players.get(playerId);
                snakes.put(playerId,
                        SnakesProto.GameState.Snake.newBuilder()
                                .addPoints(
                                        SnakesProto.GameState.Coord.newBuilder()
                                                .setX(player.getId())
                                                .setY(player.getId())
                                                .build()
                                )
                                .setState(SnakesProto.GameState.Snake.SnakeState.ALIVE)
                                .setHeadDirection(steerChoices.getOrDefault(player.getId(), SnakesProto.Direction.UP))
                                .setPlayerId(player.getId())
                                .build()
                );
            }
        }

        newStateBuilder.clearSnakes();
        for (var snake: snakes.values()) {
            newStateBuilder.addSnakes(
                moveSnake(snake,
                        steerChoices.getOrDefault(snake.getPlayerId(), snake.getHeadDirection()),
                        oldState.getConfig().getWidth(),
                        oldState.getConfig().getHeight()
                )
            );
        }

        return newStateBuilder.build();
    }
}
