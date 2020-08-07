package ds1;

import akka.actor.ActorRef;
import akka.actor.AbstractActor;
import akka.actor.ActorSystem;
import akka.actor.Props;
import scala.annotation.meta.field;
import scala.concurrent.duration.Duration;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.lang.Thread;
import java.util.Collections;

import java.io.IOException;

import ds1.Node;
import ds1.Clients;


public class DistributedSystemElc {
  //Some initial variable

  public final static int N_PARTICIPANTS = 8;
  public final static int N_client = 2;


  public final static int DECISION_TIMEOUT = 2000;  // timeout for the decision, ms
  final static int InitValue = 1000;
  final static int epochValue = 0;
  final static int InitSeqNumber = 0;
  final static int VOTE_TIMEOUT = 1000;      // timeout for the votes, ms

  // Massages 

  ///////////////new data structure ++++++++++++++

  public static class coordinatorVoteReq2 implements Serializable {
    final int value;          // value of the message to write
    final int epoch;       // epoch in which the message belongs
    final int seqNumber;   // sequence number of the message
    final ActorRef client;
    final ActorRef node;
      public coordinatorVoteReq2(int v,int e,int seq,ActorRef cl,ActorRef nd){
        this.value = v;
        this.epoch = e;
        this.seqNumber = seq;
        this.client = cl;
        this.node = nd;
        }
    }
    //kill Node immidietly
    public static class killNode implements Serializable {}

    public static class coordinatorVoteRes2 implements Serializable {
      final int seqNumber;   // sequence number of the message
        public coordinatorVoteRes2(int seq){
          this.seqNumber = seq;
          }
      }

    //commit from coordinator

    public static class coordinatorCommitRes2 implements Serializable {
      final int seqNumber;   // sequence number of the message
      public coordinatorCommitRes2(int seq){
        this.seqNumber = seq;
        }
      }

  //for timeout
  public static class Timeout implements Serializable {
    public final int epoch;
    public final int seq;
    public final String mode;
    public Timeout (int e,int s,String m){
      this.epoch = e;
      this.seq = s;
      this.mode = m;
    }

  }
  // Start message that sends the list of participants to everyone
  public static class StartMessage implements Serializable {
    
    public final List<ActorRef> Nodes;      //list on all nodes
    public final List<ActorRef> participants;   //list of participants
    public final ActorRef coordinator;     //initial coordinator

    public StartMessage(List<ActorRef> Nodes,List<ActorRef> participants,ActorRef coordinator) {
      this.Nodes = Collections.unmodifiableList(new ArrayList<>(Nodes));
      this.participants = Collections.unmodifiableList(new ArrayList<>(participants));
      this.coordinator = coordinator;
    }
  }

  // Start message that sends the list of participants to everyone
  public static class postElection implements Serializable {
    
    public final List<ActorRef> Nodes;      //list on all nodes
    public final List<ActorRef> participants;   //list of participants
    public final ActorRef coordinator;     //initial coordinator

    public postElection(List<ActorRef> Nodes,List<ActorRef> participants,ActorRef coordinator) {
      this.Nodes = Collections.unmodifiableList(new ArrayList<>(Nodes));
      this.participants = Collections.unmodifiableList(new ArrayList<>(participants));
      this.coordinator = coordinator;
    }
  }

  //Start massage for running clients. after sending this clients will start sending read and 
  //write req to their own nodes 

  public static class clientStart implements Serializable {
    public final List<ActorRef> nodes;   //list of clients
    public clientStart (List<ActorRef> node) {
      this.nodes = Collections.unmodifiableList(node);

    }
  }

  //Read req massage. this will sent from client to local node and response is 
  //local value of the node.

  public static class clientReadRequest implements Serializable { }
  public static class clientReadResponse implements Serializable { 
    final int value;          // value of the message to write
    final int epoch;       // epoch in which the message belongs
    final int seqNumber;   // sequence number of the message
    public clientReadResponse(int v,int e,int seq){
      this.value = v;
      this.epoch = e;
      this.seqNumber = seq;
    }
  }
  //massage to write a value on node
  public static class clientwriteRequest implements Serializable {
    final int value;          // value of the message to write
    final ActorRef client;
    final ActorRef node;
    public clientwriteRequest(int v,ActorRef cl,ActorRef nd){
      this.value = v;
      this.client = cl;
      this.node = nd;
    }
   }

   //response to write request from node to client
   public static class clientwriteResponse implements Serializable {
    final int value;          // value of the message to write
    final int epoch;       // epoch in which the message belongs
    final int seqNumber;   // sequence number of the message
    final ActorRef client;
    final ActorRef node;
      public clientwriteResponse(int v,int e,int seq,ActorRef cl,ActorRef nd){
        this.value = v;
        this.epoch = e;
        this.seqNumber = seq;
        this.client = cl;
        this.node = nd;
        }
    }


