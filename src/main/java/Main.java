import agent.Dealer;
import agent.Player;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Main {
    private Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        Runtime runtime = Runtime.instance();

        Profile config = new ProfileImpl("localhost", 8888, null);
        config.setParameter("gui", "false");
        AgentContainer mc = runtime.createMainContainer(config);
        AgentController dealer, player;
        try {
            dealer = mc.createNewAgent("Dealer", Dealer.class.getName(), null);
            player = mc.createNewAgent("Player", Player.class.getName(), null);

            dealer.start();
            player.start();
        } catch (StaleProxyException e) {
            System.err.println(e.getMessage());
        }
    }
}
