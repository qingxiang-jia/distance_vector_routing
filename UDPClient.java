
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.Charset;
import java.util.Iterator;

/**
 * 客户端
 * 
 * @author chenxuegui
 */
public class UDPClient
{
	DatagramChannel channel;
	Selector selector;

	@SuppressWarnings("rawtypes")
	public void work()
	{
		try
		{
			// 开启一个通道
			channel = DatagramChannel.open();
			channel.configureBlocking(false);
			SocketAddress sa = new InetSocketAddress("localhost", 8080);
			channel.connect(sa);
		} catch (Exception e)
		{
			e.printStackTrace();
		}
		try
		{
			selector = Selector.open();
			channel.register(selector, SelectionKey.OP_READ);
			channel.write(Charset.defaultCharset().encode("data come from client"));
		} catch (Exception e)
		{
			e.printStackTrace();
		}
		ByteBuffer byteBuffer = ByteBuffer.allocate(100);
		int count = 0;
		while (true)
		{
			try
			{
				int n = selector.select();
				if (n > 0)
				{
					Iterator iterator = selector.selectedKeys().iterator();
					while (iterator.hasNext())
					{
						SelectionKey key = (SelectionKey) iterator.next();
						iterator.remove();
						if (key.isReadable())
						{
							channel = (DatagramChannel) key.channel();
							channel.read(byteBuffer);
							System.out.println(new String(byteBuffer.array()));
							byteBuffer.clear();
							channel.write(Charset.defaultCharset().encode(
									"data come from client " + count));
						}
					}
				}
			} catch (Exception e)
			{
				e.printStackTrace();
			}
			try 
			{
				Thread.sleep(5000);
			} catch (InterruptedException e) 
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			count++;
		}
	}

	public static void main(String[] args)
	{
		new UDPClient().work();
	}
}