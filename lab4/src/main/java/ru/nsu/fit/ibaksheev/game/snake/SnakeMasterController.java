package ru.nsu.fit.ibaksheev.game.snake;

import io.reactivex.rxjava3.core.Observable;
import me.ippolitov.fit.snakes.SnakesProto;

import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class SnakeMasterController {
    private static final int FOOD_STATIC = 1;
    private static final int FOOD_PER_PLAYER = 1;

    private final Map<Integer, SnakesProto.Direction> steerChoices;

    public SnakeMasterController(Observable<SnakeView.Control> controlObservable) {
        steerChoices = new ConcurrentHashMap<>();
        controlObservable.subscribe(control -> steerChoices.put(control.getPlayerId(), control.getDirection()));
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

    public SnakesProto.GameState.Snake moveSnake(SnakesProto.GameState.Snake oldSnake, SnakesProto.Direction direction, int width, int height, boolean eaten) {
        var newDirection = canSteer(oldSnake.getHeadDirection(), direction) ? direction : oldSnake.getHeadDirection();
        var newSnake = SnakesProto.GameState.Snake.newBuilder(oldSnake);
        newSnake.setHeadDirection(newDirection);
        newSnake.clearPoints();
        var newPoint = movePoint(oldSnake.getPoints(0), newDirection);
        newPoint = normalizeCoord(newPoint, width, height);
        newSnake.addPoints(newPoint);
        newSnake.addPoints(
                SnakesProto.GameState.Coord.newBuilder()
                        .setX(oldSnake.getPoints(0).getX() - newPoint.getX())
                        .setY(oldSnake.getPoints(0).getY() - newPoint.getY())
                        .build()
        );
        for (var idx = 1; idx < oldSnake.getPointsCount() - (eaten ? 0 : 1); idx++) {
            newSnake.addPoints(oldSnake.getPoints(idx));
        }
        return newSnake.build();
    }

    private static boolean canSteer(SnakesProto.Direction a, SnakesProto.Direction b) {
        return switch (a) {
            case UP -> switch (b) {
                case DOWN -> false;
                default -> true;
            };
            case DOWN -> switch (b) {
                case UP -> false;
                default -> true;
            };
            case LEFT -> switch (b) {
                case RIGHT -> false;
                default -> true;
            };
            case RIGHT -> switch (b) {
                case LEFT -> false;
                default -> true;
            };
        };
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
                                .setHeadDirection(steerChoices.getOrDefault(player.getId(), SnakesProto.Direction.DOWN))
                                .setPlayerId(player.getId())
                                .build()
                );
            }
        }

        var allFoods = new HashSet<>(oldState.getFoodsList());
        newStateBuilder.clearSnakes();
        for (var snake : snakes.values()) {
            var eatenFood = oldState.getFoodsList().stream().filter(food -> food.equals(snake.getPoints(0))).findAny();
            newStateBuilder.addSnakes(
                    moveSnake(snake,
                            steerChoices.getOrDefault(snake.getPlayerId(), snake.getHeadDirection()),
                            oldState.getConfig().getWidth(),
                            oldState.getConfig().getHeight(),
                            eatenFood.isPresent()
                    )
            );
            eatenFood.ifPresent(allFoods::remove);
        }

        newStateBuilder.clearFoods();
        for (var food : allFoods) {
            newStateBuilder.addFoods(food);
        }
        var foodCount = FOOD_STATIC + (FOOD_PER_PLAYER * snakes.size());
        for (var i = 0; i < foodCount - allFoods.size(); i++) {
            newStateBuilder.addFoods(SnakesProto.GameState.Coord.newBuilder()
                    .setX(ThreadLocalRandom.current().nextInt(0, oldState.getConfig().getWidth()))
                    .setY(ThreadLocalRandom.current().nextInt(0, oldState.getConfig().getHeight()))
                    .build()
            );
        }

        return newStateBuilder.build();
    }
}
