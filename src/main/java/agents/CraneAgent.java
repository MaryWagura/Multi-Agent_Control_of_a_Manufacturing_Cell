package agents;

import com.ghgande.j2mod.modbus.msg.ReadMultipleRegistersRequest;
import com.ghgande.j2mod.modbus.msg.ReadMultipleRegistersResponse;
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
    private int currentCraneX = 0; // Track the crane positions

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
                String[] route = cfp.getContent().split(",");
                String pickup = route[0];
                String dropoff = route[1];

                System.out.println(getLocalName() + ": Validating route config from " + pickup + " to " + dropoff);

                // Pass the ACCEPT message into the thread so the thread can respond after checking sensors
                myAgent.addBehaviour(new CranePickAndPlaceBehaviour(myAgent, accept, pickup, dropoff));
                return null; // Return null so we don't automatically send the INFORM message yet
            }
        });
    }

    // --- NEW: MODBUS READ HELPER ---
    private int readModbus(int register) throws Exception {
        ReadMultipleRegistersRequest request = new ReadMultipleRegistersRequest(register, 1);
        request.setUnitID(1);
        ModbusTCPTransaction transaction = new ModbusTCPTransaction(modbusConnection);
        transaction.setRequest(request);
        transaction.execute();
        ReadMultipleRegistersResponse response = (ReadMultipleRegistersResponse) transaction.getResponse();
        return response.getRegisterValue(0);
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        if (modbusConnection != null && modbusConnection.isConnected()) {
            modbusConnection.close();
        }
        System.out.println("Crane Agent shutting down.");
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

    // --- NEW DYNAMIC PHYSICAL MACRO BEHAVIOUR ---

    private class CranePickAndPlaceBehaviour extends jade.core.behaviours.OneShotBehaviour {
        private ACLMessage acceptMessage; // Contains the ACCEPT message from the Part
        private String pickupLocationName, dropoffLocationName;
        private Agent agentRef;

        public CranePickAndPlaceBehaviour(Agent a, ACLMessage acceptMessage, String pickupName, String dropoffName) {
            super(a);
            this.agentRef = a;
            this.acceptMessage = acceptMessage;
            this.pickupLocationName = pickupName;
            this.dropoffLocationName = dropoffName;
        }

        // Returns String[] { "X_Coordinate", "Modbus_Register" }
        private String[] fetchModuleConfig(String serviceType) throws Exception {
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType(serviceType);
            template.addServices(sd);
            DFAgentDescription[] result = DFService.search(agentRef, template);

            if (result.length == 0) throw new Exception("Module " + serviceType + " not found!");

            ACLMessage req = new ACLMessage(ACLMessage.REQUEST);
            req.addReceiver(result[0].getName());
            req.setOntology("CONFIG_REQUEST"); // Updated ontology name
            req.setReplyWith("req" + System.currentTimeMillis());
            agentRef.send(req);

            ACLMessage reply = agentRef.blockingReceive(MessageTemplate.MatchInReplyTo(req.getReplyWith()), 5000);
            if (reply != null && reply.getPerformative() == ACLMessage.INFORM) {
                return reply.getContent().split(","); // Splits "450,4" into ["450", "4"]
            }
            throw new Exception("Failed to retrieve config from " + serviceType);
        }

        @Override
        public void action() {
            new Thread(() -> {
                try {
                    // 1. DYNAMICALLY FETCH PICKUP CONFIG
                    String[] pickupConfig = fetchModuleConfig(pickupLocationName);
                    int pickupX = Integer.parseInt(pickupConfig[0]);
                    int sensorReg = Integer.parseInt(pickupConfig[1]);

                    // 2. MODBUS SENSOR CHECK (Totally Decoupled!)
                    if (pickupLocationName.startsWith("source_station")) {
                        int isPartPresent = readModbus(sensorReg);
                        if (isPartPresent == 0) {
                            System.err.println("\n" + getLocalName() + ": ERROR! Sensor at Reg " + sensorReg + " is empty! Aborting.");

                            // Send FAILURE back to the Part
                            ACLMessage failure = acceptMessage.createReply();
                            failure.setPerformative(ACLMessage.FAILURE);
                            failure.setContent("no_part");
                            agentRef.send(failure);
                            return; // Kill the thread immediately to stop the crane
                        }
                    }

                    // 3. DYNAMICALLY FETCH DROPOFF CONFIG
                    String[] dropoffConfig = fetchModuleConfig(dropoffLocationName);
                    int dropoffX = Integer.parseInt(dropoffConfig[0]);

                    // 4. EXECUTE KINEMATICS MACRO
                    System.out.println(getLocalName() + ": Location data acquired. Raising to safe travel height.");
                    writeModbus(2, 200);
                    Thread.sleep(1500);

                    // Closed-loop: Read actual physical location
                    int actualStartX = readModbus(15);
                    int distToPickup = Math.abs(pickupX - actualStartX);
                    long sleepToPickup = (distToPickup * 10) + 1500;

                    System.out.println(getLocalName() + ": Moving from actual X:" + actualStartX + " to pickup X:" + pickupX + " (Waiting " + sleepToPickup + "ms)");
                    writeModbus(1, pickupX);
                    Thread.sleep(sleepToPickup);

                    writeModbus(2, 82);
                    Thread.sleep(1500);

                    writeModbus(3, 1);
                    Thread.sleep(500); // Grip

                    writeModbus(2, 200);
                    Thread.sleep(1500); // Raise

                    int distToDropoff = Math.abs(dropoffX - pickupX);
                    long sleepToDropoff = (distToDropoff * 10) + 1500;

                    System.out.println(getLocalName() + ": Part secured. Moving to dropoff X:" + dropoffX + " (Waiting " + sleepToDropoff + "ms)");
                    writeModbus(1, dropoffX);
                    Thread.sleep(sleepToDropoff);

                    writeModbus(2, 82);
                    Thread.sleep(1500);

                    writeModbus(3, 0);
                    Thread.sleep(500); // Release

                    writeModbus(2, 200);
                    Thread.sleep(1500);

                    // 5. SEND COMPLETION INFORM TO PART (Only after the physical dropoff is 100% finished)
                    ACLMessage inform = acceptMessage.createReply();
                    inform.setPerformative(ACLMessage.INFORM);
                    inform.setContent("Arrived at " + dropoffLocationName);
                    agentRef.send(inform);

                } catch (Exception e) {
                    System.err.println("Crane macro failed.");
                    e.printStackTrace();
                }
            }).start();
        }
    }
}