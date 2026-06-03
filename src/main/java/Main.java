import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;

public class Main {
    public static void main(String[] args) {
        // 1. Get the JADE Runtime instance
        Runtime rt = Runtime.instance();

        // 2. Create a default profile
        Profile profile = new ProfileImpl();
        profile.setParameter(Profile.MAIN_HOST, "localhost");
        profile.setParameter(Profile.GUI, "true"); // Launches the JADE visual console

        // 3. Create the Main Container
        AgentContainer mainContainer = rt.createMainContainer(profile);

        try {

            // 4. Deploy the Crane Agent
            AgentController craneAgent = mainContainer.createNewAgent("Crane", "agents.CraneAgent", new Object[0]);
            craneAgent.start();

            System.out.println("JADE Platform started successfully!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}