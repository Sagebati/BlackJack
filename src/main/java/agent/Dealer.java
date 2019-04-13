package agent;

import game.Action;
import game.BlackJack;
import game.Card;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.FSMBehaviour;
import jade.core.behaviours.SimpleBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.UnreadableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;

public class Dealer extends Agent {
    private final Logger logger = LoggerFactory.getLogger(Dealer.class);

    private final List<AID> players;
    private final HashMap<AID, List<Card>> playerCards;
    private final Map<AID, Double> bets;
    private final List<Card> cards;
    private final Set<Player> busted;
    private BlackJack deck;


    public Dealer() {
        this.bets = new HashMap<>();
        this.playerCards = new HashMap<>();
        this.players = new ArrayList<>(4);
        this.deck = new BlackJack(6);
        this.cards = new ArrayList<>();
        busted = new HashSet<>();
    }

    private enum States{
        Dealing,
        FinalState;
    }

    public List<AID> getPlayers() {
        return players;
    }

    private Collection<Action> legalsMoves(List<Card> cards) {
        var res = new ArrayList<Action>();
        res.add(Action.Pass);

        var s = BlackJack.score(cards);
        if (s > 21) {
            // Busted
            return new ArrayList<>();
        } else {
            res.add(Action.Card);
        }

        return res;
    }

    public void execute(Player player, javax.swing.Action action) {

    }

    @Override
    protected void setup() {
        var dfd = new DFAgentDescription();
        dfd.setName(getAID());
        var sd = new ServiceDescription();
        sd.setType("dealer");
        sd.setName(getLocalName());
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException e) {
            logger.error("Cannot register to the DFService", e);
        }

