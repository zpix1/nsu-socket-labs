package ru.nsu.fit.ibaksheev.game;

import java.io.IOException;

public class SnakeController {
    public enum Role {
        MASTER,
        DEPUTY,
        NORMAL
    }

    private final UnicastManager unicastManager;
    private final MulticastManager multicastManager;
    private final PlayersManager playersManager;

    private volatile Role role;

    public SnakeController(int listenPort, Role role) throws IOException {
        this.role = role;

        unicastManager = new UnicastManager(listenPort);
        multicastManager = new MulticastManager();
        playersManager = new PlayersManager();
    }

    private void announceWorker() {

    }
}
