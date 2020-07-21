package ds1;

import akka.actor.ActorRef;
import akka.actor.AbstractActor;
import akka.actor.ActorSystem;
import akka.actor.Props;
import ds1.DistributedSystemElc;
import ds1.DistributedSystemElc.*;

import ds1.DistributedSystemElc.StartMessage;
import ds1.DistributedSystemElc.clientReadRequest;
import ds1.DistributedSystemElc.clientReadResponse;
import ds1.DistributedSystemElc.clientwriteRequest;
import ds1.DistributedSystemElc.clientwriteResponse;

import scala.collection.Seq;
import scala.concurrent.duration.Duration;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;
import java.util.Set;
import java.util.*;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.ArrayList;
import java.lang.Thread;
import java.util.Collections;

import java.io.IOException;


public class Node extends AbstractActor {

    protected int id;                           // node ID
    protected List<ActorRef> participants;      // list of participant nodes
    protected List<ActorRef> Nodes;             // list of all nodes
    protected List<ActorRef> Clients;           //list of Clients
    protected ActorRef Coordinator;             // coordinator

    protected Set<coordinatorVoteReq> VoteReq;// for concurrent write req
    protected Set<clientwriteResponse> finalizedMsg;// finished voted massages

    public enum Decision {hold,abort,commit};     //store msg for vote based on seq number
    protected Map<Integer, Decision> DecSeq;
    protected Map<Integer,Map<Integer, Set<clientwriteResponse>>> LastOfUs;

    protected Map<Integer,Integer> EpochSeqPair;
    protected Map<Map<Integer,Integer>,clientwriteResponse> EpochSeqMassage;
    protected Map<Map<Integer,Integer>,Set<ActorRef>> EpochSeqVoters;

    //Important ones 
    protected Map<Integer, clientwriteResponse> workingMsg; //for saveing vote req and response
    protected Map<Integer, clientwriteResponse> finallizedMsg; //for saveing final result
    protected Set<ActorRef> voters;             // keep voters
    protected Map<Integer,Set<ActorRef>> SeqVoters;






    protected int Value;                        //Value of node
    protected int epoch;
    protected int SeqNumber;
    protected Boolean isManager = false;
    private Random rnd = new Random();

    public Node(int id,int Value,int epoch, int SeqNumber,Boolean isManager){
        this.Value = 1000;
        this.epoch = 0;
        this.SeqNumber = 0;
        this.VoteReq = new HashSet<>();
        this.finalizedMsg = new HashSet<>();

        this.workingMsg = new HashMap<>();
        this.finallizedMsg = new HashMap<>();

        this.DecSeq = new HashMap<>();
        this.SeqVoters = new HashMap<>();
        this.LastOfUs = new HashMap<>();

        this.EpochSeqPair = new HashMap<>();
        this.EpochSeqMassage = new HashMap<>();
        this.EpochSeqVoters = new HashMap<>();
        this.voters = new HashSet<>();



    }


    //general receive builder for both coordinator and participants
    @Override
    public Receive createReceive() {
        return receiveBuilder()
        .match(StartMessage.class, this::onStartMessage)
        .build();
    }


    // //general receive builder for both coordinator and participants
    // @Override
    // public Receive createReceive() {
    //     return receiveBuilder()
    //     .match(StartMessage.class, this::onStartMessage)
    //     .match(clientReadRequest.class, this::onClientReadReq)
    //     .match(clientwriteRequest.class, this::onClientWriteReq)
    //     .build();
    // }

    ///////////////////////////////// General node behaviors \\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\

    //Build node
    static public Props props(int a, int b, int c, int d,Boolean e) {
        return Props.create(Node.class,() -> new Node(a,b,c,d,e));
      }

    // a simple logging function
    void print(String s) {
        System.out.format("%2d: %s\n", id, s);
      }

    // emulate a delay of d milliseconds
    void delay(int d) {
        try {Thread.sleep(d);} catch (Exception ignored) {}
      }
  
    void multicast(Serializable m) {
        for (ActorRef p: participants)
          p.tell(m, getSelf());
    }