        logger.info("Registered in the DF");
        var fsm = new FSMBehaviour(this) {
            public int onEnd() {
                logger.info("FSMBehaviourCompleted");
                myAgent.doDelete();
                return super.onEnd();
            }
        };
        fsm.registerFirstState(new Dealing(), States.Dealing.toString());
        addBehaviour(fsm);
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            logger.error("Cannot deregister dealer" + getAID(), e);
        }
        super.takeDown();
    }

    private void takeCards() {
        cards.addAll(this.deck.pullCards(2));
    }

    private void collectPlayers() {
        collectPlayers(15000);
    }

    private void collectPlayers(long millis) {
        logger.info("Waiting for players");
        var message = blockingReceive(millis);
        if (message != null) {
            logger.info("Received" + message);
            var sender = message.getSender();
            try {
                var object = (Proto) message.getContentObject();
                if (object == Proto.AskPlay) {
                    var messageToSend = new ACLMessage(ACLMessage.AGREE);
                    messageToSend.addReceiver(sender);
                    send(messageToSend);
                    players.add(sender);
                    logger.info("Player added to the game" + sender);
                }
            } catch (UnreadableException e) {
                logger.error("Cannot read the content of the message of the player", e);
            }

        }
    }


    private void collectingBetsAndDistributingCards() {
        logger.info("Collecting bets");
        for (var player : players) {
            logger.info("Asking to player if he wants to play \n player: \n" + player);
            var message = new ACLMessage(ACLMessage.REQUEST);
            Proto.setContentObject(message, Proto.Bet, logger);
            message.addReceiver(player);
            send(message);
        }

        final long t = System.currentTimeMillis();
        while (System.currentTimeMillis() < t + 15000) {
            var message = blockingReceive(1000);
            if (message != null) {
                logger.info("Received" + message);
                var sender = message.getSender();
                if (players.contains(sender)) {
                    if (message.getPerformative() == ACLMessage.AGREE) {
                        var bet = Double.parseDouble(message.getContent());
                        bets.put(sender, bet);
                        playerCards.put(sender, deck.pullCards(2));
                        var messageToSend = new ACLMessage(ACLMessage.INFORM);
                        messageToSend.addReceiver(sender);
                        try {
                            messageToSend.setContentObject((Serializable) playerCards.get(sender));
                        } catch (IOException e) {
                            logger.error("Couldn't set the playerCards to send");
                        }
                        send(messageToSend);
                        logger.info("Message sent to " + sender + "with his playerCards");
                    }
                } else {
                    logger.warn("Sender :" + sender + "ignored didn't ask for play");
                }
            }
        }

    }

    private boolean handleAction(AID player, Action action) throws IOException {
        ACLMessage message;
        boolean passed = false;
        switch (action) {
            case Card:
                message = new ACLMessage(ACLMessage.AGREE);
                var card = this.deck.pullCard();
                playerCards.get(player).add(card);
                message.setContentObject(card);
                message.addReceiver(player);
                send(message);
                break;
            case Pass:
                passed = true;
                break;

        }
        return passed;
    }

    private boolean handlePlayerTurn(AID player) {
        boolean turnFinished = false;
        var messageTosend = new ACLMessage(ACLMessage.INFORM);
        messageTosend.addReceiver(player);
        var actions = legalsMoves(playerCards.get(player));
        if (actions.isEmpty()) {
            logger.info("Player busted : " + player);
            try {
                messageTosend.setContentObject(Proto.Busted);
                turnFinished = true;
            } catch (IOException e) {
                logger.error("Busted", e);
            }
        } else {
            try {
                messageTosend.setContentObject((Serializable) actions);
            } catch (IOException e) {
                logger.error("Adding actions to the message", e);
            }
        }
        send(messageTosend);
        if (!turnFinished) {
            var reply = blockingReceive();
            if (reply.getSender().equals(player)) {
                try {
                    var action = (Action) reply.getContentObject();
                    if (actions.contains(action)) {
                        turnFinished = handleAction(player, action);
                    }
                } catch (UnreadableException e) {
                    logger.error("Cannot read the action choose by the payer", e);
                } catch (IOException e) {
                    logger.error("Couldn't put the playerCards in the content object", e);
                }
            } else {
                logger.info("Message ignored doesn't come from the player");
            }
        }
        return turnFinished;
    }

    /**
     * Take care of the croupier turn.
     */
    private void handleCroupierTurn() {
        while (BlackJack.score(this.cards) < 16) {
            this.cards.add(this.deck.pullCard());
        }
    }

    private void finishGame() {
        logger.info("Finishing the game");
        if (BlackJack.score(this.cards) > 21) {
            // Everyone wins
            bets.keySet().stream().filter(p -> !busted.contains(p)).forEach(
                    p -> giveMoney(p, bets.get(p) * 2)
            );
        } else {
            bets.keySet().stream().filter(p -> !busted.contains(p)).forEach(
                    p -> {
                        if (BlackJack.score(playerCards.get(p)) > BlackJack.score(this.cards)) {
                            logger.info("Player: {}, won : {}", p, bets.get(p));
                            giveMoney(p, bets.get(p) * 2);
                        } else if (BlackJack.score(playerCards.get(p)) == BlackJack.score(this.cards)) {
                            logger.info("Player: {}, Equality", p);
                            giveMoney(p, bets.get(p));
                        } else {
                            logger.info("Player: {}, Lost", p);
                            giveMoney(p, 0);
                        }
                    }
            );
        }

    }

    private void giveMoney(AID player, double money) {
        var message = new ACLMessage(ACLMessage.INFORM);
        Proto.setContentObject(message, Proto.Money, logger);
        message.addReceiver(player);
        logger.info("Notifying player that he is gonna to have his result");
        send(message);
        ACLMessage m;
        if (money == 0) {
            m = new ACLMessage(ACLMessage.FAILURE);
            m.addReceiver(player);
        } else {
            m = new ACLMessage(ACLMessage.INFORM);
            m.setContent("" + money);
            m.addReceiver(player);
        }
        send(m);
    }

    private void clearGame() {
        logger.info("Clearing the game");
        this.bets.clear();
        this.playerCards.clear();
        this.deck = new BlackJack(6);
        this.cards.clear();
        busted.clear();
    }

    private void game() {
        // for each player who bet
        logger.info("Stating game");
        players.stream().filter(bets::containsKey).forEach(
                p -> {
                    while (!handlePlayerTurn(p)) ;
                }
        );
        handleCroupierTurn();
        finishGame();
    }

    class Dealing extends SimpleBehaviour {
        private boolean terminated = false;


        @Override
        public void action() {
            // While he dont have players, don't do nothing
            while (players.isEmpty()) {
                collectPlayers();
            }
            // while he got his first player 
            final long t = System.currentTimeMillis();
            while (System.currentTimeMillis() < t + 15000) {
                collectPlayers(150);
            }
            // taking my cards
            takeCards();
            // 15 seconds for collecting all the bets.
            collectingBetsAndDistributingCards();
            game();
            clearGame();
            terminated = true;
        }

        @Override
        public boolean done() {
            return terminated;
        }
    }
}
