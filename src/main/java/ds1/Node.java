package ds1;

import akka.actor.ActorRef;
import akka.actor.AbstractActor;
import akka.actor.Cancellable;
import akka.actor.Props;
import ds1.DistributedSystemElc.*;
import ds1.DistributedSystemElc.StartMessage;
import ds1.DistributedSystemElc.clientReadRequest;
import ds1.DistributedSystemElc.clientReadResponse;
import ds1.DistributedSystemElc.clientwriteRequest;
import ds1.DistributedSystemElc.clientwriteResponse;
import scala.PartialFunction.OrElse;
import scala.concurrent.duration.Duration;

import java.io.InputStream;
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

public class Node extends AbstractActor {

    protected int id;                           // node ID
    protected int nextNodeId;                   //next node to forward the message in election
    protected List<ActorRef> participants;      // list of participant nodes
    protected List<ActorRef> Nodes;             // list of all nodes
    protected List<ActorRef> Clients;           //list of Clients
    protected ActorRef Coordinator;             // coordinator

    protected Boolean Ping;                     //HeartBeat check
    protected Boolean isElection;               //check if participant is on Election
    protected Boolean electionAck;              //if ack is received after forwarding msg in election
    protected Integer nextNodeIdE;              //next node to send election
    protected int electionIssuer;               // node ID of election issuer

    protected Set<coordinatorVoteReq> VoteReq;      // for concurrent write req
    protected Set<clientwriteResponse> finalizedMsg;// finished voted massages. Keep history of all commited msg

    //Important ones 
    protected Map<Integer, clientwriteResponse> workingMsg;     //for saving vote req and response temporary
    protected Map<Integer, clientwriteResponse> finallizedMsg;  //for saving final result
    protected Set<ActorRef> voters;                             // keep voters in temporary set
    protected Map<Integer,Set<ActorRef>> SeqVoters;             //map between the voting set and sequence number
    

    //list all schedulers
    protected List<Cancellable> schedules;
    protected Map<String, Cancellable> schedulesMap;

    //put all election massages in map so keep track of them and make them seperate
    protected Map<Integer, startElection> electionMap;
    protected startElection electionMassageCache;

    protected int Value;                        //Value of node
    protected int epoch;
    protected int SeqNumber;
    protected Boolean isManager = false;        // if node is coordinator we set this var to true
    private Random rnd = new Random();

    public Node(int id,int Value,int epoch, int SeqNumber,Boolean isManager){
        this.id = id;
        this.nextNodeId = id;
        this.electionIssuer = 1000;
        this.Value = 1000;
        this.epoch = 0;
        this.SeqNumber = 0;
        this.Ping = false;
        this.isElection =false;
        this.electionAck = false;
        this.VoteReq = new HashSet<>();
        this.finalizedMsg = new HashSet<>();

        this.workingMsg = new HashMap<>();
        this.finallizedMsg = new HashMap<>();

        this.SeqVoters = new HashMap<>();
        this.voters = new HashSet<>();
        this.schedules = new ArrayList<>();
        this.schedulesMap = new HashMap<>();
        this.electionMap = new HashMap<>();

    }


    //general receive builder for both coordinator and participants
    @Override
    public Receive createReceive() {
        return receiveBuilder()
        .match(StartMessage.class, this::onStartMessage)
        .build();
    }

    ///////////////////////////////// General node behaviors \\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\

    // These are some method which is used by both coordinator and participants. For implementing coordinator and participants We used 
    // Node class as general class for both of them and we change the behavior of the Node by akka become method to coordinator(receive) and 
    //participant(receive).

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
  
    //Send message to all items in the list of the participants.
    void multicast(Serializable m) {
        for (ActorRef p: participants)
          p.tell(m, getSelf());
    }

    // after building Nodes and clients we will receive the list of the participants and coordinator 
    //This method will set these lists for starting the process
    void setGroup(StartMessage sm) {
        Nodes = new ArrayList<>();
        participants = new ArrayList<>();
        //add all node to list for future usage
        for (ActorRef b: sm.Nodes) {

          this.Nodes.add(b);
          
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
        }
        this.Coordinator = sm.coordinator;

        
        print("starting with " + sm.Nodes.size() + " Node(s)");
    }

    // This method is after electing new coordinator and when last msg is handled we need to clear all temporary variable 
    // and reset the lists of participants and coordinator and change to normal behavior.

    void setGroupPostElection(postElection sm) {
      Nodes.clear();
      participants.clear();
      //add all node to list for future usage
      for (ActorRef b: sm.Nodes) {

        this.Nodes.add(b);
        
      }

      // list of participants
      for (ActorRef b: sm.participants) {
          if (!b.equals(getSelf())) {
  
            // copying all participant refs except for self
            this.participants.add(b);
          }
        }

      this.Coordinator = sm.coordinator;
      
      print("starting with " + sm.Nodes.size() + " Node(s)");
  }


