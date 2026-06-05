package agents;

import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;

public class ProcessAgent extends Agent {

    @Override
    protected void setup() {
        // Read the specific service type from arguments
        String serviceType = "processing_station"; // Default fallback
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            serviceType = (String) args[0];
        }

        System.out.println("Process Agent " + getLocalName() + " is ready. Providing: " + serviceType);

        // 1. Create a description for the Yellow Pages
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());

        ServiceDescription sd = new ServiceDescription();
        sd.setType(serviceType); // Use the dynamic type!
        sd.setName(getLocalName() + "_service");
        dfd.addServices(sd);

        // 2. Register with the Directory Facilitator
        try {
            DFService.register(this, dfd);
            System.out.println(getLocalName() + " registered successfully with the DF.");
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
    }

    @Override
    protected void takeDown() {
        // Always deregister from the Yellow Pages on shutdown
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        System.out.println("Process Agent " + getAID().getName() + " shutting down.");
    }
}