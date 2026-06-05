package agents;

import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.ContractNetResponder;
import jade.domain.FIPAAgentManagement.NotUnderstoodException;
import jade.domain.FIPAAgentManagement.RefuseException;
import jade.domain.FIPAAgentManagement.FailureException;

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
        // --- THE CONTRACT NET RESPONDER ---
        // Create a template so the agent only listens for "Call For Proposal" (CFP) messages
        MessageTemplate template = MessageTemplate.MatchPerformative(ACLMessage.CFP);

        addBehaviour(new ContractNetResponder(this, template) {
            @Override
            protected ACLMessage handleCfp(ACLMessage cfp) throws NotUnderstoodException, RefuseException {
                System.out.println(getLocalName() + ": Received CFP for a job from " + cfp.getSender().getLocalName());

                // We are available, so we formulate a proposal to do the job
                ACLMessage propose = cfp.createReply();
                propose.setPerformative(ACLMessage.PROPOSE);
                propose.setContent("10"); // We propose an arbitrary "cost" or "time" of 10
                return propose;
            }

            @Override
            protected ACLMessage handleAcceptProposal(ACLMessage cfp, ACLMessage propose, ACLMessage accept) throws FailureException {
                System.out.println(getLocalName() + ": Proposal accepted! Starting work for " + accept.getSender().getLocalName());

                // TODO later: This is where Process1/Process2 will send the Modbus TCP signal
                // to the simulation to physically run the machine!

                // For now, we simulate the work instantly finishing and inform the Part
                ACLMessage inform = accept.createReply();
                inform.setPerformative(ACLMessage.INFORM);
                inform.setContent("Process completed");
                System.out.println(getLocalName() + ": Work finished. Notifying " + accept.getSender().getLocalName());
                return inform;
            }
        });
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