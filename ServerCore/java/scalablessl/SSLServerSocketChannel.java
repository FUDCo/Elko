package scalablessl;

import java.io.IOException;
import java.nio.channels.ServerSocketChannel;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLServerSocket;
import scalablessl.spi.SSLSelectorProvider;

abstract public class SSLServerSocketChannel extends ServerSocketChannel
{
    protected SSLServerSocketChannel(SSLSelectorProvider provider)
        throws SSLException, IOException
    {
        super(provider);
        throw new UnsupportedOperationException(
            "you are linked with the fake scalablessl package");
    }

    public static SSLServerSocketChannel open(String protocol)
        throws SSLException,
               IOException,
               NoSuchAlgorithmException,
               KeyStoreException,
               KeyManagementException,
               UnrecoverableKeyException,
               CertificateException
    {
        throw new UnsupportedOperationException(
            "you are linked with the fake scalablessl package");
    }

    public static SSLServerSocketChannel open(SSLContext context)
        throws IOException
    {
        throw new UnsupportedOperationException(
            "you are linked with the fake scalablessl package");
    }

    public static ServerSocketChannel open() throws IOException {
        throw new UnsupportedOperationException(
            "you are linked with the fake scalablessl package");
    }

    public abstract SSLServerSocket socket();

    public abstract SSLSocketChannel accept() throws IOException;
}
