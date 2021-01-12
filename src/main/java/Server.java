import io.grpc.ServerBuilder;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;

import io.grpc.stub.StreamObserver;
import rpcstubs.Resposta;
import spread.*;


public class Server{

    private HashMap<SpreadGroup, MsgData> ServerRepo = new HashMap<>();
    private HashMap<StreamObserver<Resposta>, SpreadGroup> clientRepo = new HashMap<>();

    private final int grcpPort = 5050;
    private ConfigService configService;
    private io.grpc.Server grcpServer;

    //vars do spread
    private String spreadIP = "35.246.58.5";
    private SpreadConnection spreadConn;
    private MessageListener msgHandling;

    public Server(String[] args){
        if(args.length > 0){
            this.spreadIP = args[0];
        }
        this.startServers();
        this.shutdownServers();

    }

    private void startServers(){
        try  {
            String spreadName = "config";
            int spreadPort = 4803;
            String configGroup = "Config";

            spreadConn= new SpreadConnection();
            spreadConn.connect(
                    InetAddress.getByName(this.spreadIP),
                    spreadPort, spreadName,
                    false, true);

            this.configService = new ConfigService(this.ServerRepo, this.clientRepo, this.spreadConn);
            this.grcpServer = ServerBuilder
                    .forPort(grcpPort)
                    .addService(this.configService)
                    .build();

            this.grcpServer.start();

            msgHandling = new MessageListener(this.configService);
            spreadConn.add(msgHandling);


            joingSpreadGroup(configGroup);
        }
        catch(SpreadException e)  {
            System.err.println("There was an error connecting to the daemon.");
            e.printStackTrace();
            System.exit(1);
        }
        catch(UnknownHostException e) {
            System.err.println("Can't find the daemon " + this.spreadIP);
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Can't Start Grcp Server " + this.grcpPort);
            System.exit(1);
        }
    }


    private void joingSpreadGroup(String name){
        SpreadGroup group = new SpreadGroup();

        try {
            group.join(spreadConn, name);

        } catch (SpreadException e) {
            e.printStackTrace();
            System.err.println("Failed to join Group, " + name);
        }
    }


    public void shutdownServers(){
        // shutdown and quit
        try {

            grcpServer.awaitTermination();
            grcpServer.shutdown();

            spreadConn.remove(msgHandling);
            spreadConn.disconnect();
        } catch (SpreadException | InterruptedException e) {
            System.err.println("error disconnecting spread server ");
            e.printStackTrace();
        }
        System.exit(0);
    }



    public static void main(String[] args) {
        Server server = new Server(args);


    }
}