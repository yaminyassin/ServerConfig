import io.grpc.stub.StreamObserver;
import rpcstubs.Empty;
import rpcstubs.*;
import spread.SpreadConnection;
import spread.SpreadGroup;

import java.util.*;


public class ConfigService extends ConfigServiceGrpc.ConfigServiceImplBase {

    private HashMap<StreamObserver<Resposta>, SpreadGroup> clientRepo;
    private HashMap<SpreadGroup, MsgData> serverRepo;
    private SpreadConnection spreadConn;

    private final Random randomPicker = new Random();

    public ConfigService(
            HashMap<SpreadGroup, MsgData> serverRepo,
            HashMap<StreamObserver<Resposta>, SpreadGroup> clientRepo,
            SpreadConnection spreadConn)
    {
        this.clientRepo = clientRepo;
        this.serverRepo = serverRepo;
        this.spreadConn = spreadConn;
    }

    @Override
    public void getClusterInfo(Empty request, StreamObserver<Resposta> responseObserver) {

        getRandomServer(responseObserver);
    }

    /*
        Este metodo procura dos servidores disponíveis e atribui um aleatorio ao
        cliente.
        Se nao existem servidores, insere cliente no clientRepo e envia uma
        mensagem vazia ao cliente (esta mensagem serve para indicar ao cliente
        que foi inserido numa lista de espera)
     */
    public void getRandomServer(StreamObserver<Resposta> responseObserver){

        ArrayList<SpreadGroup> arr = new ArrayList<>(serverRepo.keySet());

        if(arr.size() > 0){ //se houver servidores disponíveis, escolhe um aleatorio

            SpreadGroup picked = arr.get(randomPicker.nextInt(serverRepo.size()));

            if(clientRepo.containsKey(responseObserver))
                clientRepo.replace(responseObserver, picked);
            else
                clientRepo.put(responseObserver, picked);

            MsgData msg = serverRepo.get(picked);

            ServerInfo serverInfo= ServerInfo
                    .newBuilder()
                    .setIp(msg.key)
                    .setPort(Integer.parseInt(msg.value)).build();

            Resposta resposta = Resposta
                    .newBuilder()
                    .setServerInfo(serverInfo).build();

            System.out.println("Sent Server Details ");
            System.out.println("IP: " + msg.key);
            System.out.println("PORT: " + msg.value + "\n");

            responseObserver.onNext(resposta);
        }
        else{
            /*
            se nao ha servidores disponiveis,
             insere cliente na lista de clientes
             */

            clientRepo.put(responseObserver, spreadConn.getPrivateGroup());

            Empty empty = rpcstubs.Empty.newBuilder().build();

            Resposta resposta = Resposta
                    .newBuilder()
                    .setEmpty(empty)
                    .build();

            System.out.println("No Server Availabe, Client on Waiting List \n");

            responseObserver.onNext(resposta);
        }
    }

    public HashMap<SpreadGroup, MsgData> getServerRepo() {
        return serverRepo;
    }

    public HashMap<StreamObserver<Resposta>, SpreadGroup> getClientRepo() {
        return clientRepo;
    }

    public SpreadConnection getSpreadConn() {
        return spreadConn;
    }

}