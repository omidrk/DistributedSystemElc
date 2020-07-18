package ds1;
import akka.actor.ActorRef;
import akka.actor.AbstractActor;
import akka.actor.ActorSystem;
import akka.actor.Props;
import ds1.DistributedSystemElc;
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
import java.util.HashSet;
import java.util.List;
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

    protected int Value;                        //Value of node
    protected int epoch;
    protected int SeqNumber;
    protected Boolean isManager = false;
    private Random rnd = new Random();

    public Node(int id,int Value,int epoch, int SeqNumber,Boolean isManager){
        this.Value = 1000;
        this.epoch = 0;
        this.SeqNumber = 0;
    }


    //general receive builder for both coordinator and participants
    @Override
    public Receive createReceive() {
        return receiveBuilder()
        .match(StartMessage.class, this::onStartMessage)
        .match(clientReadRequest.class, this::onClientReadReq)
        .match(clientwriteRequest.class, this::onClientWriteReq)
        .build();
    }

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

    ///////receiving functions

      public void onStartMessage(StartMessage msg){
        setGroup(msg);
        }
  


    /////////////////Coordinator functions start here\\\\\\\\\\\\\\\\\\\\\

    // @Override
    public Receive createReceiveCoordinator() {
      return receiveBuilder()
        .match(StartMessage.class, this::onStartMessage)
        .build();
    }

    







    ////////////////////Participants functions start here\\\\\\\\\\\\\\\\\\\\\\

    public Receive createReceiveparticipants() {
        return receiveBuilder()
          .match(clientReadRequest.class, this::onClientReadReq)
          .build();
      }


    public void onClientReadReq(clientReadRequest msg){
        ActorRef sender = getSender();
        print("massage recieved from "+sender+" done:))");
        clientReadResponse onClientReadRes = new clientReadResponse(this.Value,this.epoch,this.SeqNumber);
        sender.tell(onClientReadRes, getSelf());

    }

    public void onClientWriteReq(clientwriteRequest msg){
        ActorRef sender = getSender();
        print("Write massage recieved from client : "+sender);
        // clientReadResponse onClientReadRes = new clientReadResponse(this.Value,this.epoch,this.SeqNumber);
        // sender.tell(onClientReadRes, getSelf());

    }
    
}