package ds1;

import akka.actor.ActorRef;
import akka.actor.AbstractActor;
import akka.actor.ActorSystem;
import akka.actor.Props;
import ds1.DistributedSystemElc;
import ds1.DistributedSystemElc.StartMessage;
import ds1.DistributedSystemElc.clientStart;
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



public class Clients extends AbstractActor{

    protected int id;                           // node ID
    protected ActorRef MyNode;             // list of all nodes
    protected int Value;                        //Value of node
    protected int epoch;
    protected int SeqNumber;
    private Random rnd = new Random();

    public Clients(int id){
        this.id = id;
        //Send massage to self to start process
        readValueStart();
        writeValueStart();

    }


    //general receive builder for both coordinator and participants
    @Override
    public Receive createReceive() {
        return receiveBuilder()
        .match(clientStart.class, this::onStartClient)
        .match(clientReadRequest.class, this::onStartRead)
        .match(clientReadResponse.class, this::onClientReadRes)
        .match(clientwriteRequest.class, this::onStartwrite)
        .match(clientwriteResponse.class, this::onClientWriteRes)
        .build();
    }

    ///////////////////////////////// General node behaviors \\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\

    //Build node
    static public Props props(int id) {
        return Props.create(Clients.class,() -> new Clients(id));
      }

    // a simple logging function
    void print(String s) {
        System.out.format("%2d: %s\n", id, s);
      }

    // emulate a delay of d milliseconds
    void delay(int d) {
        try {Thread.sleep(d);} catch (Exception ignored) {}
      }




    //massage receive at the begining of the process
    public void onStartClient(clientStart msg){
        this.MyNode = msg.nodes.get(this.id);
        print("client done ---id : "+ this.id);
        print("my node is : "+ this.MyNode);

    }

    //schedule read value from Node. This will start sending massage to self and schedule randomly
    // to do it again 
    public void readValueStart(){

        clientReadRequest onStartRead = new clientReadRequest();
        int interval = rnd.nextInt(10)+5;

        getContext().system().scheduler().scheduleOnce(Duration.create(5, TimeUnit.SECONDS), 
            getSelf(), 
            onStartRead, 
            getContext().system().dispatcher(), 
            getSelf());
    }

//start send massage to self to satrting write process
    public void writeValueStart(){

        clientwriteRequest onStartwrite = new clientwriteRequest(10);
        getContext().system().scheduler().scheduleOnce(Duration.create(5, TimeUnit.SECONDS), 
            getSelf(), 
            onStartwrite, 
            getContext().system().dispatcher(), 
            getSelf());
    }


    

    //when receive massage from self to start read process and send it to node 

    public void onStartRead(clientReadRequest msg){

        int interval = rnd.nextInt(10)+5;
        clientReadRequest onClientReadReq = new clientReadRequest();
        getContext().system().scheduler().scheduleWithFixedDelay(Duration.create(1, TimeUnit.SECONDS), 
            Duration.create(interval, TimeUnit.SECONDS), 
            this.MyNode, 
            onClientReadReq, 
            getContext().system().dispatcher(), 
            getSelf());

    }

    //receive write req from self to start th whole process and send write req to the node
    public void onStartwrite(clientwriteRequest msg){
        int value = rnd.nextInt(1000);
        int interval = rnd.nextInt(10)+5;
        clientwriteRequest onClientwriteReq = new clientwriteRequest(value);
        getContext().system().scheduler().scheduleWithFixedDelay(Duration.create(1, TimeUnit.SECONDS), 
            Duration.create(interval, TimeUnit.SECONDS), 
            this.MyNode, 
            onClientwriteReq, 
            getContext().system().dispatcher(), 
            getSelf());
        print("massage sended to the node with value : "+ value);
    }

    //printing the response of the node to the read request
    public void onClientReadRes(clientReadResponse msg){
        print("client response received");
        print("Value for node X "+"in <"+msg.epoch+" - "+msg.seqNumber+"> is "+msg.value);
    }

    //printing the response of the node to the write request
    public void onClientWriteRes(clientwriteResponse msg){
        print("client write response received");
        print("Value for node X "+"in <"+msg.epoch+" - "+msg.seqNumber+"> is "+msg.value);
    }
    
    
}