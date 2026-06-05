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

    private String partType;
    private Queue<String> processPlan;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            partType = (String) args[0];
        } else {
            partType = "type1";
        }

        System.out.println("Part Agent [" + getLocalName() + "] (Type: " + partType + ") has entered the system.");

        processPlan = new LinkedList<>();

        if (partType.equals("type1")) {
            processPlan.addAll(Arrays.asList("processing_station_1", "sink_station"));
        } else if (partType.equals("type2")) {
            // Now it will specifically ask for 2, then 1!
            processPlan.addAll(Arrays.asList("processing_station_2", "processing_station_1", "sink_station"));
        }

        System.out.println("[" + getLocalName() + "] Process plan loaded: " + processPlan);

        // Start the routing engine
        addBehaviour(new RouteExecutionBehaviour());
    }

    @Override
    protected void takeDown() {
        System.out.println("Part Agent [" + getLocalName() + "] has completed its plan and left the system.");
    }

    // --- The Routing Engine ---
    private class RouteExecutionBehaviour extends Behaviour {
        private int step = 0;
        private AID targetProvider = null;

        @Override
        public void action() {
            switch (step) {
                case 0: // Step 0: Find the required service in the DF
                    if (processPlan.isEmpty()) {
                        System.out.println("[" + getLocalName() + "] All steps complete! Shutting down.");
                        step = 3; // Move to exit state
                        return;
                    }

                    String neededService = processPlan.peek();
                    System.out.println("\n[" + getLocalName() + "] Looking for service: " + neededService);

                    // Build a search template for the DF
                    DFAgentDescription template = new DFAgentDescription();
                    ServiceDescription sd = new ServiceDescription();
                    sd.setType(neededService);
                    template.addServices(sd);

                    try {
                        DFAgentDescription[] result = DFService.search(myAgent, template);
                        if (result.length > 0) {
                            // Grab the first agent that provides this service
                            targetProvider = result[0].getName();
                            System.out.println("[" + getLocalName() + "] Found provider: " + targetProvider.getLocalName());
                            step = 1;
                        } else {
                            System.out.println("[" + getLocalName() + "] No provider found for " + neededService + ". Waiting...");
                            block(2000); // Sleep for 2 seconds and try again
                        }
                    } catch (FIPAException fe) {
                        fe.printStackTrace();
                    }
                    break;

                case 1: // Step 1: Hand off to external Negotiator
                    System.out.println("[" + getLocalName() + "] Initiating Contract Net with " + targetProvider.getLocalName());

                    // Build the Call For Proposal message
                    ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                    cfp.addReceiver(targetProvider);
                    cfp.setContent(neededService); // Tell them what job we want done

                    // Add the external behavior and kill this internal one
                    myAgent.addBehaviour(new PartNegotiator(myAgent, cfp));

                    step = 3; // Terminate this specific RouteExecutionBehaviour thread
                    break;
            }
        }

        // This is called by the external negotiation behavior when a process finishes
        public void markServiceComplete() {
            processPlan.poll(); // Remove the finished job from the queue
            System.out.println("[" + getLocalName() + "] Queue updated. Remaining steps: " + processPlan);

            // Restart the routing engine to find the next service
            addBehaviour(new RouteExecutionBehaviour());
        }

        @Override
        public boolean done() {
            // The behavior terminates when step 3 is reached
            if (step == 3) {
                doDelete(); // Kill the agent when the queue is empty
                return true;
            }
            return false;
        }
    }
}