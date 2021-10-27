package ru.nsu.fit.ibaksheev.game.snake;

import ru.nsu.fit.ibaksheev.game.io.PlayerController;

public class SnakeViewController {
    private PlayerController playerController;

    public SnakeViewController(PlayerController playerController, SnakeView snakeView) {
        playerController.getNewMessageSubject().subscribe(messageWithSender -> {
            if (messageWithSender.getMessage().hasState()) {
                snakeView.setState(messageWithSender.getMessage().getState().getState());
            }
        });
        snakeView.getControlObservable().subscribe(System.out::println);
    }
}
