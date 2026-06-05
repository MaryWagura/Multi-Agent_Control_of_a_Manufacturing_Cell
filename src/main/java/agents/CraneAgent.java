package agents;

import jade.core.Agent;
import jade.core.behaviours.WakerBehaviour;
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

// Modbus Imports
import com.ghgande.j2mod.modbus.net.TCPMasterConnection;
import com.ghgande.j2mod.modbus.io.ModbusTCPTransaction;
import com.ghgande.j2mod.modbus.msg.WriteSingleRegisterRequest;
import com.ghgande.j2mod.modbus.procimg.SimpleRegister;
import java.net.InetAddress;

public class CraneAgent extends Agent {

    private TCPMasterConnection modbusConnection;
    // Address 1 is the setX register from your documentation
    private static final int CRANE_X_REGISTER = 1;

    @Override
    protected void setup() {
        System.out.println("Crane Agent " + getLocalName() + " is starting up...");

        // --- 1. MODBUS CONNECTION ---
        try {
            InetAddress address = InetAddress.getByName("127.0.0.1");
            modbusConnection = new TCPMasterConnection(address);
            modbusConnection.setPort(502);
            modbusConnection.connect();
            System.out.println(getLocalName() + " connected to Modbus simulation.");
        } catch (Exception e) {
            System.err.println(getLocalName() + " failed to connect to Modbus.");
            e.printStackTrace();
        }

        // --- 2. DF REGISTRATION (The Taxi Stand) ---
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("transport_service");
        sd.setName(getLocalName() + "_service");
        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        // --- 3. CONTRACT NET RESPONDER ---
        MessageTemplate template = MessageTemplate.MatchPerformative(ACLMessage.CFP);
        addBehaviour(new ContractNetResponder(this, template) {

            @Override
            protected ACLMessage handleCfp(ACLMessage cfp) throws NotUnderstoodException, RefuseException {
                // The Part will send its destination in the CFP content
                String destination = cfp.getContent();
                System.out.println(getLocalName() + ": Received transport request to " + destination + " from " + cfp.getSender().getLocalName());

                ACLMessage propose = cfp.createReply();
                propose.setPerformative(ACLMessage.PROPOSE);
                propose.setContent("10"); // Standard cost
                return propose;
            }

            @Override
            protected ACLMessage handleAcceptProposal(ACLMessage cfp, ACLMessage propose, ACLMessage accept) throws FailureException {
                String destination = cfp.getContent();
                System.out.println(getLocalName() + ": Proposal accepted! Moving to " + destination);

                ACLMessage inform = accept.createReply();
                inform.setPerformative(ACLMessage.INFORM);
                inform.setContent("Arrived at " + destination);

                // Convert the string destination to an X coordinate
                int targetX = getCoordinateForDestination(destination);

                // Launch the async behavior to move the hardware
                myAgent.addBehaviour(new CraneMoveBehaviour(myAgent, 4000, inform, targetX));

                return null; // Handle async
            }
        });
    }

    @Override
    protected void takeDown() {
        try { DFService.deregister(this); } catch (FIPAException fe) { fe.printStackTrace(); }
        if (modbusConnection != null && modbusConnection.isConnected()) {
            modbusConnection.close();
        }
        System.out.println("Crane Agent shutting down.");
    }

    // --- HARDWARE COORDINATE MAPPER ---
    private int getCoordinateForDestination(String destination) {
        switch (destination) {
            case "source_station_1": return 55;
            case "source_station_2": return 158;
            case "processing_station_1": return 450;
            case "processing_station_2": return 650;
            case "sink_station": return 945;
            default: return 0; // Default safe position
        }
    }

    // --- ASYNC HARDWARE EXECUTION ---
    private class CraneMoveBehaviour extends WakerBehaviour {
        private ACLMessage replyMessage;
        private int targetX;

        public CraneMoveBehaviour(Agent a, long timeout, ACLMessage reply, int x) {
            super(a, timeout);
            this.replyMessage = reply;
            this.targetX = x;

            // Fire the Modbus movement command immediately
            moveCrane(targetX);
        }

        @Override
        protected void onWake() {
            // Once the timeout finishes (assuming travel time), notify the Part
            System.out.println(getLocalName() + ": Arrived at X:" + targetX + ". Notifying " + replyMessage.getAllReceiver().next());
            myAgent.send(replyMessage);
        }

        private void moveCrane(int target) {
            try {
                WriteSingleRegisterRequest request = new WriteSingleRegisterRequest(
                        CRANE_X_REGISTER,
                        new SimpleRegister(target)
                );
                request.setUnitID(1);

                ModbusTCPTransaction transaction = new ModbusTCPTransaction(modbusConnection);
                transaction.setRequest(request);
                transaction.execute();
            } catch (Exception e) {
                System.err.println(getLocalName() + " failed to move crane via Modbus.");
            }
        }
    }
}