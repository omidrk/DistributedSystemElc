# Structure of the project

This project consists of 3 main files( DistributedSystemElc, Node, Client).
DistributedSystemElc is the main file. It contains all massage classes for send and receives, the Main void method which handles creating the actor system and initiates all participants and coordinator, and then sends start massages to start the system. 
Node.java contains Node class which is the main class for coordinator and participants. In this class, we define all variables and methods necessary for transmission and the behavior of Node.
Client.java contains Client class which is responsible for sending read and write requests. Client will only send read/write requests to the fixed participant through time.
The protocol of structure is refer to Appendix 1.

# Components
## Voting
Voting sequence starts when a coordinator receives a new write request from participants. coordinator will assign a unique sequence number to each request (incremental) so it can handle voting of the multiple write request at the same time. After this assignment coordinator will send votes request to participants simultaneously, and set a timeout for the vote response. When Participants receiving the vote request will respond immediately and keep the write message in a temporary set. This temporary set corresponds to handling unfinished requests when a coordinator crash. When the timeout arrives, the coordinator will count the votes based on the sequence number and finalize the message by sending commits to the participants. Moreover, Participants when receiving the vote request will set the timeout if the coordinator crashed and not received the commit message after a certain time.

All variables needed for this process have been explained in the storing messages section. We can conclude two facts from this logic. First write messages will be handled in the sequential order because of the fixed timeout and sequence number. Second, by keeping write message in a temporary set if a crash is detected we can finish the process after the election.
## Election
Election started based on a returned message: ‘ping’ ‘pong’ . For example, firstly participants will send ‘ping’ to the coordinator and set a timeout for the response. If participants received the ‘pong’ message it will cancel the timeout otherwise start the election. Election will be activated in two cases:

Participant not received commit after receiving voting request
Participant not received response after forwarding the write request message

At each epoch we will assign integers as id to working participants. Each participant contains the list of the working participants with their unique id. When participants start election it will call the startElection method. Behavior of the participants will change from normal to election, and any read or write request will be ignored. startElection method will send the start election message to the next node in the list (If participant is the last node in the list it will send to the first one in the list - circular list) and store the next node as variable. Participants expect Acknowledgement from the next node in the certain time. If not received ack from the next node, it will resend to the next node in the list. Based on the assumption that half +1 of the participants are always alive so for sure there is a node that can send back the ack message so definitely the election will be ended and there is no need to set the time for finishing the election. startElection message also contains the name of the issuer of the election and the last message that it has.

Participants will check the result of the election when it receives the message and it is also on the election. In this case it will check the issuer of the message. Message will be forward when the issuer is not itself, and issuer itself means the election message finished one round. so we check the how has the last message in the list. If I have the last message im coordinator otherwise forward until I reach the coordinator. This idea will solve these problems :
Two participants start election at the same time or from different locations.
In this case we distinguish between the different election messages by the issuer and we circulate them concurrently. The first message who finished the circle will choose the coordinator.
Receiving the election message but we are already in the election.
assigning issuer to each message will solve this.
Participants crash in the middle of the election.
If we don't receive the Acknowledge from the next node we simply skip that node and send to next in the list so for sure there are some nodes which are alive and can circulate the message.
## After Election
The new cooperator will send init messages(postElection class) to the participants. The init message contains the list of the participants and coordinator to be replaced with the previous one , behavior of the participants will be changed to normal. In this behavior, participants will wait for the coordinator instruction to continue the unfinished write request and install a new epoch.
There is two scenario where new coordinator handled:
Last write request already decided.
In this case cause coordinator is the node with the last message so it knows the decision and send it to all participants.( temporary message is empty)
Last write request voted but decision is unknown (temporary message not empty).
In this case nobody knows the decision so the coordinator will vote again on the decision and commit the message.
After handling the last message coordinator will send postElection message. When participants receive this message they change their behavior to the normal and empty all temporary variables, clear the timeout schedules, install a new epoch by increasing its number  and wait for new read or write requests from clients.

