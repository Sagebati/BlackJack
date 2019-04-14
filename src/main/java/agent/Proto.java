package agent;

import jade.lang.acl.ACLMessage;
import org.slf4j.Logger;

import java.io.IOException;

public enum Proto {
    AskPlay(1000),
    Bet(1100),
    Busted(1200),
    Money(1300),
    EndGame(1400),
    YourTurn(1500),
    Actions(1600),
    TurnFinished(1700),
    ChoosedAction(1800),
    Result(1900);


    private int value;

    Proto(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static void setContentObject(ACLMessage message, Proto proto, Logger logger) {
        try {
            message.setContentObject(proto);
        } catch (IOException e) {
            logger.error("Couldn't serialise object", e);
        }
    }
}
