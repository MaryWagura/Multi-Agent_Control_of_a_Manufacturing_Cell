package agents;

import jade.core.Agent;
import jade.lang.acl.ACLMessage;
import jade.proto.ContractNetInitiator;
import java.util.Vector;

/**
 * This external behavior handles the FIPA Contract Net Protocol for the Part.
 * By keeping this separate from PartAgent.java, we prevent the main agent from
 * becoming a bloated "God Class" and keep network logic isolated.
 */
public class PartNegotiator extends ContractNetInitiator {

    // Constructor: Passes the agent instance and the initial CFP message to the JADE protocol
    public PartNegotiator(Agent a, ACLMessage cfp) {
        super(a, cfp);
    }

    /**
     * Triggered when the machines reply to the initial Call For Proposal (CFP).
     * @param responses A vector of all messages received back (Proposals, Refusals, etc.)
     * @param acceptances A vector where we put our replies (Accept or Reject)
     */
    @Override
    protected void handleAllResponses(Vector responses, Vector acceptances) {

        ACLMessage bestPropose = null;
        ACLMessage accept = null;

        // Loop through all responses from the machines
        for (int i = 0; i < responses.size(); i++) {
            ACLMessage msg = (ACLMessage) responses.get(i);

            // Check if the machine actually proposed to do the work
            if (msg.getPerformative() == ACLMessage.PROPOSE) {
                // In a highly complex system, you would compare the "cost" content
                // of multiple proposals here. Since we only have one machine per service,
                // we just grab the first valid proposal we see.
                bestPropose = msg;
                break;
            }
        }

        // If a machine agreed to do the work, send them the formal ACCEPT message
        if (bestPropose != null) {
            System.out.println(myAgent.getLocalName() + ": Accepting proposal from " + bestPropose.getSender().getLocalName());

            accept = bestPropose.createReply(); // Creates a reply specifically to that machine
            accept.setPerformative(ACLMessage.ACCEPT_PROPOSAL); // Set the formal FIPA performative
            acceptances.addElement(accept); // Add it to the outgoing queue
        } else {
            System.out.println(myAgent.getLocalName() + ": No valid proposals received.");
        }
    }

    /**
     * Triggered when the machine finishes the physical work and sends an INFORM message.
     * @param inform The completion message from the machine
     */
    @Override
    protected void handleInform(ACLMessage inform) {
        System.out.println(myAgent.getLocalName() + ": Received completion notice from " + inform.getSender().getLocalName());

        // Tell the PartAgent the current negotiation (Crane OR Machine) is done
        if (myAgent instanceof PartAgent) {
            ((PartAgent) myAgent).negotiationFinished();
        }
    }
}