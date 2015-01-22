
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;

/**
 * 服务器端
 * 
 * @author chenxuegui
 * 
 */
public class UDPServer
{
	DatagramChannel channel;
	Selector selector;

	@SuppressWarnings("rawtypes")
	public void work()
	{
		try
		{
			// 打开一个UDP Channel
			channel = DatagramChannel.open();

			// 设定为非阻塞通道
			channel.configureBlocking(false);
			// 绑定端口
			channel.socket().bind(new InetSocketAddress(8080));

			// 打开一个选择器
			selector = Selector.open();
			channel.register(selector, SelectionKey.OP_READ);
		} catch (Exception e)
		{
			e.printStackTrace();
		}

		ByteBuffer byteBuffer = ByteBuffer.allocate(65536);
		int count = 0;
		while (true)
		{
			try
			{
				// 进行选择
				int n = selector.select();
				System.out.println("Number of selected channel: "+n);
				if (n > 0)
				{
					// 获取以选择的键的集合
					Iterator iterator = selector.selectedKeys().iterator();
					
					while (iterator.hasNext())
					{
						SelectionKey key = (SelectionKey) iterator.next();

						// 必须手动删除
						iterator.remove();

						if (key.isReadable())
						{
							DatagramChannel datagramChannel = (DatagramChannel) key
									.channel();

							byteBuffer.clear();
							// 读取
							InetSocketAddress address = (InetSocketAddress) datagramChannel
									.receive(byteBuffer);

							System.out.println(new String(byteBuffer.array()));

							// 删除缓冲区中的数据
							byteBuffer.clear();

							String message = "data come from server " + count;

							byteBuffer.put(message.getBytes());

							byteBuffer.flip();

							// 发送数据
							datagramChannel.send(byteBuffer, address);
						}
						count++;
					}
				}
			} catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}
	public static void main(String[] args)
	{
		new UDPServer().work();
	}
}