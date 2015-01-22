
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.InetSocketAddress;
//import java.net.StandardSocketOptions; For Java 7
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;

// TODO: It seems to converge properly. 
// Linkdown seems to work properly.
// Linkup seems to work properly.
// File transfer seems to work properly.
// Poisoned reverse seems to work properly.
// Close and come back to alive seem to work properly.

// Neighbor = Direct neighbor.
public class bfclient 
{
	/**
	 * Non-client/server Part
	 */
	// Define INFINITY.
	final float INFINITY = 9999f;
	// Timeout
	int timeout;
	// My port number for UDP
	int port;
	// My DV is a hashmap, having minCost and nextHop to all y in N.
	HashMap<String, MyDVentry> myDV;
	// My neighbor's DV is a hashmap of hashmap.
	HashMap<String, HashMap<String, Float>> neighborDVs;
	// Cost to my neighbors has all cost to my neighbors.
	HashMap<String, Float> costToNeighbors;
	// Timestamp for latest reception of DV from a neighbor v
	HashMap<String, Long> timestampNeighbors;
	// My neighbor list (I am in there too.)
	ArrayList<String> neighborList;
	// My id is myip:myport
	String myId;
	// Name of the file to send
	String sendFileName;
	// Sequence number of the file to send
	int sequenceNumber;
	// The buffer that holds the file to send.
	ByteBuffer fileBuffer;
	// The buffer that temporarily holds incoming file.
	ByteBuffer cachedFileBuffer;
	// Stored files (to be merged).
	HashMap<Integer, ByteBuffer> storedFileBuffer;
	// Used for DelayedSendMyDV
	Timer timer;
	// Used for CheckLinkdown
	Timer timerLinkdown;
	// A flag tells me that I need to recompute myDV
	boolean needToSendMyDV;
	// A flag to tell if I have been contacted at least once
	boolean isContacted;
	// A flag to stop the thread that listens to user keyboard input
	boolean listenToKeyboard;
	
	// When receive a DV from neighbor, parse it into a HashMap<String, Float>,
	// and call this method. It will add this vDV to neighborDVs and call
	// recomputeMyDV().
	// vDV is the parsed out DV, v is ip:port identifying who (v) sends this DV.
	public void digestIncomingDV(HashMap<String, Float> vDV, String v, boolean recomputeNow)
	{// I need to add this vDV to neighborDVs.
		// Before I add, let me check if there is any node I have not yet have in myDV.
		isContacted = true;
		Iterator<String> iter = vDV.keySet().iterator();
		while(iter.hasNext())
		{// Use an iterator to enumerate vDV. 
			String key = iter.next();
			if(!myDV.containsKey(key)) // If I do not have it in myDV...
			{// Add it. I know the cost (may not be optimal), I know the next hop (may not be optimal).
				myDV.put(key, new MyDVentry(costToNeighbors.get(v), v));
			}
		}
		// Does not matter if I have this previously, I will replace it with this one.
		neighborDVs.put(v, vDV);
		// Update timeout for this neighbor v.
		Date currTime = new Date();
		timestampNeighbors.put(v, currTime.getTime());
		if(!neighborList.contains(v)) // In case of new neighbor.
			neighborList.add(v);
		// Now I need to recompute myDV. Notice, if I have many incoming DVs, I may want to pass
		// all of them first, then do one recomputing. So, I specify this in recomputeNow.
		if(recomputeNow)
			recomputeMyDV();
	}
	
	// When I detect a link cost change, I call this method.
	// newCost is the new cost on link me->v. v is ip:port.
	public void digestLinkCostChange(Float newCost, String v)
	{// I need to update/add an entry in costToNeighbors, and call recomputeMyDV() to update myDV.
		costToNeighbors.put(v, newCost);
		recomputeMyDV();
	}
	
	// When I receive a linkdown message, that means a neighbor has linked me down, so will I.
	// Set in costToNeighbors this neighbor's cost to INFINITY. Update myDV.
	// But keep this neighbor in neighborList.
	/*
	 * Format @ip:port myIp:myPort#
	 */
	public void digestLinkdown(String linkdownMsg)
	{// Designed to be able to handle multiple linkdown messages together.
		String[] messages = linkdownMsg.split("\\@|\\#");
		for(String message: messages)
		{
			String[] msgComponents = message.split("\\s+");
			String targetId = msgComponents[0];
			costToNeighbors.put(targetId, INFINITY);
			/**log("me to"+targetId+" is "+costToNeighbors.get(targetId));**/
			Iterator<String> imyDV = myDV.keySet().iterator();
			while(imyDV.hasNext())
			{
				String y = imyDV.next();
				if(myDV.get(y).nextHop.equals(targetId))
				{
					myDV.get(y).nextHop = null;
					myDV.get(y).minCost = INFINITY;
				}
			}
		}
		recomputeMyDV();
		// Since myDV has been changed, we need to send out myDV. myDV will be recomputed when I 
		// get DVs from neighbors.
		needToSendMyDV = true;
	}
	
