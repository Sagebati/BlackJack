package game;

import javax.swing.*;
import java.util.*;

public class BlackJack {
    private Deque<Card> cards;
    private int turn;

    public BlackJack(int numberOfPacks) {
        this.turn = 0;
        this.cards = new ArrayDeque<>();
        var buffer = new ArrayList<Card>();
        for (int i = 0; i < numberOfPacks; i++) {
            buffer.addAll(new Deck(Deck.Size.s52).getCards());
        }
        Collections.shuffle(buffer);
        this.cards.addAll(buffer);
    }

    public Card pullCard() {
        var res = cards.getFirst();
        cards.removeFirst();
        return res;
    }

    public int getTurn() {
        return turn;
    }



    public static int score(Collection<Card> cards) {
        var total = 0;
        for (Card c : cards) {
            if (c.getValue() == 1) {
                total += 11;
            } else {
                total += c.getValue();
            }
        }

        var aces = cards.stream().filter(card -> card.getValue() == 1).count();
        while (total > 21 && aces != 0) {
            total -= 10;
            aces--;
        }
        return total;
    }
}
