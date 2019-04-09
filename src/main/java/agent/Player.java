package agent;

import game.Card;
import jade.core.Agent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Player extends Agent {
    private final List<Card> cards;
    private double money;

    public Player(double money) {
        this.cards = new ArrayList<>();
        this.money = money;
    }

    public List<Card> getCards() {
        return cards;
    }

    public double getMoney() {
        return money;
    }
}