	// When I receive a linkup message, I also need to recover the link from my side.
	/*
	 * Format $ip:port myIp:myPort cost#
	 */
	public void digestLinkup(String linkupMsg)
	{
		// Designed to be able to handle multiple linkdown messages together.
		String[] messages = linkupMsg.split("\\$|\\#");
		for(String message: messages)
		{// Somehow the splitting line creates empty strings for "$" and "#". But with the same line,
			if(!message.equals("")) // there is no empty strings in digestLinkdown.
			{
				/**log(message);**/
				String[] msgComponents = message.split("\\s+");
				String targetId = msgComponents[0];
				float cost = Float.parseFloat(msgComponents[2]);
				costToNeighbors.put(targetId, cost);
				/**log("The cost from me to "+targetId+" is "+costToNeighbors.get(targetId));**/
			}
		}
		recomputeMyDV(); // Recompute myDV and then send it to neighbors.
		// Since myDV has been changed, we need to send out myDV. myDV will be recomputed when I 
		// get DVs from neighbors.
		needToSendMyDV = true;
	}
	
	// Deal with incoming message.
	public void digestChat(String chatMsg)
	{
		String[] messages = chatMsg.split("\\^|\\#");
		for(String message: messages)
		{
			if(!message.equals(""))
			{
				log(message);
			}
		}
	}
	
	// Either I detect a link cost changes, or I receive a new DV, I need to call this
	// method.
	public void recomputeMyDV()
	{// First get an iterator that gives me the entry of myDV one by one.
		Iterator<Entry<String, MyDVentry>> imyDV = myDV.entrySet().iterator();
		boolean isMyDVchanged = false;
		while(imyDV.hasNext())
		{// For each y in N that is not myself...
			Map.Entry<String, MyDVentry> entry = imyDV.next();
			String y = entry.getKey(); // y in N.
			if(!y.equals(myId))
			{
				String oldNextHop = entry.getValue().nextHop; // So I can compare at the end.
				Float oldMinCost = entry.getValue().minCost; // So I can compare at the end.
				// Initialize nextHop.
				String nextHop = entry.getValue().nextHop;
				// Initialize minCost to be negative.
				Float minCost = -1f;
				// At the beginning I may not have all DVs of my neighbors, so use neighborDVs to dominate the loop.
				Iterator<String> neighborIter = neighborDVs.keySet().iterator();
				/**log("\nFind path: me->"+y);**/
				while(neighborIter.hasNext())
				{// Textbook p.373, picking min among all c(x,v)+Dv(y).
					String v = neighborIter.next();
					/**log("c(me->"+v+": "+costToNeighbors.get(v)+")+D"+v+"->"+y+"("+":"+neighborDVs.get(v).get(y)+")");
					if(neighborDVs.get(v).get(y) != null) // Make sure Dv->y exists.
						log("The above result="+(costToNeighbors.get(v) + neighborDVs.get(v).get(y)));
					log("Priori to above calculation, minCost of "+myId+"->"+y+" is "+minCost+" via next hop "+nextHop);**/
					if(neighborDVs.get(v).get(y) == null); // My neighbor does not know this node.
					else if(minCost == -1f || costToNeighbors.get(v) + neighborDVs.get(v).get(y) < minCost)
					{// -1f indicates this is the first round, or no nodes from previous round knew this y.
						minCost = costToNeighbors.get(v) + neighborDVs.get(v).get(y);
						nextHop = v;
					}
				}
				// Check if y is my neighbor and if that cost to y is less than the calculated cost.
				if(neighborList.contains(y) && costToNeighbors.get(y) < minCost)
				{
					minCost = costToNeighbors.get(y);
					nextHop = y;
				}// I have found the new minCost and new nextHop, if they differ from old ones, I update myDV.
				if(nextHop == null) // This node is newly known, but currently unreachable.
				{
					myDV.put(y, new MyDVentry(INFINITY, null));
					/**log("\n\nnull added\n");
					log("y="+y);
					log(""+myDV.size());**/
					isMyDVchanged = true;
				}
				else if(oldNextHop == null) // If we now know the next hop now, update.
				{
					myDV.put(y, new MyDVentry(minCost, nextHop));
					isMyDVchanged = true;
				}
				else if(oldNextHop.equals(nextHop) && oldMinCost.compareTo(minCost)==0); // Nothing to update.s
				else // If they are not the same, I should update myDV and send the new myDV to my neighbors.
				{
					myDV.put(y, new MyDVentry(minCost, nextHop));
					isMyDVchanged = true;
				}
			}// If myDV has changed, then I need to send it to my neighbors.
		}
		if(isMyDVchanged)
			needToSendMyDV = true;
	}
	
	class UserInteraction implements Runnable
	{
		Scanner userInput;
		@Override
		public void run() 
		{
			String cmd;
			while(listenToKeyboard)
			{
				userInput = new Scanner(System.in);
				cmd = userInput.nextLine();
				if(cmd.equalsIgnoreCase("showrt")) // Not case sensitive
				{
					// Call showrt().
					showrt();
				}
				else if(cmd.toLowerCase().startsWith("linkdown")) // Not case sensitive
				{
					String ip = cmd.split("\\s+")[1];
					String port = cmd.split("\\s+")[2];
					String linkdownTarget = ip+":"+port;
					linkdown(linkdownTarget); // Call linkdown().
				}
				else if(cmd.toLowerCase().startsWith("linkup")) // Not case sensitive
				{
					String ip = cmd.split("\\s+")[1];
					String port = cmd.split("\\s+")[2];
					float cost = Float.parseFloat(cmd.split("\\s+")[3]);
					linkup(ip, port, cost);
				}
				else if(cmd.toLowerCase().startsWith("transfer"))
				{
					String ip = cmd.split("\\s+")[1];
					String port = cmd.split("\\s+")[2];
					sendFile(ip+":"+port);
				}
				else if(cmd.toLowerCase().startsWith("private")) // Usage: private ip:port msg
				{
					String id = cmd.split("\\s+", 3)[1];
					String msg = "^"+myId+" said to you: "+cmd.split("\\s+", 3)[2]+"#";
					sendPrivate(id, msg);
				}
				else if(cmd.toLowerCase().startsWith("broadcast")) // Usage: broadcast msg
				{
					String msg = "^"+myId+" said to everyone: "+cmd.split("\\s+", 2)[1]+"#";
					sendBroadcast(msg);
				}
				else if(cmd.equalsIgnoreCase("close"))
				{
					close();
				}
			}
		}
	}
	
