package agents;

import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;

public class SourceAgent extends Agent {

    @Override
    protected void setup() {
        System.out.println("Source Agent " + getLocalName() + " is ready.");

        // --- DF REGISTRATION (The "Yellow Pages") ---
        // 1. Create the main description for this specific agent instance
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID()); // Associate the description with this agent's unique ID

        // 2. Define the specific service this agent provides
        ServiceDescription sd = new ServiceDescription();
        sd.setType("source_station"); // The generic category other agents will search for
        sd.setName(getLocalName() + "_service"); // A unique name for this specific instance's service
        dfd.addServices(sd); // Attach the service to the agent's description

        // 3. Publish the description to the Directory Facilitator
        try {
            // DFService is a static utility class provided by JADE to interact with the DF agent
            DFService.register(this, dfd);
            System.out.println(getLocalName() + " registered successfully with the DF.");
        } catch (FIPAException fe) {
            // FIPAExceptions occur if the DF is unreachable or if the agent is already registered
            System.err.println("Error registering " + getLocalName() + " with the DF.");
            fe.printStackTrace();
        }

        // TODO for later (Phase 4 & 5):
        // Add a CyclicBehaviour here to listen for FIPA-ACL messages from the LLM_Order_Agent
        // When a message is received, this agent should spawn a new PartAgent.
    }

    @Override
    protected void takeDown() {
        // --- CLEANUP ---
        // It is critical to deregister when the agent dies, otherwise the DF will
        // keep advertising a "ghost" service, leading to failed negotiations from Part agents.
        try {
            DFService.deregister(this);
            System.out.println(getLocalName() + " deregistered from the DF.");
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        System.out.println("Source Agent " + getAID().getName() + " shutting down.");
    }
}