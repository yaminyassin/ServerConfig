package ConfigServer;

import io.grpc.stub.StreamObserver;
import rpcstubs.Resposta;
import spread.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;


public class MessageListener implements AdvancedMessageListener {

    private HashMap<StreamObserver<Resposta>, SpreadGroup> clientRepo;
    private HashMap<SpreadGroup, MsgData> serverRepo;

    private SpreadConnection spreadConn;
    private ConfigService configService;

    public MessageListener(ConfigService configService) {
        this.configService = configService;
        this.spreadConn = configService.getSpreadConn();
        this.serverRepo = configService.getServerRepo();
        this.clientRepo = configService.getClientRepo();
    }

    @Override
    public void regularMessageReceived(SpreadMessage spreadMessage) {
        try {
            String msg = spreadMessage.getSender().toString();

            if( ! spreadConn.getPrivateGroup().toString().equals(msg) ){
                MsgData obj = (MsgData) spreadMessage.getObject();

                if (obj.msgType == MsgType.CONFIG_RES) {
                    System.out.println("Recieved CONFIG_RES From: " + msg + "\n" );
                    SpreadGroup group = spreadMessage.getSender();

                    if (serverRepo.containsKey(group))
                        serverRepo.replace(group, obj);
                    else
                        serverRepo.put(group, obj);

                    /*
                    verificar todos clientes que nao tem servidor ativo
                    e enviar um novo servidor.
                    */
                    for(StreamObserver<Resposta> client : clientRepo.keySet()){
                        if( clientRepo.get(client).equals(spreadConn.getPrivateGroup())
                        && ! serverRepo.containsKey(clientRepo.get(client)) )
                            configService.getRandomServer(client);
                    }
                }
            }
        } catch (SpreadException e) {
            e.printStackTrace();
        }
    }



    @Override
    public void membershipMessageReceived(SpreadMessage spreadMessage) {

        MembershipInfo info = spreadMessage.getMembershipInfo();

        if(info.isCausedByJoin() && CheckSameSender(info.getJoined())){

            System.out.println("Added " + info.getJoined() + " to Repo");
            serverRepo.put(info.getJoined(), new MsgData());
        }
        else if(info.isCausedByLeave() && CheckSameSender(info.getLeft())){
            System.out.println("Removed " + info.getLeft() + " From Repo");
            serverRepo.remove(info.getLeft());

            removeInactiveClients(info);
        }

        else if(info.isCausedByDisconnect() && CheckSameSender(info.getDisconnected())){

            System.out.println("Removed " + info.getDisconnected() + " From Repo");
            serverRepo.remove(info.getDisconnected());

            removeInactiveClients(info);

        }else{
            ArrayList<SpreadGroup> arr = new ArrayList<>(Arrays.asList(info.getMembers()));

            for(SpreadGroup sg : arr){
                if(! (sg.equals(spreadConn.getPrivateGroup()))){
                    serverRepo.put(sg, new MsgData());
                    this.sendSpreadmsgOBJ(sg, MsgType.CONFIG_REQ);
                }
            }


        }


        System.out.println("Servidores Atuais : ");
        for(SpreadGroup sg : serverRepo.keySet())
            System.out.println(sg);

    }

    /*
    Este metodo remove Clientes que nao estao ativos,
    pois sempre que o cliente acaba de escrever ou ler
    ele fecha a ligacao e pede um novo server ao configServer
     */
    public void removeInactiveClients(MembershipInfo info){
        for(StreamObserver<Resposta> client : clientRepo.keySet()){
            if( clientRepo.get(client).equals(info.getDisconnected()) ) {
                clientRepo.remove(client);
            }
        }
        System.out.println("Remaining Clients: ");
        for(StreamObserver<Resposta> client : clientRepo.keySet())
            System.out.println(client);
    }


    private boolean CheckSameSender(SpreadGroup group ){
        System.out.println(group.toString());
        return ! spreadConn.getPrivateGroup().toString().equals(group.toString());
    }

    public void sendSpreadmsgOBJ(SpreadGroup group, MsgType msgType) {
        try {
            MsgData data = new MsgData();
            data.setMsgType(msgType);

            SpreadMessage msg = new SpreadMessage();

            msg.setSafe();
            msg.addGroup(group);
            msg.setObject(data);

            spreadConn.multicast(msg);

        } catch (SpreadException e) {
            System.err.println("Error on ConfigServer.MessageListener.sendSpreadmsgOBJ");
        }
    }
}
