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

            if(spreadConn.getPrivateGroup().toString().equals(msg)){
                System.out.println("Same sender... Ignoring");
            }
            else {
                MsgData obj = (MsgData) spreadMessage.getObject();

                if (obj.msgType == MsgType.SENTCONFIG) {
                    System.out.println("GOT SERVER DATA FROM -> " + msg);
                    SpreadGroup group = spreadMessage.getSender();

                    if (serverRepo.containsKey(group))
                        serverRepo.replace(group, obj);
                    else
                        serverRepo.put(group, obj);

                    for(StreamObserver<Resposta> client : clientRepo.keySet()){

                        if( clientRepo.get(client).equals(spreadConn.getPrivateGroup())
                                &&  !serverRepo.containsKey(clientRepo.get(client)) )
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
        System.out.println("Recieved membership");

        if(spreadMessage.isMembership()) {
            MembershipInfo info = spreadMessage.getMembershipInfo();

            if(info.isCausedByJoin() && CheckSameSender(info.getJoined())){

                System.out.println("Added " + info.getJoined() + "to repo");
                serverRepo.put(info.getJoined(), new MsgData());
            }
            else if(info.isCausedByLeave() && CheckSameSender(info.getLeft())){
                System.out.println("Removed " + info.getLeft() + "to repo");
                serverRepo.remove(info.getLeft());

                resetClientUsingDisconnectedServer(info);
            }

            else if(info.isCausedByDisconnect() && CheckSameSender(info.getDisconnected())){

                System.out.println("Removed " + info.getDisconnected() + "to repo");
                serverRepo.remove(info.getDisconnected());

                resetClientUsingDisconnectedServer(info);

                /**
                 * ver qual cliente usa este servidor e
                 * mudar o spreadgroup para o base
                 */

            }else{
                ArrayList<SpreadGroup> arr = new ArrayList<>(Arrays.asList(info.getMembers()));

                for(SpreadGroup sg : arr){
                    if(! (sg.equals(spreadConn.getPrivateGroup())))
                        serverRepo.put(sg, new MsgData());
                }
                if(arr.size() > 1)
                    this.sendSpreadmsgOBJ(spreadMessage.getSender(), MsgType.REQCONFIG);
            }
        }

        System.out.println("REPOSITORIO ATUAL : ");
        for(SpreadGroup sg : serverRepo.keySet())
            System.out.println(sg);

        clearDisconnectedClients(spreadMessage.getMembershipInfo());

        PrintMessages.printMembershipInfo(spreadMessage.getMembershipInfo());
    }

    public void resetClientUsingDisconnectedServer(MembershipInfo info){
        for(StreamObserver<Resposta> client : clientRepo.keySet()){
            if( clientRepo.get(client).equals(info.getDisconnected()) )
                configService.getRandomServer(client);
        }
    }

    public void clearDisconnectedClients(MembershipInfo info){
        for(StreamObserver<Resposta> client : clientRepo.keySet() ){
            if( clientRepo.get(client).equals(info.getDisconnected()) )
            clientRepo.remove(client);
        }
    }


    private boolean CheckSameSender(SpreadGroup group ){
        System.out.println(group.toString());
        return ! spreadConn.getPrivateGroup().toString().equals(group.toString());
    }

    public void sendSpreadmsgOBJ(SpreadGroup group, MsgType msgType) {
        try {
            SpreadMessage msg = new SpreadMessage();
            msg.setSafe();

            msg.addGroup(group);
            msg.setObject(new MsgData(msgType, "key", "value"));

            spreadConn.multicast(msg);

        } catch (SpreadException e) {
            System.err.println("Error on MessageListener.sendSpreadmsgOBJ");
        }
    }
}
