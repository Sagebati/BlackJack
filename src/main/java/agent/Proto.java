package agent;

import jade.lang.acl.ACLMessage;
import org.slf4j.Logger;

import java.io.IOException;

public enum Proto {
    AskPlay,
    Bet,
    Busted,
    Money,
    EndGame;

    public static void setContentObject(ACLMessage message, Proto proto, Logger logger) {
        try {
            message.setContentObject(proto);
        } catch (IOException e) {
            logger.error("Couldn't serialise object", e);
        }
    }
}
