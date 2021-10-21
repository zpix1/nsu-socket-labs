package ru.nsu.fit.ibaksheev.game.datatypes;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PlayerSignature {
    private String ip;
    private int port;
}
