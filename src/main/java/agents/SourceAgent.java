package agents;

import jade.core.Agent;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class SourceAgent extends Agent {

    private HttpServer server;
    private int partCounter = 1;
    private int startRegister;
    private String serviceType;

    @Override
    protected void setup() {
        Object[] args = getArguments();

        // --- PROPERLY PARSE ALL 4 ARGUMENTS FROM MAIN.JAVA ---
        serviceType = (String) args[0];                     // e.g., "source_station_1"
        int port = Integer.parseInt((String) args[1]);      // e.g., 8001
        String myLocationX = (String) args[2];              // e.g., "55"
        startRegister = Integer.parseInt((String) args[3]); // e.g., 17

        System.out.println(getLocalName() + " ready at X:" + myLocationX + " wired to Modbus Reg:" + startRegister);

        // --- 1. DF REGISTRATION ---
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType(serviceType); // FIX: Use serviceType instead of the null sourceName
        sd.setName(getLocalName() + "_service");
        dfd.addServices(sd);

        try { DFService.register(this, dfd); } catch (FIPAException fe) { fe.printStackTrace(); }

        // --- 2. HTTP SERVER LISTENER ---
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/spawn", new SpawnHandler());
            server.setExecutor(null);
            server.start();
            System.out.println(getLocalName() + " is listening for AI commands on http://localhost:" + port + "/spawn");
        } catch (IOException e) {
            System.err.println(getLocalName() + " failed to start HTTP server.");
            e.printStackTrace();
        }

        // --- 3. CONFIG LISTENER (For the Crane) ---
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
    }

    @Override
    protected void takeDown() {
        try { DFService.deregister(this); } catch (FIPAException fe) { fe.printStackTrace(); }
        if (server != null) {
            server.stop(0);
        }
        System.out.println("Source Agent " + getLocalName() + " shutting down.");
    }

    // --- THE API ENDPOINT LOGIC ---
    private class SpawnHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                InputStream is = exchange.getRequestBody();
                String body = new String(is.readAllBytes()).trim();

                String response;
                int statusCode = 200;

                try {
                    String uniqueID = String.valueOf(System.currentTimeMillis()).substring(8);
                    String agentName = "Part_" + uniqueID;

                    ContainerController cc = getContainerController();
                    AgentController part = cc.createNewAgent(agentName, "agents.PartAgent", new Object[]{body});
                    part.start();

                    // FIX: Use serviceType here as well
                    response = "SUCCESS: Spawned " + agentName + " of type " + body + " from " + serviceType;
                    System.out.println("\n[" + getLocalName() + "] API TRIGGERED! Injecting " + agentName + " (" + body + ") into the JADE platform.");
                } catch (Exception e) {
                    response = "ERROR: Failed to spawn part - " + e.getMessage();
                    statusCode = 500;
                    e.printStackTrace();
                }

                exchange.sendResponseHeaders(statusCode, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }
}