	// Show route == traversing myDV.
	public void showrt()
	{
		Iterator<String> imyDV = myDV.keySet().iterator();
		Date currTime = new Date(); // Get current time.
		SimpleDateFormat formatted = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
		System.out.printf("%s Distance vector list is:\n", formatted.format(currTime));
		while(imyDV.hasNext())
		{// y in N
			String y = imyDV.next();
			String nextHop = myDV.get(y).nextHop;
			Float minCost = myDV.get(y).minCost;
			System.out.printf("Destination = %s, Cost = %.1f, Link = (%s)\n", y, minCost, nextHop);
		}
	}
	
	// Link down == set v's minCost to INFINITY.
	public void linkdown(String linkdownTarget)
	{// Target must be a neighbor...
		if(neighborList.contains(linkdownTarget) && costToNeighbors.get(linkdownTarget) < INFINITY)
		{
			costToNeighbors.put(linkdownTarget, INFINITY);
			if(myDV.containsKey(linkdownTarget) && myDV.get(linkdownTarget).minCost < INFINITY)
			{
				Iterator<String> imyDV = myDV.keySet().iterator();
				while(imyDV.hasNext())
				{// If next hop is this linked-down neighbor, nextHop is null, and minCost is INFINITY.
					String y = imyDV.next();
					if(myDV.get(y).nextHop.equals(linkdownTarget))
					{
						myDV.get(y).nextHop = null;
						myDV.get(y).minCost = INFINITY;
					}
				}
				recomputeMyDV();
				needToSendMyDV = true; // Since myDV is changed. (?I will recompute when I receive others' DV.)
				sendLinkdown(linkdownTarget); // Send linkdown message now.
			}
		}
	}
	
	// Link up == set y's minCost to given value. If y is also v, set costToNeighbors.get(y) to given value.
	public void linkup(String ip, String port, float cost)
	{
		// Synthesize the ID for the target.
		String id = ip+":"+port;
		costToNeighbors.put(id, cost); // Cost to neighbors is updated.
		log("link between me and "+id+" is recovered with cost "+cost);
		recomputeMyDV(); // Recompute myDV.
		sendLinkup(id, cost); // Send linkup message immediately.
		needToSendMyDV = true; // Since myDV is changed. I will recompute when I receive others' DV.
	}
	
	// Read the file into the buffer so later I can send it.
	public void readFileToBuffer(String path)
	{
		RandomAccessFile file;
		try 
		{
			file = new RandomAccessFile(path, "r");
			FileChannel readInFile = file.getChannel();
	        while(readInFile.read(fileBuffer) > 0)
	        {
	            fileBuffer.flip();
	            for (int i = 0; i < fileBuffer.limit(); i++)
	            	fileBuffer.get();
	        }
	        readInFile.close();
            file.close();
		} 
		catch(FileNotFoundException e) 
		{
			handleException("FileNotFoundException", e);
		}
		catch(IOException ioe)
		{
			handleException("IOException", ioe);
		}
	}
	
	// Close: close DatagramChannel snd and rcv.
	public void close()
	{
		try 
		{// Stopping all scheduled tasks.
			timer.cancel();
			timer.purge();
			snd.close();
			rcv.close();
			System.exit(0);
		} 
		catch (IOException e) 
		{
			handleException("IOException", e);
		}
	}
	
	// For the initial send of myDV
	class DelayedSendMyDV extends TimerTask
	{
		@Override
		public void run() 
		{
//			if(needToSendMyDV) // TODO: Comment this out after testing.
//			{
				if(needToSendMyDV)
					log("Converging... When converged, this will stop being printed out.");
				for(String v: neighborList)
				{
					if(costToNeighbors.get(v) != INFINITY) // In case a neighbor has linked down.
					{
						if(!needToSendMyDV);
							//log("Network converged, routine ROUTE UPDATE");
						if(!v.equals(myId)) // Do not send it to myself.
							sendMyDV(v); // Send via UDP. v is the receiver.
					}
				}
//			}
			needToSendMyDV = false;
		}
	}
	
	// After 2*timeout seconds, if I do not get any information from my neighbor, I know I must previously
	// be closed and now they think I am dead. So I need to send linkup message to all my neighbors.
	class TellBackToAlive extends TimerTask
	{
		@Override
		public void run() 
		{
			Iterator<String> imyDV = myDV.keySet().iterator();
			while(imyDV.hasNext())
			{
				String v = imyDV.next();
				sendLinkup(v, myDV.get(v).minCost);
			}
		}
	}
	
