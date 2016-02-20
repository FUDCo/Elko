package org.elkoserver.foundation.net.zmq.test;

import org.elkoserver.foundation.net.NetAddr;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import org.zeromq.ZMQ;

public class ZMQSender {
    public static void main(String[] args) {
        BufferedReader in =
            new BufferedReader(new InputStreamReader(System.in));

        String host = args[0];
        boolean push = true;
        if (host.startsWith("PUSH:")) {
            push = true;
            host = "tcp://" + host.substring(5);
        } else if (host.startsWith("PUB:")) {
            push = false;
            try {
                NetAddr parsedAddr = new NetAddr(host.substring(4));
                host = "tcp://*:" + parsedAddr.getPort();
            } catch (IOException e) {
                System.out.println("problem setting up ZMQ connection with " +
                                   host + ": " + e);
                return;
            }
        } else {
            push = true;
            host = "tcp://" + host;
        }
        

        ZMQ.Context context = ZMQ.context(1);
        ZMQ.Socket socket;
        if (push) {
            socket = context.socket(ZMQ.PUSH);
            System.out.println("PUSH to server at " + host);
            socket.connect(host);
        } else {
            socket = context.socket(ZMQ.PUB);
            System.out.println("PUB at " + host);
            socket.bind(host);
        }

        String msg = null;
        while (true) {
            try {
                String line = in.readLine();
                
                if (line == null) {
                    break;
                } else if (line.equals("")) {
                    if (msg != null) {
                        msg += " ";
                        byte[] msgBytes = msg.getBytes();
                        msgBytes[msgBytes.length - 1] = 0;
                        socket.send(msgBytes, 0);
                        msg = null;
                    }
                } else if (msg == null) {
                    msg = line;
                } else {
                    msg += "\n" + line;
                }
            } catch (IOException e) {
                break;
            }
        }
    }
}
