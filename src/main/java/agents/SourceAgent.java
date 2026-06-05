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
    private String sourceName;
    private int partCounter = 1; // Keeps track of part IDs

    @Override
    protected void setup() {
        Object[] args = getArguments();
        sourceName = (args != null && args.length > 0) ? (String) args[0] : "source_station_1";

        // Use the second argument as the port number, default to 8001 if missing
        int port = (args != null && args.length > 1) ? Integer.parseInt((String) args[1]) : 8001;

        System.out.println("Source Agent " + getLocalName() + " is ready.");

        // --- 1. DF REGISTRATION ---
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType(sourceName);
        sd.setName(getLocalName() + "_service");
        dfd.addServices(sd);

        try { DFService.register(this, dfd); } catch (FIPAException fe) { fe.printStackTrace(); }

        // --- 2. HTTP SERVER LISTENER ---
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/spawn", new SpawnHandler());
            server.setExecutor(null); // Use the default executor
            server.start();
            System.out.println(getLocalName() + " is listening for AI commands on http://localhost:" + port + "/spawn");
        } catch (IOException e) {
            System.err.println(getLocalName() + " failed to start HTTP server.");
            e.printStackTrace();
        }
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
                    // THE FIX: Guarantee a completely unique name using the timestamp
                    String uniqueID = String.valueOf(System.currentTimeMillis()).substring(8);
                    String agentName = "Part_" + uniqueID;

                    ContainerController cc = getContainerController();
                    AgentController part = cc.createNewAgent(agentName, "agents.PartAgent", new Object[]{body});
                    part.start();

                    response = "SUCCESS: Spawned " + agentName + " of type " + body + " from " + sourceName;
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