	// Periodically checks a neighbor's timestamp to see if it is linked down.
	class CheckLinkdown extends TimerTask
	{
		@Override
		public void run() 
		{
			Date currentDate = new Date();
			Iterator<String> iter = timestampNeighbors.keySet().iterator();
			while(iter.hasNext())
			{
				String v = iter.next();
				if(costToNeighbors.get(v) < INFINITY && 
						currentDate.getTime() - timestampNeighbors.get(v) > timeout*3*1000)
				{
					linkdown(v); // It is OK to send the message, let it.
					log("Neighbor "+v+" has been inactive for 3*timeout seconds, linkdown("+v+").");
				}
			}
		}
	}
	
	// Send via UDP.
	public void sendMyDV(String receiverId)
	{
		String myDV = myDVtoString(receiverId);
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		buffer.clear(); // Zero out the buffer.
		buffer.put(myDV.getBytes()); // Copy myDV to buffer.
		buffer.flip();
		try 
		{
			snd.send(buffer, new InetSocketAddress(getIp(receiverId), getPort(receiverId)));
			/**log("\n\nsend DV to "+receiverId+"\nDV contents: "+myDV);**/
		} 
		catch (IOException e) 
		{
			handleException("IOException", e);
		}
	}
	
	// Send private message to a node.
	public void sendPrivate(String receiverId, String msg)
	{
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		buffer.clear(); // Zero out the buffer.
		buffer.put(msg.getBytes()); // Copy myDV to buffer.
		buffer.flip();
		try 
		{
			snd.send(buffer, new InetSocketAddress(getIp(receiverId), getPort(receiverId)));
		} 
		catch (IOException e) 
		{
			handleException("IOException", e);
		}
	}
	
	// Send broadcast (to all network, not just neighbors).
	public void sendBroadcast(String msg)
	{
		Iterator<String> imyDV = myDV.keySet().iterator();
		while(imyDV.hasNext())
		{
			String y = imyDV.next();
			if(!y.equals(myId) && myDV.get(y).minCost < INFINITY)
				sendPrivate(y, msg);
		}
	}
	
	// Send linkdown message via UDP.
	public void sendLinkdown(String target)
	{
		String linkdownMsg = linkdownMsgGenerator(target);
		ByteBuffer buffer = ByteBuffer.allocate(256);
		buffer.clear(); // Zero out the buffer.
		buffer.put(linkdownMsg.getBytes()); // Copy myDV to buffer.
		buffer.flip();
		try 
		{
			snd.send(buffer, new InetSocketAddress(getIp(target), getPort(target)));
			/**log("\n\nsend linkdown message to "+target+"\n msg content: "+linkdownMsg);**/
		} 
		catch (IOException e) 
		{
			handleException("IOException", e);
		}
	}
	
	// Send a file. Header is 3KB, file is at most 60KB.
	// Time is in seconds, type long.
	// First read header into a 3KB buffer, then read the file into a buffer smaller than 60KB,
	// then read the 3KB and file buffer into a new buffer. When decode, just read the first
	// 3KB, must be the header with a # as terminator, then, read size many of the rest of buffer.
	/*                                                                      // Next hop
	 * Format: %size filename sequence_number destIp:destPort ip:port time ip:port time ip:port ...#DATA
	 */
	public void sendFile(String destination)
	{// Prepare buffer to send.
		int chunkSize = fileBuffer.limit();
		// Find the next hop and generate the header
		String vId = myDV.get(destination).nextHop; // vId, neighbor's id, also nextHop.
		Date currTime = new Date();
		String header = "%"+chunkSize+" "+sendFileName+" "+sequenceNumber+" "+destination+
				" "+myId+" "+currTime.getTime()+" "+vId+"#";
		// Put header into buffer.
		ByteBuffer headerBuffer = ByteBuffer.allocate(1024*3);
		headerBuffer.clear();
		headerBuffer.put(header.getBytes());
		// Fill the rest of headerBuffer.
		for(int pos = headerBuffer.position(); pos < headerBuffer.capacity(); pos++)
			headerBuffer.put((byte)3);
		headerBuffer.flip(); // Switch to reading mode.
		// Copy headerBuffer to sending buffer.
		ByteBuffer headerAndData = ByteBuffer.allocate(1024*3+chunkSize);
		for(int pos = 0; pos < headerBuffer.limit(); pos++) // Copy header into sending buffer to send.
			headerAndData.put(headerBuffer.get());
		fileBuffer.flip();
		headerAndData.put(fileBuffer);
		headerAndData.flip();
		try 
		{// Send to next hop.
			snd.send(headerAndData, new InetSocketAddress(getIp(vId), getPort(vId)));
			log("\n\n"+sendFileName+" chunk "+sequenceNumber+" to next hop "+vId);
		} 
		catch (IOException e) 
		{
			handleException("IOException", e);
		}
	}
	
