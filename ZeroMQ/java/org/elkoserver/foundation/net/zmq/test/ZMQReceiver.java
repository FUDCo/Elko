package org.elkoserver.foundation.net.zmq.test;

import org.elkoserver.foundation.net.NetAddr;
import java.io.IOException;
import org.zeromq.ZMQ;

public class ZMQReceiver {
    /** Subscribe filter to receive all messages. */
    private static final byte[] UNIVERSAL_SUBSCRIPTION = new byte[0];

    public static void main(String[] args) {
        String host = args[0];
        boolean subscribe = false;

        if (host.startsWith("SUB:")) {
            subscribe = true;
            host = host.substring(4);
        } else if (host.startsWith("PULL:")) {
            subscribe = false;
            host = host.substring(5);
        }
        NetAddr netAddr;
        try {
            netAddr = new NetAddr(host);
        } catch (IOException e) {
            System.out.println("problem parsing host address: " + e);
            return;
        }

        String addr;
        if (subscribe) {
            addr = "tcp://" + host;
        } else {
            addr = "tcp://*:" + netAddr.getPort();
        }

        ZMQ.Context context = ZMQ.context(1);
        ZMQ.Socket socket;
        if (subscribe) {
            System.out.println("subscribing to ZMQ messages from " + addr);
            socket = context.socket(ZMQ.SUB);
            socket.subscribe(UNIVERSAL_SUBSCRIPTION);
            socket.connect(addr);
        } else {
            System.out.println("pulling ZMQ messages at " + addr);
            socket = context.socket(ZMQ.PULL);
            socket.bind(addr);
        }

        while (true) {
            byte data[] = socket.recv(0);
            if (data != null) {
                int length = data.length;
                while (length > 0 && data[length - 1] == 0) {
                    --length;
                }
                while (length > 0 && data[length - 1] == '\n') {
                    --length;
                }
                String msg = new String(data, 0, length);
                System.out.println("in: " + msg + "\n");
            } else {
                System.out.println("null ZMQ recv, exiting");
                break;
            }
        }
    }
}
