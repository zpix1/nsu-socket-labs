package ru.nsu.fit.ibaksheev.game.io.datatypes;

import lombok.AllArgsConstructor;
import lombok.Data;
import me.ippolitov.fit.snakes.SnakesProto;

@Data
@AllArgsConstructor
public class PlayerSignature {
    private String ip;
    private int port;

    public PlayerSignature(SnakesProto.GamePlayer player) {
        this.ip = player.getIpAddress();
        this.port = player.getPort();
    }
}
