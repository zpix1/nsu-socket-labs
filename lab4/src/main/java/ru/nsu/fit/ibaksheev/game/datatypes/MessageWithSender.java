package ru.nsu.fit.ibaksheev.game.datatypes;

import lombok.Builder;
import lombok.Getter;
import me.ippolitov.fit.snakes.SnakesProto;

@Builder
public class MessageWithSender {
    @Getter
    private SnakesProto.GameMessage message;
    @Getter
    private String ip;
    @Getter
    private Integer port;
}
