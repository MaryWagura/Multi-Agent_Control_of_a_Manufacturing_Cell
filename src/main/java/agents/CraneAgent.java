package agents;

import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import com.ghgande.j2mod.modbus.net.TCPMasterConnection;
import com.ghgande.j2mod.modbus.msg.WriteSingleRegisterRequest;
import com.ghgande.j2mod.modbus.msg.ModbusResponse;
import com.ghgande.j2mod.modbus.procimg.SimpleRegister;
import java.net.InetAddress;
import com.ghgande.j2mod.modbus.io.ModbusTCPTransaction;

public class CraneAgent extends Agent {

    private TCPMasterConnection modbusConnection;
    private static final String SIMULATION_IP = "127.0.0.1";
    //502 is the default port set in windows, when using my linux, had to use sudo to force the port to open
    private static final int MODBUS_PORT = 502; // Standard Modbus TCP port

    @Override
    protected void setup() {
        System.out.println("Crane Agent " + getAID().getName() + " is starting up...");

        try {
            // 1. Establish the Modbus Connection
            InetAddress address = InetAddress.getByName(SIMULATION_IP);
            modbusConnection = new TCPMasterConnection(address);
            modbusConnection.setPort(MODBUS_PORT);
            modbusConnection.connect();
            System.out.println("Crane successfully connected to the simulation over Modbus TCP.");

            // 2. Add a temporary test behavior to move the crane
            addBehaviour(new TestMovementBehaviour());

        } catch (Exception e) {
            System.err.println("Crane failed to connect to Modbus simulation. Is the simulation running?");
            e.printStackTrace();
            doDelete(); // Kill the agent if it can't connect to hardware
        }
    }

    @Override
    protected void takeDown() {
        // Always clean up hardware connections!
        if (modbusConnection != null && modbusConnection.isConnected()) {
            modbusConnection.close();
            System.out.println("Modbus connection closed cleanly.");
        }
        System.out.println("Crane Agent shutting down.");
    }


    // --- Temporary Test Behavior ---
    private class TestMovementBehaviour extends OneShotBehaviour {
        @Override
        public void action() {
            try {
                // Modbus wire protocol is often 0-based. If the doc says [1], the wire address is 0.
                int xAxisRegister = 1;
                int targetPosition = 450;

                WriteSingleRegisterRequest request = new WriteSingleRegisterRequest(
                        xAxisRegister,
                        new SimpleRegister(targetPosition)
                );

                // THE FIX: Explicitly set the Unit ID to 1 so the server doesn't reject it as a broadcast
                request.setUnitID(1);

                ModbusTCPTransaction transaction = new ModbusTCPTransaction(modbusConnection);
                transaction.setRequest(request);
                transaction.execute();

                ModbusResponse response = transaction.getResponse();

                if (response != null) {
                    System.out.println("Success! Crane commanded to move to Process1 (X: " + targetPosition + ")");
                }

            } catch (Exception e) {
                System.err.println("Failed to execute Modbus transaction.");
                e.printStackTrace();
            }
        }
    }
}