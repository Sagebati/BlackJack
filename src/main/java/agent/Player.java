package agent;

import game.Action;
import game.BlackJack;
import game.Card;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.FSMBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.UnreadableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class Player extends Agent implements Intelligency<Action> {
    private final Logger logger = LoggerFactory.getLogger(Player.class);
    private AID dealer = null;
    private List<Card> cards;
    private double money;

    @Override
    public Action ai(List<Action> moves) {
        if (BlackJack.score(this.cards) > 16) {
            return Action.Pass;
        } else {
            return Action.Card;
        }
    }

    enum States {
        Searching,
        Playing
    }

    public Player() {
        this(150);
    }


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

    @Override
    protected void setup() {
        var fsm = new FSMBehaviour(this) {
            @Override
            public int onEnd() {
                logger.info("" + "FSMBheaviour completed");
                return super.onEnd();
            }
        };
        fsm.registerFirstState(new Searching(), States.Searching.toString());
        fsm.registerState(new Play(), States.Playing.toString());
        fsm.registerDefaultTransition(States.Searching.toString(), States.Playing.toString());
        addBehaviour(fsm);
    }

    class Searching extends OneShotBehaviour {
        @Override
        public void action() {
            while (dealer == null) {
                List<AID> dealers = new ArrayList<>();
                while (dealers.isEmpty()) {
                    dealers = findDealers();
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        logger.error("Couldn't stop the thread for searching dealers", e);
                    }
                }
                for (AID d : dealers) {
                    if (connectToTable(d)) {
                        dealer = d;
                        logger.info("found dealer");
                        break;
                    }
                }
            }
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean connectToTable(AID dealer) {
        var res = false;
        logger.info("Connecting to the dealer");
        var message = new ACLMessage(ACLMessage.REQUEST);
        message.addReceiver(dealer);
        try {
            message.setContentObject(Proto.AskPlay);
        } catch (IOException e) {
            logger.error("Couldn't add the content object to the message");
        }
        logger.info("Sending message to the dealer AID: " + dealer);
        send(message);
        var reply = blockingReceive();
        logger.info("Received reply from the dealer" + reply.toString());
        switch (reply.getPerformative()) {
            case ACLMessage.AGREE:
                logger.info("Dealer agreed");
                res = true;
                break;
            case ACLMessage.REFUSE:
                logger.info("Dealer rejected my offer");
                this.dealer = null;
                break;
            default:
                logger.info("Reponse not handled by the player");
                break;
        }
        return res;
    }

    private List<AID> findDealers() {
        var dfd = new DFAgentDescription();
        var sd = new ServiceDescription();
        sd.setType("dealer");
        dfd.addServices(sd);
        logger.info("Searching for a dealer");
        try {
            return Arrays.stream(DFService.search(Player.this, dfd)).
                    map(DFAgentDescription::getName)
                    .collect(Collectors.toList());
        } catch (FIPAException e) {
            logger.error("couldn't search in the DF for dealers");
        }
        return Collections.emptyList();
    }

    private void getCardsFromDealer() {
        var message = getMessageFromDealer();
        try {
            this.cards = (List<Card>) message.getContentObject();
        } catch (UnreadableException e) {
            logger.error("Couldn't get the cards from the dealer", e);
        }
    }

    private void bet(double money) {
        // Waiting intructions
        var message = getMessageFromDealer();
        try {
            var p = (Proto) message.getContentObject();
            if (p == Proto.Bet) {
                var reply = message.createReply();
                reply.setPerformative(ACLMessage.AGREE);
                reply.setContent(String.valueOf(money));
                send(reply);
                getCardsFromDealer();
                logger.info("Got cards form the dealer");
            } else {
                logger.warn("Message ignored from the dealer, doesn't respect protocol) \n message : \n" + message);
            }
        } catch (UnreadableException e) {
            e.printStackTrace();
        }
    }

    private void handleAction(Action action) {
        var reply = new ACLMessage(Proto.ChoosedAction.getValue());
        reply.addReceiver(dealer);
        try {
            reply.setContentObject(action);
        } catch (IOException e) {
            logger.error("Couldn't put the ai in the message to the dealer", e);
        }
        logger.info("Send choose action: {}", action.toString());
        send(reply);
        switch (action) {
            case Pass:
            case Double:
                break;
            case Card:
                actionFromMessageFromTheDealer(message -> {
                    if (message.getPerformative() == ACLMessage.AGREE) {
                        try {
                            cards.add((Card) message.getContentObject());
                        } catch (UnreadableException e) {
                            logger.error("Couldn't get the card from the message ", e);
                        }
                    } else {
                        logger.warn("Incoherent message form dealer" + message);
                    }
                });
        }
    }

    private boolean handleMyTurn() {
        logger.info("Handling my turn");
        final boolean[] isMyTurnFinish = {false};
        actionFromMessageFromTheDealer(message -> {
            if (message.getPerformative() == Proto.Busted.getValue()) {
                logger.info("I lost");
                isMyTurnFinish[0] = true;
            } else if (message.getPerformative() == Proto.Actions.getValue()) {
                try {
                    var actions = (List<Action>) message.getContentObject();
                    logger.info("Receiving actions");
                    handleAction(this.ai(actions));
                } catch (UnreadableException e) {
                    logger.error("Unable to get actions from the dealer", e);
                }
            } else if (message.getPerformative() == Proto.TurnFinished.getValue()) {
                isMyTurnFinish[0] = true;
            }
        });
        return isMyTurnFinish[0];
    }

    private void waitForTurn() {
        logger.info("Waiting for turn");
        actionFromMessageFromTheDealer(message -> {
            if (message.getPerformative() == Proto.YourTurn.getValue()) {
                logger.info("My turn");
            } else {
                logger.warn("Ignored message from dealer");
            }
        });
    }

    private void play() {
        bet(15);
        waitForTurn();
        while (!handleMyTurn()) ;
    }

    private void actionFromMessageFromTheDealer(ActionMessageDealer action) {
        var message = getMessageFromDealer();
        action.action(message);
    }

    private ACLMessage getMessageFromDealer() {
        if (this.dealer == null)
            throw new NullPointerException("Dealer not initialised");
        AID sender = null;
        while (!dealer.equals(sender)) {
            var message = blockingReceive();
            sender = message.getSender();
            if (sender.equals(dealer)) {
                return message;
            } else {
                logger.info("Message ignored from" + message.getSender().getName());
            }
        }
        // Can't happend
        return null;
    }

    interface ActionMessageDealer {
        void action(ACLMessage message);
    }

    class Play extends OneShotBehaviour {
        @Override
        public void action() {
            play();
        }
    }
}
