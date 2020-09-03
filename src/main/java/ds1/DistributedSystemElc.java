package ds1;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.io.IOException;
import java.io.InputStream;


public class DistributedSystemElc {
  //Some initial variable

  public final static int N_PARTICIPANTS = 6;
  public final static int N_client = 1;


  final static int InitValue = 1000;
  final static int epochValue = 0;
  final static int InitSeqNumber = 0;

  ///////////////Massages data structure ++++++++++++++



    //kill Node immidietly for simulating participant crash randomly
    public static class killNode implements Serializable {}

    //Response of the vote from participants to coordinator. Participants just need to say yes or no.
    public static class coordinatorVoteRes2 implements Serializable {
      final int seqNumber;  
        public coordinatorVoteRes2(int seq){
          this.seqNumber = seq;
          }
      }

    //commit from coordinator to participants

    public static class coordinatorCommitRes2 implements Serializable {
      final int seqNumber;   // sequence number of the message
      public coordinatorCommitRes2(int seq){
        this.seqNumber = seq;
        }
      }

  //for scheduling timeout. we need to know this schedule belong to which epoch and sequence number
  //also by defining mode which is a string we can know this timeout belong to what part of the process
  // for instance it is for voting or election or ... 
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
  // Start message that sends the list of participants to everyone in the begining of the code.
  // WHen node receive this message it will use setgroup method to set all the list and init the process
  public static class StartMessage implements Serializable {
    
    public final List<ActorRef> Nodes;           //list on all participants plus coordinator
    public final List<ActorRef> participants;   //list of participants
    public final ActorRef coordinator;          //initial coordinator

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

  // After election new coordinator will send postelection message which contain the list of the remaning participants
  // and new coordinator. 
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

  

  //Read req massage. this will sent from client to local node and response is 
  //local value of the node.

  public static class clientReadRequest implements Serializable { }
  public static class clientReadResponse implements Serializable { 
    final int value;          // value of the message to write
    final int epoch;          // epoch in which the message belongs
    final int seqNumber;      // sequence number of the message
    public clientReadResponse(int v,int e,int seq){
      this.value = v;
      this.epoch = e;
      this.seqNumber = seq;
    }
  }

  //massage from client to participant to write a value.
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

   //response of the participant to client for write request
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

    // when participants receive the write request it will forward this message to coordinator

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

   /////////Voting start here
   //Coordinator send vote request to the participants. this vote also contain the value,sequence number, epoch 
   // participant who forward the message, and the client. participant will keep this message in temp set and wait
   // for the commit response

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


    //to start heartbeat
    public static class Ping implements Serializable { }
    public static class Pong implements Serializable { }
    public static class PongFail implements Serializable { }

    public static class PingPongStartMassage implements Serializable { }

    //////////////////starting election
    //each participant will add the last message to the startElection message and send to next one. Thiss message also
    //keep track of the issuer of the election for concurrency

    public static class startElection implements Serializable {
   
      final List<insideNode> lastMassages = new ArrayList<>();
      final Integer issuer;
      public startElection(insideNode lm, Integer id){
          this.lastMassages.add(lm);
          this.issuer = id;
        }
     }

     //is the structure of the last message for each participant
     //startElection will hold the list of these last message which will select the coordinator based on
     // the max sequence number or max ID
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
  
     //sending acknowledgement to previous participant 
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
    
    //merge participants and coordinator in nodes list
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

    //for starting clients and read and write process
    clientStart onStartClient = new clientStart(Nodes);
    for (ActorRef i : Clientss){
      i.tell(onStartClient, null);
    }

    inputContinue();

    system.terminate();

    
}

public static void inputContinue() {
  try {
    System.out.println(">>> Press ENTER to continue <<<");
    System.in.read();
  }
  catch (IOException ignored) {}
}
}
