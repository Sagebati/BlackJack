package agent;

import game.Actions;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

public class Player extends Agent {
    private final Logger logger = LoggerFactory.getLogger(Player.class);
    private AID dealer = null;
    private final List<Card> cards;
    private double money;

    enum States {
        SearchingDealer,
        ConnectingDealer,
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

    private Actions IA(List<Actions> possibleMoves) {
        var i = new Random().nextInt(possibleMoves.size());
        return possibleMoves.get(i);
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
        fsm.registerFirstState(new FindTable(), States.SearchingDealer.toString());
        fsm.registerState(new ConnectToTable(), States.ConnectingDealer.toString());
        fsm.registerDefaultTransition(States.SearchingDealer.toString(), States.ConnectingDealer.toString());
        addBehaviour(fsm);
    }

    class FindTable extends OneShotBehaviour {

        @Override
        public void action() {
            var dfd = new DFAgentDescription();
            var sd = new ServiceDescription();
            sd.setType("dealer");
            dfd.addServices(sd);
            logger.info("Searching for a dealer");
            while (true) {

                try {
                    var t = Arrays.stream(DFService.search(Player.this, dfd)).findFirst();
                    if (t.isPresent()) {
                        dealer = t.get().getName();
                        break;
                    }
                } catch (FIPAException e) {
                    logger.error("couldn't search in the DF for dealers");
                }

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    logger.error("Couldn't sleep the thread", e);
                }
            }
        }
    }


    class ConnectToTable extends OneShotBehaviour {

        @Override
        public void action() {
            logger.info("Connecting to the dealer");
            var message = new ACLMessage(ACLMessage.REQUEST);
            message.setSender(getAID());
            message.addReceiver(dealer);
            try {
                message.setContentObject(Proto.ASKPLAYING);
            } catch (IOException e) {
                logger.error("Couldn't add the content object to the message");
            }
            logger.info("Sending message to the dealer AID: " + dealer);
            send(message);
            var reply = blockingReceive();
            logger.info("Received reply from the dealer" + reply.toString());
        }
    }
}