    void setGroup(StartMessage sm) {
        Nodes = new ArrayList<>();
        participants = new ArrayList<>();
        //add all node to list for future usage
        for (ActorRef b: sm.Nodes) {
          if (!b.equals(getSelf())) {
  
            // copying all participant refs except for self
            this.Nodes.add(b);
          }
        }

        // list of participants
        for (ActorRef b: sm.participants) {
            if (!b.equals(getSelf())) {
    
              // copying all participant refs except for self
              this.participants.add(b);
            }
          }

        if(sm.coordinator == getSelf()){
            this.isManager = true;
            //become coordinator receiver to do
        }
        this.Coordinator = sm.coordinator;

        
        print("starting with " + sm.Nodes.size() + " Node(s)");
    }

    // schedule a Timeout message in specified time
    void setTimeout(int time,String mode,int e,int s) {
      switch(mode){
        case "voteRes":
          getContext().system().scheduler().scheduleOnce(
            Duration.create(time, TimeUnit.MILLISECONDS),  
            getSelf(),
            new Timeout(e,s), // the message to send
            getContext().system().dispatcher(), getSelf()
            );
            break;
        case "b":

      }
     
    }

    ///////receiving functions
      public void onStartMessage(StartMessage msg){
        setGroup(msg);
        if(this.isManager){
            getContext().become(createReceiveCoordinator());
        } else {
          getContext().become(createReceiveparticipants());
        }
        
        }
  


    /////////////////Coordinator functions start here\\\\\\\\\\\\\\\\\\\\\

    public Receive createReceiveCoordinator() {
      return receiveBuilder()
        .match(nodewriteRequest.class, this::onWriteReqFromNode)
        .match(coordinatorVoteRes2.class, this::onVoteRes)
        .match(Timeout.class, this::onTimeout)
        .build();
    }

    //after receiving write req prepare voting system
    public void onWriteReqFromNode(nodewriteRequest msg){
      print("massage received in coordinator");
      this.SeqNumber +=1;

      //prepare final massage to send:)
      clientwriteResponse coordinatorToNodeFinalRes = new clientwriteResponse(msg.value,
      this.epoch,
      this.SeqNumber,
      msg.client,
      msg.node);

      //add massage to wotking massage
      this.workingMsg.put(this.SeqNumber, coordinatorToNodeFinalRes);

      //prepare voting array
      this.voters.clear();
      this.voters.add(getSelf());
      this.SeqVoters.put(this.SeqNumber, this.voters);

      //prepare voting mechanism
      coordinatorVoteReq onVoteReqp  = new coordinatorVoteReq(msg.value,
       this.epoch,
       this.SeqNumber,
       msg.client,
       msg.node);
      // multicast vote and wait for response after 1 sec
      multicast(onVoteReqp);
      print("voting started on coordinator with proposed value: "+msg.value);
      setTimeout(1000, "voteRes",this.epoch, this.SeqNumber);



      // ActorRef sendersss = msg.node;
      // this.voters.clear();
      // if(!this.voters.isEmpty()){
        
      // }
      // this.voters.add(getSelf());
      // print("fis");
      // EpochSeqPair.put(this.SeqNumber, this.epoch);
      // EpochSeqMassage.put(key, value)
      // this.SeqVoters.put(this.SeqNumber, this.voters);
      // coordinatorVoteReq onVoteReqp  = new coordinatorVoteReq(msg.value, this.epoch, this.SeqNumber, msg.client, msg.node);
      // this.VoteReq.add(onVoteReqp);
      // this.workingMsg.put(this.SeqNumber,onVoteReqp);
      // this.participants.get(3).tell(onVoteReqp, getSelf());
      // multicast(onVoteReqp);
      // print("massage sent to nodes");
      // setTimeout(1000, "voteRes",this.epoch, this.SeqNumber);

      // print("sent vote req: "+sendersss+" and value: "+msg.value);
      // Coordinator.tell(msg, getSelf());

    }

    //collect all yes voted
    public void onVoteRes(coordinatorVoteRes2 msg){
      this.SeqVoters.get(msg.seqNumber).add(getSender());
    }