  //////Timeouts

    // schedule a Timeout message in specified time
    void setTimeout(int time,String mode,int e,int s) {
      switch(mode){
        case "voteRes":
          Cancellable voteRes =
          getContext().system().scheduler().scheduleOnce(
            Duration.create(time, TimeUnit.MILLISECONDS),  
            getSelf(),
            new Timeout(e,s,mode), // the message to send
            getContext().system().dispatcher(), getSelf()
            );
            this.schedules.add(voteRes);
            this.schedulesMap.put("voteRes",voteRes);
            break;

        case "voteResAfterElection":
          Cancellable voteResAfterElection =
          getContext().system().scheduler().scheduleOnce(
            Duration.create(time, TimeUnit.MILLISECONDS),  
            getSelf(),
            new Timeout(e,s,mode), // the message to send
            getContext().system().dispatcher(), getSelf()
            );
            this.schedules.add(voteResAfterElection);
            this.schedulesMap.put("voteResAfterElection",voteResAfterElection);
            break;
        case "pingpong":
          Cancellable pingpong =
          getContext().system().scheduler().scheduleOnce(
            Duration.create(time, TimeUnit.MILLISECONDS),  
            getSelf(),
            new PongFail(), // the message to send
            getContext().system().dispatcher(), getSelf()
            );
            this.schedules.add(pingpong);
            this.schedulesMap.put("pingpong",pingpong);
            break;
        case "electionAct":
          Cancellable electionAct =
          getContext().system().scheduler().scheduleOnce(
            Duration.create(time, TimeUnit.MILLISECONDS),  
            getSelf(),
            new Timeout(e,s,mode), // the message to send
            getContext().system().dispatcher(), getSelf()
            );
            this.schedules.add(electionAct);
            this.schedulesMap.put("electionAct",electionAct);
            break;
        case "noAck":
          Cancellable noAck =
          getContext().system().scheduler().scheduleOnce(
            Duration.create(time, TimeUnit.MILLISECONDS),  
            getSelf(),
            new Timeout(e,s,mode), // the message to send
            getContext().system().dispatcher(), getSelf()
            );
            this.schedules.add(noAck);
            this.schedulesMap.put("noAck",noAck);
            break;
        case "crashType1":
          Cancellable crashType1 =
          getContext().system().scheduler().scheduleOnce(
            Duration.create(time, TimeUnit.MILLISECONDS),  
            getSelf(),
            new Timeout(e,s,mode), // the message to send
            getContext().system().dispatcher(), getSelf()
            );
            this.schedules.add(crashType1);
            this.schedulesMap.put("crashType1",crashType1);
            break;

        case "crashType2":
          Cancellable crashType2 =
          getContext().system().scheduler().scheduleOnce(
            Duration.create(time, TimeUnit.MILLISECONDS),  
            getSelf(),
            new Timeout(e,s,mode), // the message to send
            getContext().system().dispatcher(), getSelf()
            );
            this.schedules.add(crashType2);
            this.schedulesMap.put("crashType2",crashType2);
            break;
      }
     
    }

    //Cancel all schedule inside the node
    public void scheduleCancel(Set<Cancellable> set){
      for(Cancellable cl: set){
        cl.cancel();
      }
    }

    ///////receiving functions
    // this is general receive method for initializing the Node as coordinator or participant.
    
      public void onStartMessage(StartMessage msg){
        setGroup(msg);
        if(this.isManager){
            getContext().become(createReceiveCoordinatorAndCrash());
            this.epoch+=1;
            if(!schedulesMap.isEmpty()){
              for(Cancellable cl: schedulesMap.values()){
                cl.cancel();
              }
            }
             
            resetVars();
            
        } else {
          getContext().become(createReceiveparticipants());
          this.epoch+=1;
          this.SeqNumber =0;
          if(!schedulesMap.isEmpty()){
            for(Cancellable cl: schedulesMap.values()){
              cl.cancel();
            }
          }
          resetVars();
          // startHeartBeat();
        }
        
        }

        ///after starting node they will start asking coordinator every 2 min to see if it is alive
        //if not it will start election 
        public void startHeartBeat(){
          Cancellable heartBeat = 
            getContext().system().scheduler().scheduleWithFixedDelay(
            Duration.create(10000, TimeUnit.MILLISECONDS),  
            Duration.create(120000, TimeUnit.MILLISECONDS),  
            getSelf(),
            new PingPongStartMassage(), // the message to send
            getContext().system().dispatcher(),
            getSelf()
            );

            //adding timeout to the list of cancellable schedules
            this.schedules.add(heartBeat);
            this.schedulesMap.put("heartBeat",heartBeat);

        }
  


