package agents;

import jade.core.Agent;
import jade.lang.acl.ACLMessage;
import jade.proto.ContractNetInitiator;
import java.util.Vector;

public class PartNegotiator extends ContractNetInitiator {

    // Constructor requires the agent and the CFP message
    public PartNegotiator(Agent a, ACLMessage cfp) {
        super(a, cfp);
    }

    @Override
    protected void handleAllResponses(Vector responses, Vector acceptances) {
        // Step 1: Process the proposals from the machines
        ACLMessage bestPropose = null;
        ACLMessage accept = null;

        for (int i = 0; i < responses.size(); i++) {
            ACLMessage msg = (ACLMessage) responses.get(i);
            if (msg.getPerformative() == ACLMessage.PROPOSE) {
                // In a complex system, you'd compare costs here.
                // For now, we just grab the first valid proposal.
                bestPropose = msg;
                break;
            }
        }

        if (bestPropose != null) {
            System.out.println(myAgent.getLocalName() + ": Accepting proposal from " + bestPropose.getSender().getLocalName());
            accept = bestPropose.createReply();
            accept.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
            acceptances.addElement(accept);
        } else {
            System.out.println(myAgent.getLocalName() + ": No valid proposals received.");
        }
    }

    @Override
    protected void handleInform(ACLMessage inform) {
        // Step 2: The machine finished the work!
        System.out.println(myAgent.getLocalName() + ": Received completion notice from " + inform.getSender().getLocalName());

        // Cast the generic Agent to PartAgent and trigger the queue update
        if (myAgent instanceof PartAgent) {
            ((PartAgent) myAgent).markServiceComplete();
        }
    }
}