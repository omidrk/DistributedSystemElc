package ds1;

import akka.actor.ActorRef;
import akka.actor.AbstractActor;
import akka.actor.ActorSystem;
import akka.actor.Props;
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

  public final static int N_PARTICIPANTS = 6;
  public final static int N_client = 2;


  public final static int DECISION_TIMEOUT = 2000;  // timeout for the decision, ms
  final static int InitValue = 1000;
  final static int epochValue = 0;
  final static int InitSeqNumber = 0;
  final static int VOTE_TIMEOUT = 1000;      // timeout for the votes, ms

  // Massages 
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
    public clientwriteRequest(int v){
      this.value = v;
    }
   }

   //response to write request from node to client
   public static class clientwriteResponse implements Serializable {
    final int value;          // value of the message to write
    final int epoch;       // epoch in which the message belongs
    final int seqNumber;   // sequence number of the message
    public clientwriteResponse(int v,int e,int seq){
      this.value = v;
      this.epoch = e;
      this.seqNumber = seq;
    }
   }



  
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
    System.out.println(Nodes);

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