    //when vote timeout received
    public void onTimeout(Timeout msg) {
      if(this.SeqVoters.get(msg.seq).size()>3){
        print("Voting completed");
        clientwriteResponse coordToclientRes = this.workingMsg.get(msg.seq);

        //send commit massage to voters
        coordinatorCommitRes2 commitresponse = new coordinatorCommitRes2(msg.seq);
        multicast(commitresponse);

        //finalize massage to keep record and setting value
        this.finallizedMsg.put(msg.seq, coordToclientRes);
        this.Value = coordToclientRes.value;

        //send back the reply to first node who sent the initial writing req
        coordToclientRes.node.tell(coordToclientRes, getSelf());



        // clientwriteResponse coordToclientRes = new clientwriteResponse(this.workingMsg.get(msg.seq).value,
        // this.workingMsg.get(msg.seq).epoch,
        // this.workingMsg.get(msg.seq).seqNumber,
        // this.workingMsg.get(msg.seq).client,
        // this.workingMsg.get(msg.seq).node
        // );

        // this.finalizedMsg.add(coordToclientRes);
        // this.workingMsg.remove(msg.seq);
        // this.SeqVoters.remove(msg.seq);
        // this.SeqNumber +=1;
        

      }
      
    }

    







    ////////////////////Participants functions start here\\\\\\\\\\\\\\\\\\\\\\

    public Receive createReceiveparticipants() {
        return receiveBuilder()
           .match(clientReadRequest.class, this::onClientReadReq)
           .match(clientwriteRequest.class, this::onClientWriteReq)

           .match(coordinatorVoteReq.class, this::onVoteReqp)
           .match(coordinatorCommitRes2.class, this::oncommitcoord)
           .match(clientwriteResponse.class, this::onFinishedResponse)

           .match(Timeout.class, this::onTimeouts)                    
           .build();
      }


    public void onClientReadReq(clientReadRequest msg){
        ActorRef sender = getSender();
        print("Read massage recieved from client"+sender);
        clientReadResponse onClientReadRes = new clientReadResponse(this.Value,this.epoch,this.SeqNumber);
        sender.tell(onClientReadRes, getSelf());

    }
    //forward all client write req to coordinator
    public void onClientWriteReq(clientwriteRequest msg){
        ActorRef sender = getSender();
        nodewriteRequest onWriteReqFromNode = new nodewriteRequest(msg.value, msg.client, msg.node);
        print("Im : "+getSelf()+"Write massage recieved from client : "+sender+" and value: "+msg.value);
        // System.out.println(this.Coordinator);
        this.Coordinator.tell(onWriteReqFromNode, getSelf());
    }

    //coordinator ask for the vote
    public void onVoteReqp(coordinatorVoteReq msg){
      print("client answer is yes");

      //prepare send back the response to coordinator
      coordinatorVoteRes2 voteRes = new coordinatorVoteRes2(msg.seqNumber);
      clientwriteResponse finalResult = new clientwriteResponse(msg.value,
        msg.epoch,
        msg.seqNumber,
        msg.client,
        msg.node);

      this.workingMsg.put(msg.seqNumber, finalResult);
      getSender().tell(voteRes, getSelf());

      //waiting for the response of the commit from coordinator
      setTimeout(2000, "voteRes",msg.epoch,msg.seqNumber);
    }

    //if everything goes well commit the result.
    public void oncommitcoord(coordinatorCommitRes2 msg){
      //set value and seq number
      this.Value = this.workingMsg.get(msg.seqNumber).value;
      this.SeqNumber = this.workingMsg.get(msg.seqNumber).seqNumber;

      //keep record of the fixed decision with pair of seq and massage
      clientwriteResponse coordToclientRes = this.workingMsg.get(msg.seqNumber);

      //remove working massage; if coordinator timeout on commit res node will check working massage
      this.finallizedMsg.put(this.SeqNumber,coordToclientRes);
      this.workingMsg.remove(this.SeqNumber);
    }

    public void onTimeouts(Timeout msg){
      // if (!this.workingMsg.get(msg.seq)){

      // }
      print("start election");
    }

    //receive final massage from coordinator and send it to client
    public void onFinishedResponse(clientwriteResponse msg){
      msg.client.tell(msg, getSelf());
    }


    //wait for the reply of coordinator and store it in a set then increase seq number 
    //and send it to client
    // public void onCoordinatorWriteRes(clientwriteResponse onClientWriteRes){
    //   writeReq.add(onClientWriteRes);
    //   SeqNumber += 1;
    //   if(SeqNumber != onClientWriteRes.seqNumber){
    //     print("something is missing in responses to nodes; Seq number is not right :(");
    //   }
    //   onClientWriteRes.client.tell(onClientWriteRes, getSelf());
    // }
    
}