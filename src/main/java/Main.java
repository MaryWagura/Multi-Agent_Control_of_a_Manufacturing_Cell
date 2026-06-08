import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

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
            // Deploy the Crane (This stays the same! The Crane fetches coordinates dynamically later)
            AgentController craneAgent = mainContainer.createNewAgent("Crane", "agents.CraneAgent", new Object[0]);
            craneAgent.start();

            // --- NEW: READ EXTERNAL JSON CONFIGURATION ---
            Map<String, String> layout = new HashMap<>();
            try {
                String jsonContent = new String(Files.readAllBytes(Paths.get("factory_layout.json")));
                // A quick native parser for a flat JSON file
                jsonContent = jsonContent.replaceAll("[{}\"\\s]", "");
                String[] pairs = jsonContent.split(",");
                for (String pair : pairs) {
                    String[] kv = pair.split(":");
                    if (kv.length == 2) layout.put(kv[0], kv[1]);
                }
                System.out.println("Successfully loaded factory_layout.json: " + layout);
            } catch (Exception e) {
                System.err.println("CRITICAL ERROR: Could not read factory_layout.json. " + e.getMessage());
                return; // Abort startup if config is missing
            }

            // --- DEPLOY MODULES USING EXTERNAL CONFIGURATIONS ---

            // Deploy Sources (Arg 1: Name, Arg 2: Port, Arg 3: X-Coordinate from JSON)
            AgentController source1 = mainContainer.createNewAgent("Source1", "agents.SourceAgent",
                    new Object[]{"source_station_1", "8001", layout.get("source_station_1")});
            source1.start();

            AgentController source2 = mainContainer.createNewAgent("Source2", "agents.SourceAgent",
                    new Object[]{"source_station_2", "8002", layout.get("source_station_2")});
            source2.start();

            // Deploy Processes (Arg 1: Name, Arg 2: X-Coordinate from JSON)
            AgentController process1 = mainContainer.createNewAgent("Process1", "agents.ProcessAgent",
                    new Object[]{"processing_station_1", layout.get("processing_station_1")});
            process1.start();

            AgentController process2 = mainContainer.createNewAgent("Process2", "agents.ProcessAgent",
                    new Object[]{"processing_station_2", layout.get("processing_station_2")});
            process2.start();

            // Deploy Sink (Arg 1: Name, Arg 2: X-Coordinate from JSON)
            AgentController sink = mainContainer.createNewAgent("Sink", "agents.SinkAgent",
                    new Object[]{"sink_station", layout.get("sink_station")});
            sink.start();

            System.out.println("JADE Platform and all static agents started successfully!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}