    public static class nodewriteRequest implements Serializable {
      final int value;          // value of the message to write
      final ActorRef client;
      final ActorRef node;
      public nodewriteRequest(int v,ActorRef cl,ActorRef nd){
        this.value = v;
        this.client = cl;
        this.node = nd;
      }
     }
  
     //response to write request from node to client
     public static class nodewriteResponse implements Serializable {
      final int value;          // value of the message to write
      final int epoch;       // epoch in which the message belongs
      final int seqNumber;   // sequence number of the message
      final ActorRef client;
      final ActorRef node;
        public nodewriteResponse(int v,int e,int seq,ActorRef cl,ActorRef nd){
          this.value = v;
          this.epoch = e;
          this.seqNumber = seq;
          this.client = cl;
          this.node = nd;
          }
      }

   //Voting start here
   //

   public static class coordinatorVoteReq implements Serializable {
    final int value;          // value of the message to write
    final int epoch;       // epoch in which the message belongs
    final int seqNumber;   // sequence number of the message
    final ActorRef client;
    final ActorRef node;
      public coordinatorVoteReq(int v,int e,int seq,ActorRef cl,ActorRef nd){
        this.value = v;
        this.epoch = e;
        this.seqNumber = seq;
        this.client = cl;
        this.node = nd;
        }
    }

    public static class coordinatorVoteRes implements Serializable {
      final int value;          // value of the message to write
      final int epoch;       // epoch in which the message belongs
      final int seqNumber;   // sequence number of the message
      final ActorRef client;
      final ActorRef node;
        public coordinatorVoteRes(int v,int e,int seq,ActorRef cl,ActorRef nd){
          this.value = v;
          this.epoch = e;
          this.seqNumber = seq;
          this.client = cl;
          this.node = nd;
          }
      }

    //commit from coordinator

    public static class coordinatorCommitRes implements Serializable {
      final int value;          // value of the message to write
      final int epoch;       // epoch in which the message belongs
      final int seqNumber;   // sequence number of the message
      final ActorRef client;
      final ActorRef node;
        public coordinatorCommitRes(int v,int e,int seq,ActorRef cl,ActorRef nd){
          this.value = v;
          this.epoch = e;
          this.seqNumber = seq;
          this.client = cl;
          this.node = nd;
          }
      }


    //to start heartbeat
    public static class Ping implements Serializable { }
    public static class Pong implements Serializable { }
    public static class PongFail implements Serializable { }

    public static class PingPongStartMassage implements Serializable { }

    //starting election


    public static class insideNode implements Serializable {
      final int value;          // value of the message to write
      final int epoch;       // epoch in which the message belongs
      final int seqNumber;   // sequence number of the message
      final ActorRef node;
      final int id;
      public insideNode(int v,int e,int seq,ActorRef nd,int id){
        this.value = v;
        this.epoch = e;
        this.seqNumber = seq;
        this.node = nd;
        this.id = id;
        }

        int getSeq(){
          return this.seqNumber;
        }
        int getID(){
          return this.id;
        }
     }
     public static class startElection implements Serializable {
   
      final List<insideNode> lastMassages = new ArrayList<>();
      final Integer issuer;
      public startElection(insideNode lm, Integer id){
          this.lastMassages.add(lm);
          this.issuer = id;
        }
     }
     public static class ackElection implements Serializable {}








  
  /*-- Main ------------------------------------------------------------------*/
  public static void main(String[] args) {

    // Create the actor system
    final ActorSystem system = ActorSystem.create("DistProject");

    // Create participants
    List<ActorRef> participants = new ArrayList<>();
    for (int i=1; i <= N_PARTICIPANTS; i++) {
      participants.add(system.actorOf(Node.props(i, InitValue, epochValue, InitSeqNumber,false) ,"participant" + i));
    }


    // Create Clients
    List<ActorRef> Clientss = new ArrayList<>();
    for (int i=1; i <= N_client+1; i++) {
      Clientss.add(system.actorOf(Clients.props(i) ,"Clients" + i));
    }

    // Create the coordinator
    ActorRef coordinator = system.actorOf(Node.props(0, InitValue, epochValue, InitSeqNumber,true), "coordinator");
    
    //merge all in nodes
    List<ActorRef> Nodes = new ArrayList<>();
    Nodes.add(coordinator);
    for (ActorRef i : participants){
      Nodes.add(i);
    }

    //Sent Init massage
    StartMessage onStartMessage = new StartMessage(Nodes, participants, coordinator);
    for (ActorRef i : Nodes){
      i.tell(onStartMessage, null);
    }

    //for starting clients
    clientStart onStartClient = new clientStart(Nodes);
    for (ActorRef i : Clientss){
      i.tell(onStartClient, null);
    }


   

    try {
      System.out.println(">>> Press ENTER to exit <<<");
      System.in.read();
    } 
    catch (IOException ignored) {}
    system.terminate();
  }
}