    /////////////////Coordinator functions start here\\\\\\\\\\\\\\\\\\\\\

    //This section belong to the coordinator behavior. Diffrent receive methods and timouts are used for seperating 
    //coordinator from participants


    //Normal receive without any crash
    public Receive createReceiveCoordinator() {
      return receiveBuilder()
        .match(nodewriteRequest.class, this::onWriteReqFromNode)
        .match(coordinatorVoteRes2.class, this::onVoteRes)
        .match(Timeout.class, this::onTimeout)
        .match(Ping.class, this::onPing)
        .matchAny(msg -> {})
        .build();
    }

    // Receive with crash type 1 and type 2 hardcoded inside
    public Receive createReceiveCoordinatorAndCrash() {
      return receiveBuilder()
        .match(nodewriteRequest.class, this::onWriteReqFromNodeandCrash)
        .match(coordinatorVoteRes2.class, this::onVoteRes)
        .match(Timeout.class, this::onTimeout)
        .match(Ping.class, this::onPing)
        .matchAny(msg -> {})
        .build();
    }

    // receive with totally crash behavior without responding to any msg
    public Receive coordinatorCrashed() {
      return receiveBuilder()
        .match(Timeout.class, this::onTimeout)
        .match(coordinatorVoteRes2.class, this::onVoteRes)
        .matchAny(msg -> {})
        .build();
    }

    //receive after election when coordinator preparing for handling the last unfinished msg
    public Receive afterElectionCoordinator() {
      return receiveBuilder()
        .match(Timeout.class, this::onTimeout)
        .match(coordinatorVoteRes2.class, this::onVoteRes)
        .matchAny(msg -> {})
        .build();
    }

