package agents;

import jade.core.Agent;
import jade.core.AID;
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

public class ProcessAgent extends Agent {

    private TCPMasterConnection modbusConnection;
    private int startRegister; // The Modbus wire address to trigger this specific machine
    private String serviceType;

    @Override
    protected void setup() {
        // --- 1. CONFIGURATION ---
        Object[] args = getArguments();
        serviceType = (args != null && args.length > 0) ? (String) args[0] : "processing_station_1";

        // Map the correct Modbus wire address based on the agent's identity
        // Address 4 -> Wire 3. Address 5 -> Wire 4.
        if (serviceType.equals("processing_station_1")) {
            startRegister = 4;
        } else if (serviceType.equals("processing_station_2")) {
            startRegister = 5;
        }

        System.out.println("Process Agent " + getLocalName() + " starting up. Target Register: " + startRegister);

        // --- 2. MODBUS CONNECTION ---
        try {
            InetAddress address = InetAddress.getByName("127.0.0.1");
            modbusConnection = new TCPMasterConnection(address);
            modbusConnection.setPort(502); // Make sure your simulation is running with sudo!
            modbusConnection.connect();
            System.out.println(getLocalName() + " connected to Modbus simulation.");
        } catch (Exception e) {
            System.err.println(getLocalName() + " failed to connect to Modbus.");
            e.printStackTrace();
        }

        // --- 3. DF REGISTRATION ---
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType(serviceType);
        sd.setName(getLocalName() + "_service");
        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        // --- 4. CONTRACT NET RESPONDER ---
        MessageTemplate template = MessageTemplate.MatchPerformative(ACLMessage.CFP);
        addBehaviour(new ContractNetResponder(this, template) {

            @Override
            protected ACLMessage handleCfp(ACLMessage cfp) throws NotUnderstoodException, RefuseException {
                // We are idle, so we agree to do the job
                ACLMessage propose = cfp.createReply();
                propose.setPerformative(ACLMessage.PROPOSE);
                propose.setContent("10");
                return propose;
            }

            @Override
            protected ACLMessage handleAcceptProposal(ACLMessage cfp, ACLMessage propose, ACLMessage accept) throws FailureException {
                System.out.println(getLocalName() + ": Proposal accepted! Starting physical work for " + accept.getSender().getLocalName());

                // Prepare the completion message, but DO NOT send it yet.
                ACLMessage inform = accept.createReply();
                inform.setPerformative(ACLMessage.INFORM);
                inform.setContent("Process completed");

                // Launch a separate behavior to talk to the hardware
                myAgent.addBehaviour(new MachineWorkerBehaviour(myAgent, 3000, inform));

                // Return null to tell JADE protocol: "I am handling the reply asynchronously."
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
        System.out.println("Process Agent " + getAID().getName() + " shutting down.");
    }

    // --- THE HARDWARE EXECUTION ENGINE ---
    private class MachineWorkerBehaviour extends WakerBehaviour {
        private ACLMessage replyMessage;

        // WakerBehaviour executes exactly once after the specified timeout (in milliseconds)
        public MachineWorkerBehaviour(Agent a, long timeout, ACLMessage reply) {
            super(a, timeout);
            this.replyMessage = reply;

            // Fire the Modbus start command immediately when this behavior is created
            triggerMachine(1);
        }

        @Override
        protected void onWake() {
            // This runs after the timeout (e.g., 3 seconds later)
            // Reset the machine register back to 0
            triggerMachine(0);

            // Now formally notify the Part that we are done
            System.out.println(getLocalName() + ": Physical work finished. Notifying " + replyMessage.getAllReceiver().next());
            myAgent.send(replyMessage);
        }

        private void triggerMachine(int command) {
            try {
                WriteSingleRegisterRequest request = new WriteSingleRegisterRequest(
                        startRegister,
                        new SimpleRegister(command) // Write 1 to start, 0 to reset
                );
                request.setUnitID(1); // Crucial for j2mod routing

                ModbusTCPTransaction transaction = new ModbusTCPTransaction(modbusConnection);
                transaction.setRequest(request);
                transaction.execute();
            } catch (Exception e) {
                System.err.println(getLocalName() + " failed to send Modbus command.");
            }
        }
    }
}