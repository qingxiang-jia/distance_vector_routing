Programming Assignment 2
CSEE 4119 Spring 2014
Qingxiang Jia

a. Description

	I have a lot to say about this project which I have put so much effort on, but I know you do not have much time, so I will be brief.

	First of all, I didn't use anything like serializable, which means when I send the data, it is very primitive. My general approach is to first convert it to string, and put it into ByteBuffer, and use UDP DatagramChannel to send it. The advantage is that I get to design my own protocal for each operation. The disadvantage is that the code is more complex.

	You may think my code is too long. The reason is: half of the lines are comments. I put so many comments so that you can easily understand my code. Second, sometimes, I use more lines so that the code is clear. You can see this with HashMap, where I generally use a temporary variable to hold the value, then plug the variable into some other method instead of put someHashMap.getValue().

	There are some commented code -- I left them intentionally -- so that you can uncomment them and see how the program really works clearly. These codes are commented with /** some code **/. Code commented with // are not so important but nice to have during debugging. I think they may be helpful for you, so I didn't remove them.

	All requirements are met, and I did two extra credits. You can send both private messages and public messages when the network has converged.

b. Development Environment

	I developped bfclient in Eclipse with Java 1.7. Then when I tested it on CLIC machines, I realized some code din't work on Java 1.6. I did some modification on code related to ava.net.StandardSocketOptions which doesn't exist with Java 1.6.

	Develop platform: OS X 10.9.2

c. Instructions

	I carefully followed the requirements. Just run it as it's required according to the handouts.

d. Sample Commands
	
	Here is the recommended procedure to test the code.
	First, it is very important to start every client in order. For example, with the sample topology (non-trivial), you should start the client with port 5000 all the way to the client with port 5004 in order. Notice, you need to chenge the IP address in the config files. Below is a diagram for that topology.

	A 5000 ---------------9---------------- B 5001
	|											|
	|											|
	|											|
	|											|
	|											|         E 5004
	|											|         /
	1											3        /
	|											|       /
	|											|      2
	|											|     /
	|											|    /
	|											|   /
	|											|  /
	D 5003 ---------------6---------------- C 5002

	Letters represent the name of the config files. The 500X is port number. Weights are shown on the link. Personally I feel it's easier to open 5 tabs on a terminal. Initialize each node by java bfclient x (x = A, B, C, D, E).

	Configuration files A through D has two chunks to send. A has xaa; B has xab. If you send the file, for example to 5001 (B), then typing TRANSFER in A tests TRANSFER between neighbor; doing so in B tests TRANSFER between non-neighbors. You will see a file named output. Change its extension to png, you will see a cute picture.

	After it converges, at A's terminal, you can type LINKDOWN XXX.XXX.XXX.XXX 5003 to break link between A and D. 

	After it converges again, you can type SHOWRT to check the routing table for each node. Then you can type (at A) LINKUP XXX.XXX.XXX.XXX 5003 1. It will bring up the link.

	You can then test CLOSE. For example, issue CLOSE on D closes D. After 3*timeout, the other neighbors will discover the disappearance of D and the whole network will converge again after a short time.

	You can then run node D again (run command: java bfclient D) to test. The neighbors will find D after a short time.

	ATTENTION: the timeout (5 seconds) is best for this topology, if you test it with much more nodes, please increase the timeout accordingly, otherwise too many nodes sending distance vectors may make it difficult for the node to perform other functionalities. The reason is that I didn't use serializeble, if you send a file to a node while many distance vectors are queued by selector(), the file is mixed in a lot of other data.

e. Additional Functionalities
	
	Extra credit A
	You can send a message to a specific node by:
	PRIVATE receiverIp:receiverPort <message you want to send>
	e.g. PRIVATE 128.59.112.12:5001 Hello world

	Extra credit B
	You can send a broadcast message to everyone by:
	BROADCAST <message you want to send>
	e.g. BROADCAST Hello world

	Please DO NOT add special symbols such as ^ # as they are used in the protocal to divide messages.

f. Protocal Formats

	Distance Vector

	*vIP:vPort ip:port float ip:port float... ip:port float#
	* indicates the begining of the message; # denotes the ending.
	vIP is the IP of the neighbor.
	vPort is the port of the neighbor.
	float is the minimum cost (for the neighbor vIP:vPort).



	File Transfer

	The header must be 3K long. The maximun size for file is 60K. Therefore, the total size of the file transfer message at most can be 63K (which is almost the limit of UDP protocal). The following is the format.

	%size filename sequence_number destIp:destPort ip:port time ip:port time ip:port ...#DATA
	% denotes the begining of the header. # denotes the end of the header.



	Linkup Message

	$myId target cost#

	$ denotes the begining; # denotes the ending. myId is myIp:myPort (e.g. 128.59.21.33:5002). You want to break the link between you and target. target also in format ip:port.



	Linkdown Message

	@myId target#
	Similar to linkup.



	Private/Broadcast Message

	^myId message#

Thank you for grading, and have a great summer!

Qingxiang Jia

May 10, 2014 (within the remaining late days)