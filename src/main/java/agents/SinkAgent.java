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

        // --- DF REGISTRATION ---
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());

        ServiceDescription sd = new ServiceDescription();
        // FIX: Use the dynamic variable, never hardcode!
        sd.setType(serviceType);
        sd.setName(getLocalName() + "_service");
        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
            System.out.println(getLocalName() + " registered successfully with the DF.");
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        // --- CONFIG LISTENER (For the Crane) ---
        jade.lang.acl.MessageTemplate reqTemplate = jade.lang.acl.MessageTemplate.MatchOntology("CONFIG_REQUEST");
        addBehaviour(new jade.core.behaviours.CyclicBehaviour() {
            @Override
            public void action() {
                jade.lang.acl.ACLMessage msg = myAgent.receive(reqTemplate);
                if (msg != null) {
                    jade.lang.acl.ACLMessage reply = msg.createReply();
                    reply.setPerformative(jade.lang.acl.ACLMessage.INFORM);
                    reply.setContent(myLocationX + "," + startRegister);
                    myAgent.send(reply);
                } else {
                    block();
                }
            }
        });

        // --- THE CONTRACT NET RESPONDER ---
        MessageTemplate template = MessageTemplate.MatchPerformative(ACLMessage.CFP);
        addBehaviour(new ContractNetResponder(this, template) {
            @Override
            protected ACLMessage handleCfp(ACLMessage cfp) throws NotUnderstoodException, RefuseException {
                ACLMessage propose = cfp.createReply();
                propose.setPerformative(ACLMessage.PROPOSE);
                propose.setContent("10");
                return propose;
            }

            @Override
            protected ACLMessage handleAcceptProposal(ACLMessage cfp, ACLMessage propose, ACLMessage accept) throws FailureException {
                System.out.println(getLocalName() + ": Part arrived. Consuming part " + accept.getSender().getLocalName());

                // The Sink usually just deletes/swallows the part, so we send INFORM instantly.
                ACLMessage inform = accept.createReply();
                inform.setPerformative(ACLMessage.INFORM);
                inform.setContent("Process completed");
                return inform;
            }
        });
    }

    @Override
    protected void takeDown() {
        try { DFService.deregister(this); } catch (FIPAException fe) { fe.printStackTrace(); }
        System.out.println("Sink Agent " + getAID().getName() + " shutting down.");
    }
}