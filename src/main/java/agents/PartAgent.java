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
    // Track where we physically are!
    private String currentLocation;

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
            currentLocation = "source_station_1";
            processPlan.addAll(Arrays.asList("processing_station_1", "sink_station"));
        } else if (partType.equals("type2")) {
            currentLocation = "source_station_2";
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

    // --- STATE MANAGEMENT LOGIC ---
    // This public method allows external behaviors (like PartNegotiator) to inform
    // the main agent that a step (transport OR processing) is done.
    public void negotiationFinished() {
        if (!isAtDestination) {
            // Crane finished dropping us off
            System.out.println("[" + getLocalName() + "] Successfully transported to destination.");

            // Update our current physical location to wherever the Crane just went to!
            currentLocation = processPlan.peek();
            isAtDestination = true;
            addBehaviour(new RouteExecutionBehaviour());
        } else {
            // Machine finished
            processPlan.poll();
            System.out.println("[" + getLocalName() + "] Service complete. Queue updated. Remaining steps: " + processPlan);
            isAtDestination = false;
            addBehaviour(new RouteExecutionBehaviour());
        }
    }

    // --- THE ROUTING ENGINE (Internal State Machine) ---
    private class RouteExecutionBehaviour extends Behaviour {
        private int step = 0; // Tracks the current state of the behavior
        private AID targetProvider = null; // Holds the ID of the machine/crane we discover
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

                    // Build a search template for the DF Yellow Pages
                    DFAgentDescription template = new DFAgentDescription();
                    ServiceDescription sd = new ServiceDescription();

                    // THE CRANE FIX: Determine if we need a taxi or a machine
                    if (!isAtDestination) {
                        // We need a ride. Ask the DF for the transport service.
                        System.out.println("\n[" + getLocalName() + "] Needs to go to: " + neededService + ". Hailing Crane...");
                        sd.setType("transport_service");
                    } else {
                        // We arrived. Ask the DF for the actual machine.
                        System.out.println("\n[" + getLocalName() + "] Arrived! Requesting service from: " + neededService);
                        sd.setType(neededService);
                    }

                    template.addServices(sd); // Attach the service requirement to the search template

                    try {
                        // Query the DF
                        DFAgentDescription[] result = DFService.search(myAgent, template);
                        if (result.length > 0) {
                            // Match found! Save the Agent ID (AID) of the provider
                            targetProvider = result[0].getName();
                            System.out.println("[" + getLocalName() + "] Found provider: " + targetProvider.getLocalName());
                            step = 1; // Move to negotiation state
                        } else {
                            System.out.println("[" + getLocalName() + "] No provider found. Waiting...");
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
                    cfp.addReceiver(targetProvider); // Address it to the machine or crane we just found

                    // 2. SET THE CONTENT (THIS IS THE UPDATED PART!)
                    // If we are talking to the Crane, send "PickupLocation,DropoffLocation".
                    // Otherwise (talking to a machine), just send "DropoffLocation".
                    if (!isAtDestination) {
                        cfp.setContent(currentLocation + "," + neededService);
                    } else {
                        cfp.setContent(neededService);
                    }

                    // 3. Attach the external PartNegotiator behavior to handle the conversation
                    myAgent.addBehaviour(new PartNegotiator(myAgent, cfp));

                    // 4. Terminate THIS specific routing behavior thread (the Negotiator takes over)
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