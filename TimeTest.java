import java.text.SimpleDateFormat;
import java.util.Date;


public class TimeTest 
{
	public static void main(String[] argv)
	{
		Date date= new Date();
		SimpleDateFormat formatted = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
		System.out.println(formatted.format(date));
        System.out.println(date.getTime());
        long currTime = date.getTime();
        System.out.println(formatted.format(new Date(currTime*1000)));
	}
}