	// If I am not the sender, I need to relay this file to some other nodes.
	// Read the header, modify the header, find the next hop, and send the file to it.
	/*
	 * Format: %size filename sequence_number destIp:destPort ip:port time ip:port time ip:port ...#DATA
	 */
	public void relayFile(ByteBuffer headerAndData)
	{
		ByteBuffer headerBuffer = ByteBuffer.allocate(3*1024);
		// Copy the first 3KB of headerAndData into headerBuffer.
		for(int pos = 0; pos < headerBuffer.capacity(); pos++)
			headerBuffer.put(headerAndData.get());
		ByteBuffer dataBuffer = ByteBuffer.allocate(headerAndData.capacity() - 3*1024);
		// Copy the rest of data (actual file) into dataBuffer.
		for(int pos = headerAndData.position(); pos < headerAndData.capacity(); pos++)
			dataBuffer.put(headerAndData.get());
		// Extract header string from headerBuffer.
		headerBuffer.flip(); // Switch to read mode.
		byte[] headerArray = new byte[headerBuffer.limit()];
		headerBuffer.get(headerArray, 0, headerBuffer.limit());
		String rawHeader = new String(headerArray);
		String refinedHeader = rawHeader.split("\\#")[0]; // So refinedHeader does not have # ending.
		String headerComponents[] = refinedHeader.split("\\s+");
		/**for(String s: headerComponents)
			log(s);**/
		String destId = headerComponents[3];
		String nextHop = myDV.get(destId).nextHop;
		Date currTime = new Date();
		String newHeader = refinedHeader+" "+currTime.getTime()+" "+nextHop+"#";
		// Generate header buffer.
		ByteBuffer newHeaderBuffer = ByteBuffer.allocate(1024*3);
		newHeaderBuffer.clear();
		newHeaderBuffer.put(newHeader.getBytes());
		// Fill up the header buffer.
		for(int pos = newHeaderBuffer.position(); pos < newHeaderBuffer.capacity(); pos++)
			newHeaderBuffer.put((byte)3);
		newHeaderBuffer.flip(); // Switch to reading mode.
		// Combine newHeaderBuffer and dataBuffer.
		dataBuffer.flip();
		ByteBuffer sendingBuffer = ByteBuffer.allocate(1024*3+dataBuffer.limit());
		/**log("SendingBuffer limit "+sendingBuffer.limit());
		log("SendingBuffer capacity "+sendingBuffer.capacity());**/
		sendingBuffer.clear();
		sendingBuffer.put(newHeaderBuffer);
		sendingBuffer.put(dataBuffer);
		sendingBuffer.flip();
		// Send sendingBuffer.
		try 
		{// Send to next hop.
			snd.send(sendingBuffer, new InetSocketAddress(getIp(nextHop), getPort(nextHop)));
			log("\n\n"+headerComponents[1]+" chunk "+headerComponents[2]+" to next hop "+nextHop);
		} 
		catch (IOException e) 
		{
			handleException("IOException", e);
		}
	}
	
	// When this file is for me...
	/*
	 * Format: %size filename sequence_number destIp:destPort ip:port time ip:port time ip:port ...#DATA
	 */
	public void receiveFile(ByteBuffer headerAndData)
	{
		ByteBuffer headerBuffer = ByteBuffer.allocate(3*1024);
		// Copy the first 3KB of headerAndData into headerBuffer.
		for(int pos = 0; pos < headerBuffer.limit(); pos++)
			headerBuffer.put(headerAndData.get());
		ByteBuffer dataBuffer = ByteBuffer.allocate(headerAndData.capacity() - 3*1024);
		// Copy the rest of data (actual file) into dataBuffer.
		for(int pos = headerAndData.position(); pos < headerAndData.capacity(); pos++)
			dataBuffer.put(headerAndData.get(pos));
		// Extract header string from headerBuffer.
		headerBuffer.flip(); // Switch to read mode.
		byte[] headerArray = new byte[headerBuffer.limit()];
		headerBuffer.get(headerArray, 0, headerBuffer.limit());
		String rawHeader = new String(headerArray);
		String refinedHeader = rawHeader.split("\\#|\\%")[1]; // So refinedHeader does not have # ending.
		String[] headerComponents = refinedHeader.split("\\s+");
		// Print out size, nodes traversed, and timestamp.
		SimpleDateFormat formater = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
		log("Chunk size: "+headerComponents[0]+" bytes");
		log("Path traversed by this chunk: ");
		int i = 4;
		while(i < headerComponents.length-2)
		{
			long timestamp = Long.parseLong(headerComponents[i+1]);
			String formatedTimestamp = formater.format(new Date(timestamp));
			log(headerComponents[i]+" on "+formatedTimestamp);
			i+=2; // Step size == 2.
		}
		// Including myself
		log(myId+" on "+formater.format(new Date().getTime()));
		int sequenceNumber = Integer.parseInt(headerComponents[2]);
		dataBuffer.flip();
		storedFileBuffer.put(sequenceNumber, dataBuffer);
		// Decide if I need to merge the 2 files.
		if(storedFileBuffer.get(1) != null && storedFileBuffer.get(2) != null)
			mergeFile(2);
	}
	
