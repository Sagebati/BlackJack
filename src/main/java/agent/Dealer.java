package agent;

import game.Actions;
import game.BlackJack;
import game.Card;
import jade.core.Agent;

import javax.swing.*;
import java.util.*;

public class Dealer extends Agent {
    public Dealer() {
        this.bets = new HashMap<>();
        this.states = new HashSet<>();
        this.players = new ArrayList<>();
    }

    public List<Player> getPlayers() {
        return players;
    }

    public Set<States> getStates() {
        return states;
    }

    public enum States {
        MyTurn,
        Dealing,
        TableEmpty
    }

    private final List<Player> players;
    private final Set<States> states;
    private final Map<Player, Double> bets;

    public Collection<Actions> legalsMoves(Player player) {
        var res = new ArrayList<Actions>();
        res.add(Actions.Pass);
        if (bets.containsKey(player)) {
            res.add(Actions.Card);
        } else {
            res.add(Actions.Bet);
        }

        var s = BlackJack.score(player.getCards());
        if (s > 21) {
            // Busted
            return new ArrayList<>();
        }

        return res;
    }

    public void execute(Player player, Action action) {
    }

    @Override
    protected void setup() {
        super.setup();
    }
}
