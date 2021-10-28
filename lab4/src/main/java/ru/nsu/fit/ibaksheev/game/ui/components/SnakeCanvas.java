package ru.nsu.fit.ibaksheev.game.ui.components;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import me.ippolitov.fit.snakes.SnakesProto;
import ru.nsu.fit.ibaksheev.game.snake.SnakeView;

import javax.swing.*;
import java.awt.*;

public class SnakeCanvas extends JPanel implements SnakeView {
    private final int canvasWidth;
    private final int canvasHeight;

    private final Subject<Control> controlSubject = PublishSubject.create();

    private SnakesProto.GameState state;

    public SnakeCanvas() {
        canvasWidth = 500;
        canvasHeight = 500;
        setSize(canvasWidth, canvasHeight);
        setPreferredSize(new Dimension(canvasWidth, canvasHeight));
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        drawState((Graphics2D) g);
    }

    private void drawState(Graphics2D canvas) {
        if (state == null) {
            return;
        }

        int width = state.getConfig().getWidth();
        int height = state.getConfig().getHeight();

        int cellWidth = canvasWidth / width;
        int cellHeight = canvasHeight / height;

        canvas.clearRect(0, 0, canvasWidth, canvasHeight);
        canvas.setStroke(new BasicStroke(0.5f));
        canvas.setColor(Color.BLACK);

        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                canvas.drawRect(i * cellWidth, j * cellHeight, cellWidth, cellHeight);
            }
        }

        for (var snake: state.getSnakesList()) {
            for (var point: snake.getPointsList()) {
                canvas.fillRect(point.getX() * cellWidth, point.getY() * cellHeight, cellWidth, cellHeight);
                break;
            }
        }
    }

    @Override
    public void setState(SnakesProto.GameState state) {
        this.state = state;
        repaint();
    }

    @Override
    public Observable<Control> getControlObservable() {
        return controlSubject;
    }
}
