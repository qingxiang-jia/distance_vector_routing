import java.util.Timer;
import java.util.TimerTask;


public class JavaReminder 
{
	Timer timer;

	public JavaReminder(int seconds) 
	{
		timer = new Timer();  //At this line a new Thread will be created
		timer.schedule(new RemindTask(), seconds*1000); //delay in milliseconds
	}
	
	public void simplePrint()
	{
		System.out.println("Hello Hello Hello");
	}

	class RemindTask extends TimerTask 
	{
		@Override
		public void run() 
		{
			System.out.println("ReminderTask is completed by Java timer");
			simplePrint();
			timer.cancel(); //Not necessary because we call System.exit
			//System.exit(0); //Stops the AWT thread (and everything else)
		}
	}

	public static void main(String args[]) 
	{
		System.out.println("Java timer is about to start");
		@SuppressWarnings("unused")
		JavaReminder reminderBeep = new JavaReminder(5);
		System.out.println("Remindertask is scheduled with Java timer.");
	}
}