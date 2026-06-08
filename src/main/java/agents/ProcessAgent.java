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
import com.ghgande.j2mod.modbus.msg.ReadMultipleRegistersRequest;
import com.ghgande.j2mod.modbus.msg.ReadMultipleRegistersResponse;
import com.ghgande.j2mod.modbus.procimg.SimpleRegister;
import java.net.InetAddress;

public class ProcessAgent extends Agent {

    private TCPMasterConnection modbusConnection;
    private int startRegister; // Dynamically injected Modbus address
    private String serviceType;

    @Override
    protected void setup() {
        // --- 1. CONFIGURATION ---
        Object[] args = getArguments();
        serviceType = (String) args[0];
        String myLocationX = (String) args[1];

        //--- Read the Modbus wire address dynamically from JSON args ---
        startRegister = Integer.parseInt((String) args[2]);

        System.out.println(getLocalName() + " ready at X:" + myLocationX + " wired to Modbus Reg:" + startRegister);

        // --- 2. MODBUS CONNECTION ---
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

        // --- 3. DF REGISTRATION ---
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType(serviceType);
        sd.setName(getLocalName() + "_service");
        dfd.addServices(sd);

        try { DFService.register(this, dfd); } catch (FIPAException fe) { fe.printStackTrace(); }

        // --- 4. CONFIG LISTENER (For the Crane) ---
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

        // --- 5. CONTRACT NET RESPONDER ---
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
                System.out.println(getLocalName() + ": Proposal accepted! Starting physical work...");

                // Execute the physical work in a thread so we can monitor the breakdown button
                new Thread(() -> {
                    try {
                        // Start the machine animation using our dynamic register
                        writeModbus(startRegister, 1);

                        // Simulate work taking time
                        Thread.sleep(4000);

                        // --- FAULT TOLERANCE CHECK (Button 1 / Register 23) ---
                        // If you press Button 1 in the UI during these 4 seconds, this catches it
                        int isBroken = readModbus(23);
                        if (isBroken == 1) {
                            System.err.println(getLocalName() + ": CRITICAL HARDWARE FAILURE! Halting process.");
                            writeModbus(startRegister, 0); // Stop machine animation

                            // Send a FAILURE message to the Part so it triggers its re-route protocol!
                            ACLMessage failureMsg = accept.createReply();
                            failureMsg.setPerformative(ACLMessage.FAILURE);
                            failureMsg.setContent("hardware_breakdown");
                            myAgent.send(failureMsg);
                            return; // Exit the thread
                        }

                        // Normal completion if nothing broke
                        writeModbus(startRegister, 0); // Stop animation
                        System.out.println(getLocalName() + ": Physical work finished.");

                        ACLMessage inform = accept.createReply();
                        inform.setPerformative(ACLMessage.INFORM);
                        inform.setContent("Process completed");
                        myAgent.send(inform);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();

                return null; // Asynchronous reply
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

    // --- MODBUS HELPER METHODS ---

    private void writeModbus(int register, int command) throws Exception {
        WriteSingleRegisterRequest request = new WriteSingleRegisterRequest(register, new SimpleRegister(command));
        request.setUnitID(1);
        ModbusTCPTransaction transaction = new ModbusTCPTransaction(modbusConnection);
        transaction.setRequest(request);
        transaction.execute();
    }

    private int readModbus(int register) throws Exception {
        // Changed "Holding" to "Multiple" here
        ReadMultipleRegistersRequest request = new ReadMultipleRegistersRequest(register, 1);
        request.setUnitID(1);

        ModbusTCPTransaction transaction = new ModbusTCPTransaction(modbusConnection);
        transaction.setRequest(request);
        transaction.execute();

        // Changed "Holding" to "Multiple" here as well
        ReadMultipleRegistersResponse response = (ReadMultipleRegistersResponse) transaction.getResponse();
        return response.getRegister(0).getValue();
    }
}