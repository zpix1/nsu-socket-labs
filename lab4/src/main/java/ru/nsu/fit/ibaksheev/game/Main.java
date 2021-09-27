package ru.nsu.fit.ibaksheev.game;

import static me.ippolitov.fit.snakes.SnakesProto.*;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        var m = new UnicastMsgManager();
        var msg = m.getMessagePublisher();
        System.out.println("before emit");
        msg.offer(new UnicastMsgManager.GameMessageWrapper(null, "kek", 123));
        System.out.println("after emit");
        Thread.sleep(20000);
    }
}
