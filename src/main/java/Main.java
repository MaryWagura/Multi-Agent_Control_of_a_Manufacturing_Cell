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

            // Deploy the Crane
            AgentController craneAgent = mainContainer.createNewAgent("Crane", "agents.CraneAgent", new Object[0]);
            craneAgent.start();

            // Deploy the two Sources
            AgentController source1 = mainContainer.createNewAgent("Source1", "agents.SourceAgent", new Object[0]);
            source1.start();
            AgentController source2 = mainContainer.createNewAgent("Source2", "agents.SourceAgent", new Object[0]);
            source2.start();

            // Deploy the two Processes
            AgentController process1 = mainContainer.createNewAgent("Process1", "agents.ProcessAgent", new Object[0]);
            process1.start();
            AgentController process2 = mainContainer.createNewAgent("Process2", "agents.ProcessAgent", new Object[0]);
            process2.start();

            // Deploy the Sink
            AgentController sink = mainContainer.createNewAgent("Sink", "agents.SinkAgent", new Object[0]);
            sink.start();

            System.out.println("JADE Platform and all static agents started successfully!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}