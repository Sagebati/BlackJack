package agent;

import game.Actions;
import game.BlackJack;
import game.Card;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
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

import javax.swing.*;
import java.util.*;

public class Dealer extends Agent {
    private final Logger logger = LoggerFactory.getLogger(Dealer.class);

    private final List<Player> players;
    private final Set<States> states;
    private final Map<Player, Double> bets;

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
        fsm.registerFirstState(new Waiting(), States.TableEmpty.toString());
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

    class Waiting extends OneShotBehaviour {

        @Override
        public void action() {
            logger.info("Wating for players");
            var message = blockingReceive();
            logger.info("Received" + message);
            var sender = message.getSender();
            try {
                var object = (Proto) message.getContentObject();
                if (object == Proto.ASKPLAYING) {
                    var messageToSend = new ACLMessage(ACLMessage.AGREE);
                    logger.info("Repliying to " + sender);
                    messageToSend.addReceiver(sender);
                    send(messageToSend);
                }
            } catch (UnreadableException e) {
                e.printStackTrace();
            }
        }


    }

}
