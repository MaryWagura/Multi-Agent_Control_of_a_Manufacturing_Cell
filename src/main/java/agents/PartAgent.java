package agents;

import jade.core.Agent;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

public class PartAgent extends Agent {

    private String partType;
    private Queue<String> processPlan;

    @Override
    protected void setup() {
        // 1. Read the arguments passed during agent creation
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            partType = (String) args[0];
        } else {
            partType = "type1"; // Default fallback
        }

        System.out.println("Part Agent [" + getLocalName() + "] (Type: " + partType + ") has entered the system.");

        // 2. Build the process plan based on the product type (Requirements R1 & R2)
        processPlan = new LinkedList<>();

        if (partType.equals("type1")) {
            // Type 1 needs Process 1, then the Sink
            processPlan.addAll(Arrays.asList("processing_station", "sink_station"));
        } else if (partType.equals("type2")) {
            // Type 2 needs Process 2, then Process 1, then the Sink
            processPlan.addAll(Arrays.asList("processing_station_2", "processing_station", "sink_station"));
        }

        System.out.println("[" + getLocalName() + "] Process plan loaded: " + processPlan);

        // TODO (Next Phase):
        // Implement a JADE FSMBehaviour (Finite State Machine) to:
        // 1. Pop the next required service off the queue.
        // 2. Search the DF for agents providing that service.
        // 3. Initiate FIPA Contract Net Protocol (CFP) to negotiate with them.
        // 4. Contact the Crane for transport.
    }

    @Override
    protected void takeDown() {
        System.out.println("Part Agent [" + getLocalName() + "] has completed its plan and left the system.");
    }
}