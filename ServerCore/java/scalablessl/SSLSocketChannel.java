package scalablessl;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import javax.net.ssl.SSLException;
import scalablessl.spi.SSLSelectorProvider;

public abstract class SSLSocketChannel extends SocketChannel {
    protected SSLSocketChannel(SSLSelectorProvider provider)
        throws SSLException, IOException
    {
        super(provider);
        throw new UnsupportedOperationException(
            "you are linked with the fake scalablessl package");
    }
}
