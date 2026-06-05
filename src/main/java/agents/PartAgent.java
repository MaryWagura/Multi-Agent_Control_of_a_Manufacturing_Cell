package agents;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.Behaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

public class PartAgent extends Agent {

    // Internal state variables for the Part
    private String partType;
    private Queue<String> processPlan; // Acts as the dynamic itinerary for the part

    // Tracks if we have taken the Crane to the current required service yet
    private boolean isAtDestination = false;

    @Override
    protected void setup() {
        // --- INITIALIZATION ---
        // Read the arguments passed by Main.java to determine product type
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            partType = (String) args[0];
        } else {
            partType = "type1"; // Fallback safety
        }

        System.out.println("Part Agent [" + getLocalName() + "] (Type: " + partType + ") has entered the system.");

        // --- ROUTE GENERATION ---
        // Build the specific process plan based on the product type (Requirements R1 & R2)
        processPlan = new LinkedList<>();

        if (partType.equals("type1")) {
            // Type 1: Process 1 -> Sink
            processPlan.addAll(Arrays.asList("processing_station_1", "sink_station"));
        } else if (partType.equals("type2")) {
            // Type 2: Process 2 -> Process 1 -> Sink
            processPlan.addAll(Arrays.asList("processing_station_2", "processing_station_1", "sink_station"));
        }

        System.out.println("[" + getLocalName() + "] Process plan loaded: " + processPlan);

        // Kick off the routing engine behavior
        addBehaviour(new RouteExecutionBehaviour());
    }

    @Override
    protected void takeDown() {
        System.out.println("Part Agent [" + getLocalName() + "] has completed its plan and left the system.");
    }

    // --- NEW STATE MANAGEMENT LOGIC ---
    public void negotiationFinished() {
        if (!isAtDestination) {
            // If we weren't at the destination, it means the Crane just finished dropping us off
            System.out.println("[" + getLocalName() + "] Successfully transported to destination.");
            isAtDestination = true; // Flip the flag
            addBehaviour(new RouteExecutionBehaviour()); // Restart behavior to find the machine
        } else {
            // If we were at the destination, the Machine just finished its work
            processPlan.poll();
            System.out.println("[" + getLocalName() + "] Service complete. Queue updated. Remaining steps: " + processPlan);
            isAtDestination = false; // Reset the flag for the NEXT stop in the queue
            addBehaviour(new RouteExecutionBehaviour()); // Restart behavior to look for the next transport
        }
    }

    // --- STATE MANAGEMENT ---
    // This public method allows external behaviors (like PartNegotiator) to inform
    // the main agent that a step is done, so it can update its queue and move on.
    public void markServiceComplete() {
        processPlan.poll(); // Pop the completed service off the itinerary queue
        System.out.println("[" + getLocalName() + "] Queue updated. Remaining steps: " + processPlan);

        // Restart the routing engine to find the next service in the queue
        addBehaviour(new RouteExecutionBehaviour());
    }

    // --- THE ROUTING ENGINE (Internal State Machine) ---
    private class RouteExecutionBehaviour extends Behaviour {
        private int step = 0; // Tracks the current state of the behavior
        private AID targetProvider = null; // Holds the ID of the machine we discover
        private String neededService = null; // Class-level variable so it survives between steps

        @Override
        public void action() {
            switch (step) {
                case 0: // STATE 0: Read Queue and Search Directory Facilitator (DF)

                    // Check if the itinerary is empty
                    if (processPlan.isEmpty()) {
                        System.out.println("[" + getLocalName() + "] All steps complete! Shutting down.");
                        step = 3; // Jump to termination state
                        return;
                    }

                    // Peek at (but do not remove) the next required service
                    neededService = processPlan.peek();
                    System.out.println("\n[" + getLocalName() + "] Looking for service: " + neededService);

                    // Build a search template for the DF Yellow Pages
                    DFAgentDescription template = new DFAgentDescription();
                    ServiceDescription sd = new ServiceDescription();
                    sd.setType(neededService);
                    template.addServices(sd);

                    try {
                        // Query the DF
                        DFAgentDescription[] result = DFService.search(myAgent, template);
                        if (result.length > 0) {
                            // Match found! Save the Agent ID (AID) of the provider
                            targetProvider = result[0].getName();
                            System.out.println("[" + getLocalName() + "] Found provider: " + targetProvider.getLocalName());
                            step = 1; // Move to negotiation state
                        } else {
                            System.out.println("[" + getLocalName() + "] No provider found for " + neededService + ". Waiting...");
                            block(2000); // Sleep thread for 2 seconds and retry
                        }
                    } catch (FIPAException fe) {
                        fe.printStackTrace();
                    }
                    break;

                case 1: // STATE 1: Hand off to the External Negotiator
                    System.out.println("[" + getLocalName() + "] Initiating Contract Net with " + targetProvider.getLocalName());

                    // 1. Build the Call For Proposal (CFP) message
                    ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                    cfp.addReceiver(targetProvider); // Address it to the machine we just found
                    cfp.setContent(neededService); // Tell them what specific job we need done

                    // 2. Attach the external PartNegotiator behavior to handle the conversation
                    myAgent.addBehaviour(new PartNegotiator(myAgent, cfp));

                    // 3. Terminate THIS specific routing behavior thread (the Negotiator takes over)
                    step = 3;
                    break;
            }
        }

        @Override
        public boolean done() {
            // The behavior formally terminates when step == 3
            if (step == 3) {
                // If the queue is totally empty when we hit step 3, kill the agent entirely
                if(processPlan.isEmpty()){
                    doDelete();
                }
                return true;
            }
            return false;
        }
    }
}