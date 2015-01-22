import java.util.HashMap;

public class StringTest 
{
	public static void main(String[] args)
	{
		StringTest stringTest = new StringTest();
		stringTest.strToDV("*160.39.237.120:5001 160.39.237.120:5001 0.0 160.39.237.120:5002 1.0 160.39.237.120:5000 2.0#");
	}
		/*	v=
			v=160.39.237.120:5001
			y=160.39.237.120:5001 minCost=0.0
			y=160.39.237.120:5002 minCost=1.0
			y=160.39.237.120:5000 minCost=2.0 */
	public void strToDV(String incomingMsg)
	{
		HashMap<String, Float> DV; // An arraylist of DVs to return
		String v; // ID of the neighbor
		String y; // ID of the node y in N
		Float minCost; // minCost v->y
		String[] rawDVs = incomingMsg.split("\\*|\\#"); // Now I have one DV in an entry.
		for(String rawDV: rawDVs)
		{
			if(!rawDV.equals(""))
			{
				String[] DVparts = rawDV.split("\\s+"); // Split a single DV into its components.
				DV = new HashMap<String, Float>(); // DV to pass to digestIncomingDV()
				v = DVparts[0]; // Neighbor's ID
				int i = 1;
				log("v="+v);
				while(i+1 < DVparts.length)
				{
					y = DVparts[i]; // y in N
					minCost = Float.parseFloat(DVparts[++i]); // minCost of v->y
					DV.put(y, minCost);
					log("y="+y+" minCost="+minCost);
					i++;
				}// Now DV is filled up.
			}
		}
	}
	
	public void log(String toPrint)
	{
		System.out.println(toPrint);
	}
	
	public void lg(String toPrint)
	{
		System.out.print(toPrint);
	}
}