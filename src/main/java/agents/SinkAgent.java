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

public class SinkAgent extends Agent {
    private int startRegister;
    private String serviceType;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        serviceType = (String) args[0];
        String myLocationX = (String) args[1];

        // --- Read the Modbus wire address dynamically from JSON args ---
        startRegister = Integer.parseInt((String) args[2]);

        System.out.println(getLocalName() + " ready at X:" + myLocationX + " wired to Modbus Reg:" + startRegister);

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

        // Update the Network Listener to reply with BOTH the X-Coord and the Register separated by a comma
        jade.lang.acl.MessageTemplate reqTemplate = jade.lang.acl.MessageTemplate.MatchOntology("CONFIG_REQUEST");
        addBehaviour(new jade.core.behaviours.CyclicBehaviour() {
            @Override
            public void action() {
                jade.lang.acl.ACLMessage msg = myAgent.receive(reqTemplate);
                if (msg != null) {
                    jade.lang.acl.ACLMessage reply = msg.createReply();
                    reply.setPerformative(jade.lang.acl.ACLMessage.INFORM);
                    reply.setContent(myLocationX + "," + startRegister); // Send "450,4"
                    myAgent.send(reply);
                } else {
                    block();
                }
            }
        });


        // --- THE CONTRACT NET RESPONDER ---
        // A template so the agent only listens for "Call For Proposal" (CFP) messages
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