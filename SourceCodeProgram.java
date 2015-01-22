import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public class SourceCodeProgram 
{

    public static void main(String[] args) throws Exception 
    {
        IOCopier.joinFiles(new File("whole"), new File[] { new File("xa"), new File("xb") });
    }
}

class IOCopier 
{
    public static void joinFiles(File destination, File[] sources) throws IOException 
    {
        OutputStream output = null;
        try 
        {
            output = createAppendableStream(destination);
            for (File source : sources) 
            {
                appendFile(output, source);
            }
        } 
        finally 
        {
            IOUtils.closeQuietly(output);
        }
    }

    private static BufferedOutputStream createAppendableStream(File destination) throws FileNotFoundException 
    {
        return new BufferedOutputStream(new FileOutputStream(destination, true));
    }

    private static void appendFile(OutputStream output, File source) throws IOException 
    {
        InputStream input = null;
        try 
        {
            input = new BufferedInputStream(new FileInputStream(source));
            IOUtils.copy(input, output);
        } 
        finally 
        {
            IOUtils.closeQuietly(input);
        }
    }
}

class IOUtils 
{
    private static final int BUFFER_SIZE = 1024 * 4;

    public static long copy(InputStream input, OutputStream output) throws IOException 
    {
        byte[] buffer = new byte[BUFFER_SIZE];
        long count = 0;
        int n = 0;
        while (-1 != (n = input.read(buffer))) 
        {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

    public static void closeQuietly(Closeable output) 
    {
        try 
        {
            if (output != null) 
            {
                output.close();
            }
        } 
        catch (IOException ioe) 
        {
            ioe.printStackTrace();
        }
    }
}