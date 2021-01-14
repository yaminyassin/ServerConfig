package server;

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
            sendServer(responseObserver, picked);
        }
        else{
            /* se nao ha servidores disponiveis, insere cliente na lista de clientes */
            addToWaitlist(responseObserver);
        }
    }

    public void sendServer(StreamObserver<Resposta> client, SpreadGroup group){

        if(clientRepo.containsKey(client))
            clientRepo.replace(client, group);
        else
            clientRepo.put(client, group);

        MsgData msg = serverRepo.get(group);

        ServerInfo serverInfo= ServerInfo
                .newBuilder()
                .setIp(msg.key)
                .setPort(Integer.parseInt(msg.value)).build();

        Resposta resposta = Resposta
                .newBuilder()
                .setServerInfo(serverInfo).build();

        System.out.println("Sent ConfigServer.Server Details ");
        System.out.println("IP: " + msg.key);
        System.out.println("PORT: " + msg.value + "\n");

        client.onNext(resposta);
    }

    public void addToWaitlist(StreamObserver<Resposta> client){
        clientRepo.put(client, spreadConn.getPrivateGroup());

        Empty empty = rpcstubs.Empty.newBuilder().build();

        Resposta resposta = Resposta
                .newBuilder()
                .setEmpty(empty)
                .build();

        System.out.println("No ConfigServer.Server Availabe, Client on Waiting List \n");

        client.onNext(resposta);
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