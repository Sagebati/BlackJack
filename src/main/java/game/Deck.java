package game;

import java.util.ArrayList;
import java.util.List;


public class Deck {
    public enum Size {
        s32,
        s52
    }

    private final List<Card> cards;

    public Deck(Size size) {
        int s = (size == Size.s32) ? 32 : 52;

        cards = new ArrayList<Card>();
        for (int i = 0; i < s / 4; i++) {
            for (Color c : Color.values())
                cards.add(new Card(i, c));
        }
    }

    public List<Card> getCards() {
        return cards;
    }
}