	// Write the merged data into file named output.
	public void mergeFile(int numOfFileToMerge)
	{
		Iterator<Integer> iter = storedFileBuffer.keySet().iterator();
		int size = 0;
		int i = 0;
		while(iter.hasNext() && i < numOfFileToMerge) // Calculating size of the dataToFile buffer.
		{
			Integer sequenceNumber = iter.next();
			i++;
			/**log(""+i);**/
			size += storedFileBuffer.get(sequenceNumber).limit();
		}
		ByteBuffer dataToFile = ByteBuffer.allocate(size);
		log("size: "+size);
		i = 0;
		iter = storedFileBuffer.keySet().iterator(); // So iter will go back to the begining.
		while(iter.hasNext() && i < numOfFileToMerge) // Copy data into dataToFile.
		{
			Integer sequenceNumber = iter.next();
			i++;
			/**log(""+i);**/
			dataToFile.put(storedFileBuffer.get(sequenceNumber));
		}
		File file;
		FileChannel fileChannel;
		try // Write dataToFile to file.
		{
			file = new File("output");               // Not appending
			fileChannel = new FileOutputStream(file, false).getChannel();
			dataToFile.flip();
			/**log("dataToFile limit: "+dataToFile.limit());
			log("dataToFile pos: "+dataToFile.position());**/
			fileChannel.write(dataToFile);
			fileChannel.close();
		}
		catch(IOException e) 
		{
			handleException("IOException", e);
		}
		log("Merged. Find the file named \\'output\\' in this directory.");
	}
	
	// Generate legal linkdown message.
	/*
	 * Format @myIp:myPort ip:port#
	 */
	public String linkdownMsgGenerator(String target)
	{
		return "@"+myId+" "+target+"#";
	}
	
	// Send linkup message via UDP to the target to notify it to recover the link mutually.
	/*
	 * Format $myIp:myPort ip:port cost#
	 */
	public void sendLinkup(String target, float cost)
	{
		String linkupMsg = linkupMsgGenerator(target, cost);
		ByteBuffer buffer = ByteBuffer.allocate(256);
		buffer.clear(); // Zero out the buffer.
		buffer.put(linkupMsg.getBytes()); // Copy myDV to buffer.
		buffer.flip();
		try 
		{
			snd.send(buffer, new InetSocketAddress(getIp(target), getPort(target)));
			/**log("\n\nsend linkup message to "+target+"\n msg content: "+linkupMsg);**/
		} 
		catch (IOException e) 
		{
			handleException("IOException", e);
		}
	}
	
	// Generate legal linkup message.
	public String linkupMsgGenerator(String target, float cost)
	{
		return "$"+myId+" "+target+" "+cost+"#";
	}
	
	// Parse the config-file, and initialize variables (Still need constructor to initialize memory).
	public void cfgParser(String cfgPath)
	{
		log("Config file name: "+cfgPath);
		BufferedReader reader;
		String line = null;
		try 
		{
			reader = new BufferedReader(new FileReader(cfgPath));
			// Parse the first line.
			line = reader.readLine();
			String[] firstLine = line.split("\\s+"); // Split the first line by space.
			String[] lineContents;
			if(firstLine.length == 4) // Have file to send.
			{
				myId = getMyIp()+":"+firstLine[0]; // Initialize myId.
				log("myId: "+myId);
				port = Integer.parseInt(firstLine[0]); // Set local port.
				log("port: "+port);
				timeout = Integer.parseInt(firstLine[1]); // Initialize timeout.
				log("timeout: "+timeout);
				sendFileName = firstLine[2]; // Initialize sendFileName.
				sequenceNumber = Integer.parseInt(firstLine[3]); // Initialize sequenceNumber.
				fileBuffer = ByteBuffer.allocate(61440); // File cannot be larger than 60KB.
				readFileToBuffer(sendFileName); // Read file into buffer.
			}
			else // No file to send, regular client
			{
				myId = getMyIp()+":"+firstLine[0]; // Initialize myId.
				log("myId: "+myId);
				port = Integer.parseInt(firstLine[0]); // Set local port.
				log("port: "+port);
				timeout = Integer.parseInt(firstLine[1]); // Initialize timeout.
				log("timeout: "+timeout);
			}
			neighborList.add(myId); // Add myself as a neighbor.
			costToNeighbors.put(myId, 0f);
			// Parse the rest of lines.
			String v;
			Float cost;
			while ((line = reader.readLine()) != null) 
			{
				lineContents = line.split("\\s+"); // Split by space.
				v = lineContents[0]; // Id of the neighbor: ip:port
				cost = Float.parseFloat(lineContents[1]); // Cost to that neighbor
				costToNeighbors.put(v, cost); // Add an entry in costToNeighbors.
				neighborList.add(v); // Add an entry in neighborList.
				log("neighbor: "+v+" cost: "+cost);
			}
		} 
		catch(FileNotFoundException e){ handleException("FileNotFoundException", e); }
		catch(IOException e){ handleException("IOException", e); }
	}
	
	// Initialize myDV (Still need constructor to initialize memory).
	// Call after cfgParser i.e. after costToNeighbors is initialized.
	public void initializeMyDV()
	{
		// Add myself into myDV.
		myDV.put(myId, new MyDVentry(0f, myId));
		// An iterator for costToNeighbors
		Iterator<Entry<String, Float>> iter = costToNeighbors.entrySet().iterator();
		while(iter.hasNext())
		{// Initialize myDV with the cost to my neighbor.
			Entry<String, Float> entry = iter.next();
			String v = entry.getKey();
			Float minCost = entry.getValue();
			myDV.put(v, new MyDVentry(minCost, v)); // Initially, nextHop is the neighbor.
		}
	}
	
