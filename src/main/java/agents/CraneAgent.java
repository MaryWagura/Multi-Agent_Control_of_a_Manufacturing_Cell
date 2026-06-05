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

// Modbus Imports
import com.ghgande.j2mod.modbus.net.TCPMasterConnection;
import com.ghgande.j2mod.modbus.io.ModbusTCPTransaction;
import com.ghgande.j2mod.modbus.msg.WriteSingleRegisterRequest;
import com.ghgande.j2mod.modbus.procimg.SimpleRegister;
import java.net.InetAddress;

public class CraneAgent extends Agent {

    private TCPMasterConnection modbusConnection;

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
                // Parse the "PickupLocation,DropoffLocation" string from the Part
                String[] route = cfp.getContent().split(",");
                String pickup = route[0];
                String dropoff = route[1];

                System.out.println(getLocalName() + ": Proposal accepted! Executing pick-and-place from " + pickup + " to " + dropoff);

                ACLMessage inform = accept.createReply();
                inform.setPerformative(ACLMessage.INFORM);
                inform.setContent("Arrived at " + dropoff);

                // Convert the string destinations to X coordinates
                int pickupX = getCoordinateForDestination(pickup);
                int dropoffX = getCoordinateForDestination(dropoff);

                // Run the physical macro
                myAgent.addBehaviour(new CranePickAndPlaceBehaviour(myAgent, inform, pickupX, dropoffX));

                return null;
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
    // Moved to the main class scope so the setup() method can see it
    private int getCoordinateForDestination(String destination) {
        switch (destination) {
            case "source_station_1":
                return 55;
            case "source_station_2":
                return 158;
            case "processing_station_1":
                return 450;
            case "processing_station_2":
                return 650;
            case "sink_station":
                return 945;
            default:
                return 0; // Default safe position
        }
    }

    // --- MODBUS WRITER HELPER ---
    // Moved to main class scope so any behavior can use it cleanly
    private void writeModbus(int register, int value) throws Exception {
        WriteSingleRegisterRequest request = new WriteSingleRegisterRequest(register, new SimpleRegister(value));
        request.setUnitID(1);
        ModbusTCPTransaction transaction = new ModbusTCPTransaction(modbusConnection);
        transaction.setRequest(request);
        transaction.execute();
    }

    // --- NEW FULL PHYSICAL MACRO BEHAVIOUR ---
// --- NEW FULL PHYSICAL MACRO BEHAVIOUR ---
    private class CranePickAndPlaceBehaviour extends jade.core.behaviours.OneShotBehaviour {
        private ACLMessage replyMessage;
        private int pickupX, dropoffX;

        public CranePickAndPlaceBehaviour(Agent a, ACLMessage reply, int pickupX, int dropoffX) {
            super(a);
            this.replyMessage = reply;
            this.pickupX = pickupX;
            this.dropoffX = dropoffX;
        }

        @Override
        public void action() {
            new Thread(() -> {
                try {
                    // --- THE FIX: CRITICAL SAFETY CLEARANCE ---
                    System.out.println(getLocalName() + ": Raising to safe travel height.");
                    writeModbus(2, 200); // Raise Y to safe height FIRST
                    Thread.sleep(1000);

                    // --- PICKUP SEQUENCE ---
                    System.out.println(getLocalName() + ": Moving to pickup X:" + pickupX);
                    writeModbus(1, pickupX); // Move X to Source
                    Thread.sleep(2000); // Wait for travel

                    writeModbus(2, 82); // Lower Y
                    Thread.sleep(1000);

                    writeModbus(3, 1); // Vacuum ON (Grip)
                    Thread.sleep(500);

                    writeModbus(2, 200); // Raise Y (Travel height)
                    Thread.sleep(1000);

                    // --- DROPOFF SEQUENCE ---
                    System.out.println(getLocalName() + ": Part secured. Moving to dropoff X:" + dropoffX);
                    writeModbus(1, dropoffX); // Move X to Destination
                    Thread.sleep(2500); // Wait for travel

                    writeModbus(2, 82); // Lower Y
                    Thread.sleep(1000);

                    writeModbus(3, 0); // Vacuum OFF (Release)
                    Thread.sleep(500);

                    writeModbus(2, 200); // Raise Y back to safe height
                    Thread.sleep(1000);

                    // Physical movement complete, notify the Part!
                    myAgent.send(replyMessage);
                } catch (Exception e) {
                    System.err.println("Crane macro failed.");
                    e.printStackTrace();
                }
            }).start();
        }
    }}