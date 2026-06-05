package agents;

import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;

public class SinkAgent extends Agent {

    @Override
    protected void setup() {
        System.out.println("Sink Agent " + getLocalName() + " is ready.");

        // --- DF REGISTRATION ---
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());

        ServiceDescription sd = new ServiceDescription();
        // The service type is what Part agents will query when they reach the end of their route
        sd.setType("sink_station");
        sd.setName(getLocalName() + "_service");
        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
            System.out.println(getLocalName() + " registered successfully with the DF.");
        } catch (FIPAException fe) {
            System.err.println("Error registering " + getLocalName() + " with the DF.");
            fe.printStackTrace();
        }

        // TODO for later (Phase 4):
        // Add a behavior to accept incoming parts. When a Part agent finishes its process plan,
        // it will negotiate with this Sink agent to execute its final move out of the system.
    }

    @Override
    protected void takeDown() {
        // --- CLEANUP ---
        // Remove the agent from the Yellow Pages before terminating the thread
        try {
            DFService.deregister(this);
            System.out.println(getLocalName() + " deregistered from the DF.");
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        System.out.println("Sink Agent " + getAID().getName() + " shutting down.");
    }
}