	// Convert myDV into String so that sendMyDV can send it via UDP.
	// receiver (ID) is used to implement poisoned reverse.
	/*
	 * DV format: *vIP:vPort ip:port float ip:port float... ip:port float#
	 */
	public String myDVtoString(String receiver)
	{// Use StringBuilder for performance concerns.
		StringBuilder myDVstring = new StringBuilder(400);
		// Append "*myId".
		myDVstring.append("*"+myId);
		// An iterator for myDV
		Iterator<Entry<String, MyDVentry>> iter = myDV.entrySet().iterator();
		String y; // y in N
		Float minCost; // me->y
		while(iter.hasNext()) // Enumerating myDV
		{
			Entry<String, MyDVentry> entry = iter.next();
			y = entry.getKey();
			if(entry.getValue().nextHop.equals(receiver)) // Poisoned reverse (p.377 textbook)
				minCost = INFINITY;
			else
				minCost = entry.getValue().minCost;
//			minCost = entry.getValue().minCost; // Disable poisoned reverse.
			myDVstring.append(" "+y+" "+minCost); // Append one node onto myDVstring.
		}
		myDVstring.append("#"); // Append ending character
		return myDVstring.toString();
	}
	
	
	// Generate DV(s) (HashMap<String, Float>) based on incomingMsg (String).
	// Cannot handle DVs and then portion of file.
	public void strToDV(String incomingMsg)
	{
		HashMap<String, Float> DV; // An arraylist of DVs to return
		String v; // ID of the neighbor
		String y; // ID of the node y in N
		Float minCost; // minCost v->y
		String[] rawDVs = incomingMsg.split("\\*|\\#"); // Now I have one DV in an entry.
		for(String rawDV: rawDVs)
		{
			if(!(rawDV.length() == 0))
			{
				String[] DVparts = rawDV.split("\\s+"); // Split a single DV into its components.
				DV = new HashMap<String, Float>(); // DV to pass to digestIncomingDV()
				v = DVparts[0]; // Neighbor's ID
				int i = 1;
				while(i+1 < DVparts.length)
				{
					y = DVparts[i]; // y in N
					minCost = Float.parseFloat(DVparts[++i]); // minCost of v->y
					DV.put(y, minCost);
					i++;
				}// Now DV is filled up.
				digestIncomingDV(DV, v, true); // Digest this DV but also recompute.
			}
		}
		//recomputeMyDV(); // Now, since a series of DVs are added, recompute myDV.
	}
	
	// On receiving a message, I call this method to identify the type of message i.e. a
	// DV or portion of a file, and this message calls different methods to process the
	// message.
	/*
	 * DV format: *vIP:vPort ip:port float ip:port float... ip:port float#
	 */
	public void incomingMsgDispatcher(String incomingMsg)
	{
		if(incomingMsg.startsWith("*")) // Must be DV.
		{
			/**log("rcv "+incomingMsg);**/
			strToDV(incomingMsg);
		}
		else if(incomingMsg.startsWith("@")) // Must be linkdown message.
		{
			/**log("rcv "+incomingMsg);**/
			digestLinkdown(incomingMsg);
		}
		else if(incomingMsg.startsWith("$")) // Must be linkup message.
		{
			/**log("rcv "+incomingMsg);**/
			digestLinkup(incomingMsg);
		}
		else if(incomingMsg.startsWith("%")) // Must be a file.
		{	/* 
			 * Format: %size filename sequence_number destIp:destPort ip:port time ip:port time ip:port ...#DATA
			 */
			/**log("rcv a file.");**/
			String[] message = incomingMsg.split("\\s+");
			if(message[3].equals(myId)) // I am the destination.
			{
				/**log("Dest is "+message[3]+", so I am the dest.");**/
				receiveFile(cachedFileBuffer);
			}
			else // I need to relay this file.
			{
				/**log("Dest is "+message[3]+", so I am not the dest.");**/
				relayFile(cachedFileBuffer); // Pass the buffer to it.
			}
		}
		else if(incomingMsg.startsWith("^")) // A public/private chat message
		{
			digestChat(incomingMsg);
		}
		else
		{
			//log("Ilegal command, ignored");
			//log(incomingMsg);
		}
	}
	
	// Update a neighbor, v's timestamp.
	// TODO: have not yet been used by any method.
	public void updataTimestamp(String vId)
	{
		Date currentDate = new Date();
		timestampNeighbors.put(vId, currentDate.getTime()); // Time in milliseconds, type long
	}
	
	public String getMyIp()
	{
		try { return InetAddress.getLocalHost().getHostAddress().toString(); } 
		catch(UnknownHostException e){ handleException("UnknownHostException", e); }
		return ""; // dummy return
	}
	
	// Input: ID like ip:port
	// Output: IP
	public String getIp(String Id)
	{
		return Id.split(":")[0];
	}
	
	// Input: ID like ip:port
	// Output: port (int)
	public int getPort(String Id)
	{
		return Integer.parseInt(Id.split(":")[1]);
	}
	
	public void handleException(String eName, Exception e)
	{
		e.printStackTrace();
		log(eName);
		System.exit(1);
	}
	
	public void log(String toPrint)
	{
		System.out.println(toPrint);
	}
	
	public void lg(String toPrint)
	{
		System.out.print(toPrint);
	}
	
	// Used as value of MyDV.
	private class MyDVentry
	{
		Float minCost;
		String nextHop;
		
