package agent;

import game.Actions;
import game.BlackJack;
import game.Card;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.FSMBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SimpleBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.UnreadableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;

public class Dealer extends Agent {
    private final Logger logger = LoggerFactory.getLogger(Dealer.class);

    private final List<AID> players;
    private final HashMap<AID, List<Card>> cards;
    private final Set<States> states;
    private final Map<AID, Double> bets;
    private BlackJack deck;

    public Dealer() {
        this.bets = new HashMap<>();
        this.cards = new HashMap<>();
        this.states = new HashSet<>();
        this.players = new ArrayList<>(4);
        this.deck = new BlackJack(6);
    }

    public List<AID> getPlayers() {
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
        fsm.registerFirstState(new Dealing(), States.TableEmpty.toString());
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
                if (object == Proto.ASKPLAYING) {
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
            Proto.setContentObject(message, Proto.BET, logger);
            message.setSender(getAID());
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
                        cards.put(sender, deck.pullCards(2));
                        var messageToSend = new ACLMessage(ACLMessage.INFORM);
                        messageToSend.setSender(getAID());
                        messageToSend.addReceiver(sender);
                        try {
                            messageToSend.setContentObject((Serializable) cards.get(sender));
                        } catch (IOException e) {
                            logger.error("Couldn't set the cards to send");
                        }
                        send(messageToSend);
                        logger.info("Message sent to " + sender + "with his cards");
                    }
                } else {
                    logger.warn("Sender :" + sender + "ignored didn't ask for play");
                }
            }
        }

    }

    class Dealing extends SimpleBehaviour {
        private boolean terminated = false;


        @Override
        public void action() {
            // While he doent have players don't do nothing
            while (players.isEmpty()) {
                collectPlayers();
            }
            // while he got his first player 
            final long t = System.currentTimeMillis();
            while (System.currentTimeMillis() < t + 15000) {
                collectPlayers(150);
            }

            // 15 seconds for collecting all the bets.
            collectingBetsAndDistributingCards();
            terminated = true;
        }

        @Override
        public boolean done() {
            return terminated;
        }
    }
}