    //after receiving write req prepare voting system. In this part coordinator will make new voting msg, put it in the working msg and assign sequence
    //number to it. prepare voting response container and multicast voting.

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
      setTimeout(7000, "voteRes",this.epoch, this.SeqNumber);

    }

    //////////CrashMode\\\\\\\\\\\

    //This is the same as above except two crash type are hardcoded after epoch 1 and sequence number 4 and crash type2 
    //after epoch 2 and sequence number 4. Crash type 1 is sending decision after voting to at least one participant. Crash type 2 is 
    //cordinator crash before making decision

    public void onWriteReqFromNodeandCrash(nodewriteRequest msg){
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
      coordinatorToNodeFinalRes.epoch,
      coordinatorToNodeFinalRes.seqNumber,
      msg.client,
      msg.node);
      
      //////Crash on seq 4 and after sending commit to only one node\\\\\
      if(this.epoch<2 && this.SeqNumber == 2 && this.isElection ==false){

        // send only to one randomly and crash
        multicast(onVoteReqp);
        print("voting started on coordinator with proposed value: "+msg.value);
        setTimeout(5000, "crashType1",onVoteReqp.epoch, onVoteReqp.seqNumber);
        getContext().become(coordinatorCrashed());
        

      }else if(this.epoch==2 && this.SeqNumber == 2 && this.isElection ==false){
        multicast(onVoteReqp);
        print("voting started on coordinator with proposed value: "+msg.value);
        setTimeout(5000, "crashType2",onVoteReqp.epoch, onVoteReqp.seqNumber);
        getContext().become(coordinatorCrashed());
      }else if(this.epoch==3 && this.SeqNumber == 1){
        print(" hit the enter to shut down the system");
      }else {

        multicast(onVoteReqp);
        print("voting started on coordinator with proposed value: "+msg.value);
        setTimeout(5000, "voteRes",onVoteReqp.epoch, onVoteReqp.seqNumber);
      }
      
    }

    //After multicast vote we will wait and collect vote response until timeout reaches. Each vote has unique sequence number and can be done
    //concurrently. Here we just add voter to the sequence number. and later we will count the voters
    public void onVoteRes(coordinatorVoteRes2 msg){
      if(this.SeqVoters.get(msg.seqNumber) == null){
        //prepare voting array
      this.voters.clear();
      this.voters.add(getSelf());
      this.SeqVoters.put(msg.seqNumber, this.voters);
      }else{
        this.SeqVoters.get(msg.seqNumber).add(getSender());
      }
      
    }

    //Coordinator timeout system. Timeouts are scheduling to send a message to self after a certain time. We defined different mode for 
    //timeouts in different locations for instance after multicast vote or sending election message.

    public void onTimeout(Timeout msg) {
      
      switch(msg.mode){
        
          case("voteResAfterElection"):

            if(this.SeqVoters.get(msg.seq).size()>3){
              print("Voting completed on election");
              // send kill signal if vote not yet came. This will randomly kill some participants if they are false positive.
              killNotparticipatedNodes(this.SeqVoters.get(msg.seq));
              clientwriteResponse coordToclientRes = this.workingMsg.get(msg.seq);
      
              //send commit massage to voters
              coordinatorCommitRes2 commitresponse = new coordinatorCommitRes2(msg.seq);
              multicast(commitresponse);
      
              //finalize massage to keep record and setting value
              this.finallizedMsg.put(msg.seq, coordToclientRes);
              this.Value = coordToclientRes.value;
              this.SeqNumber = 0;
              this.epoch +=1;
              
              //send back the reply to first node who sent the initial writing req
              coordToclientRes.node.tell(coordToclientRes, getSelf());
              resetVars();

              break;
      
            }else {
              print("seq number : "+msg.seq+" is rejected :( We need more thread to handle requests.");
              break;
            }

          case("voteRes"):
            if(this.SeqVoters.get(msg.seq).size()>3){
              print("Voting completed");
              clientwriteResponse coordToclientRes = this.workingMsg.get(msg.seq);
      
              //send commit massage to voters
              coordinatorCommitRes2 commitresponse = new coordinatorCommitRes2(msg.seq);
              multicast(commitresponse);
      
              //finalize massage to keep record and setting value
              this.finallizedMsg.put(msg.seq, coordToclientRes);
              this.Value = coordToclientRes.value;
              this.SeqNumber = msg.seq;
              
              //send back the reply to first node who sent the initial writing req
              coordToclientRes.node.tell(coordToclientRes, getSelf());
              break;
      
            }else {
              print("seq number : "+msg.seq+" is rejected :( We need more thread to handle requests.");
              break;
            }

                    
          case("crashType1"):
          //crash after sending commit to one node
          
            if(this.SeqVoters.get(msg.seq).size()>3){
              print("Voting completed");              
              //send commit massage to one voter randomly 
              coordinatorCommitRes2 commitresponse = new coordinatorCommitRes2(msg.seq);
              Iterator<ActorRef> it = this.SeqVoters.get(msg.seq).iterator();
              ActorRef netcommit = it.next();
              if(netcommit == this.Coordinator){ netcommit = it.next();}
              //print the name of the commited node
              print("commited node in crash type 1 is : "+netcommit);
              netcommit.tell(commitresponse, getSelf());
              
              break;
      
            }else {
              print("seq number : "+msg.seq+" is rejected :(");
              break;
            }

        case("crashType2"):
          print("ls");
          //crash after sending commit to no one
          
            if(this.SeqVoters.get(msg.seq).size()>3){
              print("Coordinator failed to send commit to node and crashed (Crash type 2)");
              
              break;
      
            }else {
              print("seq number : "+msg.seq+" is rejected :(");
              break;
            }
      }
        
    }


    // If received a ping message send pong message 
    public void onPing(Ping msg){
      getSender().tell(new Pong(), getSelf());
    }

    ////////////////////Participants functions start here\\\\\\\\\\\\\\\\\\\\\\

    //This is a normal receive 

    public Receive createReceiveparticipants() {
        return receiveBuilder()
           .match(clientReadRequest.class, this::onClientReadReq)
           .match(clientwriteRequest.class, this::onClientWriteReq)

           .match(coordinatorVoteReq.class, this::onVoteReqp)
           .match(coordinatorCommitRes2.class, this::oncommitcoord)
           .match(clientwriteResponse.class, this::onFinishedResponses)

           .match(Timeout.class, this::onTimeouts)
           .match(PingPongStartMassage.class, this::onPingPongInit)
           .match(Pong.class, this::onPong)
           .match(PongFail.class, this::onPongFail)
           .match(startElection.class, this::onElection2)
           .match(killNode.class, this::onKillNode)
           .matchAny(msg -> {})


           .build();
      }
      ////////create receive for election
      public Receive electionReceive() {
        return receiveBuilder()
           .match(startElection.class, this::onElection2)
           .match(ackElection.class, this::onAckElection)
           .match(Timeout.class, this::onTimeouts)
           .match(StartMessage.class, this::onStartMessage)
           .match(postElection.class, this::onPostElection)
           .match(killNode.class, this::onKillNode)
           .matchAny(msg -> {})
           .build();
      }

      /////////create receive for crashed node
      public Receive CrashedReceive() {
        return receiveBuilder()
           .matchAny(msg -> {})
           .build();
      }

      //////////Create receive after alection to take care of last massage
      public Receive AfterElectionReceive() {
        return receiveBuilder()
        .match(postElection.class, this::onPostElection)
        .match(Timeout.class, this::onTimeouts)
        .match(coordinatorVoteReq.class, this::onVoteReqp)
        .match(coordinatorCommitRes2.class, this::oncommitAfterElection)
        .match(killNode.class, this::onKillNode)
        .matchAny(msg -> {})
        .build();
      }

    public void onKillNode(killNode msg){
        getContext().become(CrashedReceive());

    }

    //When participant receive read request from the client
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
        print("Im : "+getSelf()+"Write massage recieved from client : "+sender+" and value: "+msg.value+". Forwarded to coordinator.");
        this.Coordinator.tell(onWriteReqFromNode, getSelf());

    }

    //coordinator ask for the vote and after random delay participant will response to the coordinator
    public void onVoteReqp(coordinatorVoteReq msg){
      //add random delay for each node
      int delayTime = this.rnd.nextInt(1000)+500;
      int randomTime = this.id*1000;
      delay(delayTime);

      print("client vote answer is yes on sequence number : "+msg.seqNumber);

      //prepare send back the response to coordinator
      coordinatorVoteRes2 voteRes = new coordinatorVoteRes2(msg.seqNumber);
      clientwriteResponse finalResult = new clientwriteResponse(msg.value,
        msg.epoch,
        msg.seqNumber,
        msg.client,
        msg.node);

        // keep voted message in workingMsg to if commit not received coordinator will handle this later
      this.workingMsg.put(msg.seqNumber, finalResult);
      getSender().tell(voteRes, getSelf());

      //waiting for the response of the commit from coordinator
      delay(rnd.nextInt(5000)+1500);
      if(this.isElection ==false){
        setTimeout(15000+randomTime, "voteRes",msg.epoch,msg.seqNumber);
      }
      
    }

    //if everything goes well commit the result.
    public void oncommitcoord(coordinatorCommitRes2 msg){
   
      //if node receive massage which is not in the queue so its crashed :(

        if(this.workingMsg.get(msg.seqNumber) == null){
          print("SomeThing not right. Akka is giving some false crash that we dont request.");
          print("msg --- :"+msg.seqNumber+"  "+" ,sender is: "+getSender());
          getContext().become(CrashedReceive());
          return;

        }
        //cancel timout in case of commiting.
        this.schedulesMap.get("voteRes").cancel();
        this.schedulesMap.remove("voteRes");

        // message is already in workingMsg so we get the message 
        this.Value = this.workingMsg.get(msg.seqNumber).value;
        this.SeqNumber = this.workingMsg.get(msg.seqNumber).seqNumber;

        //keep record of the fixed decision with pair of seq and massage
        clientwriteResponse coordToclientRes = this.workingMsg.get(msg.seqNumber);

        //remove working massage; if coordinator timeout on commit res node will check working massage
        this.finallizedMsg.put(msg.seqNumber,coordToclientRes);
        this.workingMsg.remove(msg.seqNumber);
        
      
    }


    // Here we will handle different timouts based on the mode.
    public void onTimeouts(Timeout msg){
        switch(msg.mode){
          case("pingpong"):
            print("ping pong start election");
            break;
         
          case("voteRes"):
              print("started election cause no commit reply on vote from coordinator."+msg.seq);
              insideNode lm = new insideNode(this.Value, this.epoch, this.SeqNumber, getSelf(),this.id);
              startElection sEl = new startElection(lm,this.id);
              this.electionMassageCache = sEl;
              this.electionIssuer = this.id;
              startElection(this.id,sEl);
              break;
            
          case("noAck"):
      
              print("start election with next node: "+getSender());
              if(this.electionMassageCache == null){
                insideNode lmb = new insideNode(this.Value, this.epoch, this.SeqNumber, getSelf(),this.id);
                startElection sElb = new startElection(lmb,this.id);
                this.electionMassageCache = sElb;
              }

              this.nextNodeId +=1;
              startElection(this.nextNodeId,this.electionMassageCache);;
              break;
        }
      
      
    }

    

    //receive final massage from coordinator and send it to client
    void onFinishedResponses (clientwriteResponse msgg) {
      msgg.client.tell(msgg, getSelf());
    }

    //This heartbeat function will send ping massage to coordinator and wait for pong
    // if not receive in 30 sec election will start 
    void onPingPongInit(PingPongStartMassage msg){
      if(!this.isManager){
        this.Ping =false;
        this.Coordinator.tell(new Ping(), getSelf());
        setTimeout(30000, "pingpong", 0, 0);
      }
    }
    public void onPong(Pong msg){
      this.Ping = true;
    }
    void onPongFail(PongFail msg){
      if(!this.Ping){
        print("ping failed start election");
      }
    }


    //When we receive the election message first we will check if it is new or we are already in the election. if it is new we will send ack and 
    //add last message to the list and send to the next node. 
    //If we are already on the election 2 thing might happen if issuer is us so it means one circle is done we have to find the coordinator.
    // if issuer is someone else we add our last msg to it and forward and send ack.
    //if we receive a message and we are the issuer but we are not coordinator we forward until we reach the coordinator.
    // winner is checked by the max seq number . if there is more than one participant with max seq we ive the coordinator to the node with max 
    // id.
    // void onElection(startElection msg){
    //   //check if we are in the election or not
    //   if(this.isElection){
    //     print("Im on the elecetion");
    //     this.electionMassageCache = msg;
    //     print("election issuer is :"+this.electionIssuer+" -- in massage issuer is : "+msg.issuer);
    //     //check if issuer is the same as before or its new election
    //     if(this.electionIssuer == msg.issuer){
    //       //check if Im coordinator. it means i have max seq number or max id
    //       if(imCoordinator(msg, new insideNode(this.Value, this.epoch, this.SeqNumber, getSelf(),this.id), getSelf())){
    //         print("Im coordinator: "+getSelf()+" You are losers");
    //         //start sending new participant and coordinator list to remaining participants
    //         reInit(msg);
            
    //       }else{
    //         print("Im loser so forward massage");
    //         startElection msgg = msg;
    //         startElection(this.id, msgg);
    //       }
    //     }else{
    //       //check if you are losser forward otherwise cancel
    //       int seqMax = getMaxSeqNumber(msg.lastMassages);
    //       if(this.SeqNumber<=seqMax ){
    //         startElection msgg = msg;
    //         startElection(this.id, msgg);
    //       }

    //     }
        
 

    //   }else{
    //     this.electionIssuer = msg.issuer;
    //     // print("issuer"+this.electionIssuer +" -- "+ msg.issuer);
    //     getContext().become(electionReceive());
    //     print("send ack to previous node: "+getSender());
    //     getSender().tell(new ackElection(), getSelf());
    //     startElection msgg = msg;
    //     msgg.lastMassages.add(new insideNode(this.Value, this.epoch, this.SeqNumber, getSelf(),this.id));
    //     this.electionMassageCache = msg;
    //     startElection(this.id,msgg);
    //   }
    // }


    //When we receive the election message first we will check if it is new or we are already in the election. if it is new we will send ack and 
    //add last message to the list and send to the next node. 
    //If we are already on the election 2 thing might happen if issuer is us so it means one circle is done we have to find the coordinator.
    // if issuer is someone else we add our last msg to it and forward and send ack.
    //if we receive a message and we are the issuer but we are not coordinator we forward until we reach the coordinator.
    // winner is checked by the max seq number . if there is more than one participant with max seq we ive the coordinator to the node with max 
    // id.

    void onElection2(startElection msg){
      //check if we are in the election or not
      if(this.isElection){
        print("Im on the elecetion: "+getSelf());
        if(this.electionMap.get(msg.issuer) == null){
          print("another election received just forward");
          this.electionMap.put(msg.issuer, msg);
          getSender().tell(new ackElection(), getSelf());

          startElection msgg = msg;
          msgg.lastMassages.add(new insideNode(this.Value, this.epoch, this.SeqNumber, getSelf(),this.id));
          this.electionMassageCache = msg;
          startElection(this.id,msgg);
        }else{

          if(imCoordinator(msg, new insideNode(this.Value, this.epoch, this.SeqNumber, getSelf(),this.id), getSelf())){

            print("Im coordinator, You are losers :) , Coordinator name:"+ getSelf());
            afterElection(msg);
            
          }else{
            print("Im loser so forward massage");
            startElection msgg = msg;
            startElection(this.id, msgg);
        }
   
        }}
        else{

        this.electionMap.put(msg.issuer, msg);
        getContext().become(electionReceive());

        print("send ack election");
        getSender().tell(new ackElection(), getSelf());
        

        startElection msgg = msg;
        msgg.lastMassages.add(new insideNode(this.Value, this.epoch, this.SeqNumber, getSelf(),this.id));
        this.electionMassageCache = msg;
        startElection(this.id,msgg);
        }
    }

    // When participant receive ack from next node it will set electionAck to true and when time out reaches it will accept. otherwise send
    // election msg to next node in the round
    void onAckElection(ackElection msg){
      print("ack recieved1");
      this.electionAck = true;
      if(this.schedulesMap.get("noAck") != null){
        this.schedulesMap.get("noAck").cancel();
        this.schedulesMap.remove("noAck");
      }
      
    }

    // this will prepare the participant for election. It will change behavior to election receive and also send election to next node 
  
        void startElection(int id, startElection sEl) {
    
          //node goes into election receive mode
          getContext().become(electionReceive());
          this.isElection = true;
          this.electionAck = false;
    
          if(id > this.participants.size()){

            ActorRef nextNode = this.participants.get(0);
         
            this.nextNodeId = 1;
            nextNode.tell(sEl, getSelf());
            setTimeout(1000, "noAck", this.epoch, this.SeqNumber);
          }else{
            //case that we reach end of array
            ActorRef nextNode = this.Nodes.get(id+1);
            // insideNode lm1 = new insideNode(this.Value, this.epoch, this.SeqNumber, getSelf());
            // startElection sEl = new startElection(lm);
            nextNode.tell(sEl, getSelf());
            setTimeout(1000, "noAck", this.epoch, this.SeqNumber);
    
          }
        }

    //on election check whether node is coordinator and winner of the election or not
    //return boolean 
    public boolean imCoordinator(startElection msg, insideNode lm, ActorRef me) {

      List<insideNode> lastMsg = msg.lastMassages;
      // lastMsg.sort(c);
      Boolean imWinner = false;
      int maxSeq = getMaxSeqNumber(lastMsg);
      // int maxId = getMaxid(lastMsg);
      // int maxcount = CountseqRepeated(lastMsg,maxSeq);
      List<insideNode> maxSeqlist = new ArrayList<>();
      //find the list of max sequence winners.
      for(insideNode in: lastMsg){
        if(in.seqNumber == maxSeq){
          //if we have multi seq with same values then max id will win
          if(in.id> this.id){
            maxSeqlist.add(in);
          }
        }
      }
      //check if im coordinator or not
      if(maxSeqlist.isEmpty()){
        imWinner =false;
        for(startElection it: this.electionMap.values()){
          if(this.SeqNumber >= getMaxSeqNumber(it.lastMassages)){
            imWinner =true;
          }
        }
        // imWinner =true;
      }else{
        imWinner = false;
      }
      return imWinner;
      
      }


      //election msg contain last msg from all participants. This method will find the max sequence number among the all msgs
      int getMaxSeqNumber(List<insideNode> msg){
        int maxSeq = 0;
        for(insideNode in: msg){
          if(in.seqNumber > maxSeq){
            maxSeq = in.seqNumber;
          }
          
        }
        return maxSeq;
      }

      //election msg contain last msg from all participants. This method will find the max id number among the all participants.

      int getMaxid(List<insideNode> msg){
        // msg.sort(Comparator.comparingInt(insideNode::getSeq));
        int maxId =0;
        for(insideNode in: msg){
          if(in.id> maxId){
            maxId = in.id;
          }
        }
        return maxId;
      }
      // This method will return how many of the participants have the max sequence number.
      int CountseqRepeated(List<insideNode> msg,int seqmax){
        int count =0;
        for(insideNode in: msg){
          if(seqmax == in.seqNumber){
            count +=1;
          }
        }
        return count;
      }



       // void reInit(startElection msg){

    //   //prepare coodrinator
    //   getContext().become(createReceiveCoordinatorAndCrash());
    //   this.isManager = true;
    //   this.isElection =false;
    //   this.electionIssuer = 1000;
      
    //   ActorRef coordE = getSelf();
    //   List<ActorRef> participantsE = new ArrayList<>();
    //   List<ActorRef> NodesE = new ArrayList<>();
    //   for(insideNode in: msg.lastMassages){

    //     NodesE.add(in.node);
    //     if(in.node != getSelf()){
    //       participantsE.add(in.node);
    //     }
    //   }

    //   //Sent Init massage
    //   StartMessage onStartMessage = new StartMessage(NodesE, participantsE, coordE);
    //   for (ActorRef i : participantsE){
    //     i.tell(onStartMessage, null);
    //   // this.epoch+=1;
    // }
    // }


    // This is after election when we know the coordinator and we want to tell other participants about new lists of participants and new coordinator
    //So this method will change the behavior of the coordinator to normal with crash( crash type 2 and 1 ). Send lists to participants.
    // this method will also take care of the last unfinished msg. Because coordinator is the Node with the last msg there is two possibility:
    //If workingMsg is not empty it means we have msg which is undecided so we will vote again. if workingMsg is empty it means Coordinator already 
    //decided about the msg and at least send it to one so we will send result to others.
   
    void afterElection(startElection msg){

      //cancel all schedules 
      for(Cancellable cl: schedulesMap.values()){
        cl.cancel();
      }

      //prepare coodrinator
      getContext().become(createReceiveCoordinatorAndCrash());
      this.isManager = true;
      this.isElection =false;
      this.electionIssuer = 1000;
      
      ActorRef coordE = getSelf();
      List<ActorRef> participantsE = new ArrayList<>();
      List<ActorRef> NodesE = new ArrayList<>();

      NodesE.add(getSelf());
      for(insideNode in: msg.lastMassages){

        if(!NodesE.contains(in.node)  && in.node != getSelf()){
          NodesE.add(in.node);
          
          participantsE.add(in.node);
          
        }
      }
     
      
       //Sent Init massage. this will change the behavior of the participant from election to normal.
       postElection postElect = new postElection(NodesE, participantsE, coordE);
       for (ActorRef i : participants){
         if(participantsE.contains(i) && i!= getSelf()){
          i.tell(postElect, null);
          print("told : "+i);
         }
         
        }

        this.Nodes.clear();
        this.participants.clear();
        this.Nodes = NodesE;
        this.participants = participantsE;
        this.Coordinator = getSelf();

        delay(800);

      //if there is any msg in workingmsg so it means that we need to vote again on that msg cause 
      //coordinator crashed before sending the last msg
      if(!this.workingMsg.isEmpty()){
        //start voting
        //prepare voting array
        this.voters.clear();
        this.SeqVoters.clear();
        this.voters.add(getSelf());
        this.SeqVoters.put(this.SeqNumber-1, this.voters);
        clientwriteResponse msgtoVote = this.workingMsg.get(2);

        //prepare voting mechanism
        coordinatorVoteReq onVoteReqp  = new coordinatorVoteReq(msgtoVote.value,
        msgtoVote.epoch,
        msgtoVote.seqNumber,
        msgtoVote.client,
        msgtoVote.node);
        // multicast vote and wait for response for 7 sec
        multicast(onVoteReqp);
        print("voting again started on coordinator with proposed value: "+msgtoVote.value);
        setTimeout(7000, "voteResAfterElection",msgtoVote.epoch, msgtoVote.seqNumber);

      }else{
        //send the last fixed value to nodes
        clientwriteResponse msgtoVote = this.finallizedMsg.get(2);

        //send commit massage to voters
        coordinatorCommitRes2 commitresponse = new coordinatorCommitRes2(msgtoVote.seqNumber);
        multicast(commitresponse);
        print("final massage has been commited");

        //reset everything and prepare next epoch
        resetVars();
        this.SeqNumber = 0;
        this.epoch +=1;
        this.id = 0;
      }

     
    }

    // after receiving this it means we have to cancel all schedules and prepare for the handling of the last msg.
    // We change the behavior to the AfterElectionReceive and wait for the last msg
    void onPostElection(postElection msg){
      //cancel all schedules 
      for(Cancellable cl: schedulesMap.values()){
        cl.cancel();
      }

      getContext().become(AfterElectionReceive());
      setGroupPostElection(msg);
      
    }

    // base on the decision about the last msg when participant commit the final result it will reset all variables and scheduls and 
    // change behavior to the normal one.
    public void oncommitAfterElection(coordinatorCommitRes2 msg){
   
      //if node receive massage which is not in the queue so its crashed :(

        //cancel timout in case of commiting.
        this.schedulesMap.get("voteRes").cancel();
        this.schedulesMap.remove("voteRes");

        this.Value = this.workingMsg.get(msg.seqNumber).value;
        this.SeqNumber = this.workingMsg.get(msg.seqNumber).seqNumber;

        //keep record of the fixed decision with pair of seq and massage
        clientwriteResponse coordToclientRes = this.workingMsg.get(msg.seqNumber);

        //remove working massage; if coordinator timeout on commit res node will check working massage
        this.finallizedMsg.put(msg.seqNumber,coordToclientRes);
        this.workingMsg.clear();
        
        resetVars();
        this.epoch +=1;
        this.SeqNumber =1;
        delay(400);
        getContext().become(createReceiveparticipants());
        // print("My index is : "+this.id);
        this.id = this.Nodes.indexOf(getSelf());
        this.nextNodeId = this.id;
        // print("My index was : "+this.id);

      // }
      
    }

  //reset vars
  void resetVars(){
    this.isElection =false;
    this.electionAck = false;
    this.VoteReq.clear();
    this.workingMsg.clear();
    this.voters.clear();
    this.SeqVoters.clear();
    this.electionMassageCache = null;
    this.schedulesMap.clear();
    this.electionMap.clear();
  }

  void killNotparticipatedNodes(Set<ActorRef> voters){
    for(ActorRef it: this.participants){
      if(!voters.contains(it)){
        it.tell(new killNode(), null);
      }
    }
  }

 
}