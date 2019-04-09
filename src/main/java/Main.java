import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;

public class Main {
    public static void main(String[] args) {
        Runtime runtime = Runtime.instance();

        Profile config = new ProfileImpl("localhost", 8888, null);
        config.setParameter("gui", "false");
        AgentContainer mc = runtime.createMainContainer(config);
        AgentController ac, a, b;
        try {
            a = mc.createNewAgent("A", A.class.getName(), null);

            a.start();
            String[] arg = {"" + a.get};
            b = mc.createNewAgent("B", B.class.getName(), arg);
            b.start();
        } catch (StaleProxyException e) {
            System.err.println(e.getMessage());
        }
    }
}
