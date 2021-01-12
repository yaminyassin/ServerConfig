import io.grpc.ServerBuilder;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Scanner;

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

        } catch(SpreadException e)  {
            System.err.println("Error Connecting to Daemon.");
            System.exit(1);
        }
        catch(UnknownHostException e) {
            System.err.println("Can't Find Daemon, Unkown Host " + this.spreadIP);
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
            System.err.println("Failed Joining SpreadGroup " + name);
        }
    }


    public void shutdownServers(){
        try {
            Scanner sc = new Scanner(System.in);
            sc.nextLine();

            grcpServer.awaitTermination();
            grcpServer.shutdown();

            spreadConn.remove(msgHandling);
            spreadConn.disconnect();
        } catch (SpreadException | InterruptedException e) {
            System.err.println("Error on Spread Shutdown \n");
        }
        System.exit(0);
    }


    public static void main(String[] args) {
        Server server = new Server(args);
    }
}