		public MyDVentry(Float minCost, String nextHop)
		{
			this.minCost = minCost;
			this.nextHop = nextHop;
		}
	}
	
	/**
	 * Non-client/server Part Ends.
	 */
	
	// Constructor
	public bfclient(String cfgPath)
	{
		// Initialize non-client/server part.
		myDV = new HashMap<String, MyDVentry>();
		neighborDVs = new HashMap<String, HashMap<String, Float>>();
		costToNeighbors = new HashMap<String, Float>();
		timestampNeighbors = new HashMap<String, Long>();
		storedFileBuffer = new HashMap<Integer, ByteBuffer>();
		isContacted = false; // Have not yet been contacted.
		neighborList = new ArrayList<String>();
		log("Initialized non-client/server part.");
		cfgParser(cfgPath);
		initializeMyDV();
		//log("initializeMyDV()");
		// Initialize client/server part.
		try 
		{// Open the receiving UDP channel.
			rcv = DatagramChannel.open();
			rcv.configureBlocking(false); // Non-blocking
			rcv.socket().bind(new InetSocketAddress(port)); // Listen on this port.
			selector = Selector.open();
			rcv.register(selector, SelectionKey.OP_READ); // Register receiving channel to selector.
			// Open the sending UDP channel.
			snd = DatagramChannel.open();
			running = true; // Allow this node to run.
			/** Java 1.7
			snd.setOption(StandardSocketOptions.SO_SNDBUF, 1024*63); // Very evilly deeply tricky :(
			rcv.setOption(StandardSocketOptions.SO_RCVBUF, 1024*63); **/
			/** Java 1.6 **/
			snd.socket().setSendBufferSize(1024*63);
			rcv.socket().setSendBufferSize(1024*63);
			udpBuffer = ByteBuffer.allocate(80*1024); // Allocate space for buffer to hold incoming message(s).
			log("All ready, start running...");
			
			// Start a new thread to handle user keyboard input.
			listenToKeyboard = true; // Allow the below thread to run.
			UserInteraction keyboardInput = new UserInteraction(); 
			Thread keyboardInputThread = new Thread(keyboardInput);
			keyboardInputThread.setDaemon(true);
			keyboardInputThread.start();
			
			needToSendMyDV = true; // Set true for initial sending of myDV.
			timer = new Timer();  //At this line a new Thread will be created
			timer.scheduleAtFixedRate(new DelayedSendMyDV(), 0, 1000*timeout); // Periodically send myDV if needed.
			
			// Start a new thread to check dead neighbors.
			timer.scheduleAtFixedRate(new CheckLinkdown(), 3000*timeout, 1000*timeout);
			
			// Start a new thread to tell my existence in case I was dead previously.
			timer.schedule(new TellBackToAlive(), 2*timeout);
			
			start(); // This node starts running.
		} 
		catch (IOException e) 
		{
			handleException("IOException", e);
		}
	}
	
	// Main
	@SuppressWarnings("unused")
	public static void main(String[] argv)
	{
		bfclient bfclient = new bfclient(argv[0]);
	}
	
	/**
	 * Client/Server Part
	 */
	// UDP (listening) channel used to receive data (binded, control by selector)
	DatagramChannel rcv;
	// UDP channel used to send data (not connected, send at any time)
	DatagramChannel snd;
	// Selector
	Selector selector;
	// Flag to stop the node.
	boolean running;
	// Buffer to hold incoming message
	ByteBuffer udpBuffer;
	public void start()
	{
		while(running)
		{
			try 
			{
				int numOfChannelSelected = selector.selectNow();
				// If numOfChannelSelected is 0, no incoming UDP message;
				// if numOfChannelSelected is 1, I have message(s)!
				if(numOfChannelSelected >= 0)
				{
					if(numOfChannelSelected > 0) // I have UDP message(s) to process.
					{
						Iterator<SelectionKey> iter = selector.selectedKeys().iterator();
						while(iter.hasNext())
						{
							SelectionKey key = iter.next();
							iter.remove();
							if(key.isReadable()) // Start reading UDP message(s).
							{// If limit < 3KB, no way to be a file to relay / receive.
								rcv = (DatagramChannel) key.channel();
								rcv.receive(udpBuffer); // Copy incoming message into buffer.
								// Pass this incoming message to dispatcher to let it decide what to do.
								// Process udpBuffer.
								udpBuffer.flip(); // Flip to read mode: exchange position and limit.
								int limit = udpBuffer.limit(); // Get the limit of the buffer.
								//log("limit of udpBuffer is "+limit);
								if(limit >= 3*1024)
								{
									cachedFileBuffer = ByteBuffer.allocate(limit); // Make a copy of the file.
									cachedFileBuffer.clear();
									cachedFileBuffer.put(udpBuffer);
									udpBuffer.flip(); // Make it readable again.
									cachedFileBuffer.flip();
								}
								byte[] trimed = new byte[limit]; // Create an array hold the buffer output.
								udpBuffer.get(trimed, 0, limit); // Copy data from the buffer to the array.
								String incomingMsg = new String(trimed); // Create a string based the array.s
								incomingMsgDispatcher(incomingMsg); // Call dispatcher.
								udpBuffer.clear(); // Clear buffer.
							}
						}
					}
				}
			} 
			catch (IOException e) 
			{
				handleException("IOException", e);
			}
		}
	}
}

