package org.example;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import joptsimple.OptionParser;
import joptsimple.OptionSet;

import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;

public class UDPServer {

    private static long lastReceivedSequenceNumber = -1;

    private static int windowSizeServer = 4;

    private static HashMap<Integer, Packet> receivedWindowPackets = new HashMap<>();

    private static List<Packet> receivedPackets = new ArrayList<>();

    private static long lastReceivedClientSequenceNumber = -1;
    private static long serverSequenceNumber = 2000; // Initial server sequence number
    private static long expectedDataSequenceNumber = -1;

    private static final Logger logger = LoggerFactory.getLogger(UDPServer.class);

    public static void main(String[] args) throws Exception {
        InetSocketAddress serverAddress = new InetSocketAddress("localhost", 8007);
//        InetSocketAddress routerAddress = new InetSocketAddress("localhost", 3000);
        OptionParser parser = new OptionParser();
        parser.acceptsAll(asList("port", "p"), "Listening port")
                .withOptionalArg()
                .defaultsTo("8007");

        OptionSet opts = parser.parse(args);
        int port = Integer.parseInt((String) opts.valueOf("port"));

        SocketAddress routerAddress = new InetSocketAddress("localhost", 3000);

        try (DatagramChannel channel = DatagramChannel.open()) {
            channel.bind(new InetSocketAddress(port));
            logger.info("EchoServer is listening at {}", channel.getLocalAddress());
            ByteBuffer buf = ByteBuffer
                    .allocate(Packet.MAX_LEN)
                    .order(ByteOrder.BIG_ENDIAN);

            for (; ; ) {
                buf.clear();
                SocketAddress router = channel.receive(buf);

                buf.flip();
                Packet receivedPacket = Packet.fromBuffer(buf);
                buf.flip();

                switch (receivedPacket.getType()) {
                    case Packet.SYN:
                        System.out.println();
                        System.out.println("Server: SYN packet received from client. Sequence Number: " + receivedPacket.getSequenceNumber());
                        handleSynPacket(channel, receivedPacket, routerAddress);
                        break;
                    case Packet.ACK:
                        System.out.println();
                        handleAckPacket(receivedPacket);
                        break;
                    case Packet.DATA:
                        System.out.println();
                        if (handshakeComplete()) {
                            handleDataPacket(channel, receivedPacket, routerAddress);
                        } else {
                            System.out.println("Server: Ignoring DATA packet. Handshake not completed.");
                        }
                        break;
                    default:
                        System.out.println("Server: Unexpected packet type received.");
                }

                for (int i = 0; i < receivedPackets.size(); i++) {
                    System.out.println("------------------------------------------------------");
                    System.out.println("Packets received: " + receivedPackets.get(i));
                    System.out.println("Size of array: " + receivedPackets.size());
                    System.out.println("-------------------------------------------------------");
                }

                // Send the response to the router not the client.
                // The peer address of the packet is the address of the client already.
                // We can use toBuilder to copy properties of the current packet.
                // This demonstrate how to create a new packet from an existing packet.
//                Packet resp = packet.toBuilder()
//                        .setPayload(payload.getBytes())
//                        .create();
//                channel.send(resp.toBuffer(), router);
            }
        }
    }
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }

    private static void handleReceivedPacketUsingPacket(DatagramChannel channel, SocketAddress clientAddress, Packet receivedPacket, InetSocketAddress routerAddress) throws Exception {
        switch (receivedPacket.getType()) {
            case Packet.SYN:
                System.out.println("Server: SYN packet received from client. Sequence Number received from client: " + receivedPacket.getSequenceNumber());
                handleSynPacket(channel, receivedPacket, routerAddress);
                break;
            case Packet.ACK:
                handleAckPacket(receivedPacket);
                break;
            case Packet.DATA:
                if (handshakeComplete()) {
                    handleDataPacket(channel, receivedPacket, routerAddress);

                } else {
                    System.err.println("Server: Ignoring DATA packet. Handshake not completed.");
                }
                break;
            default:
                System.err.println("Server: Unexpected packet type received.");
        }
    }

    private static void handleSynPacket(DatagramChannel channel, Packet packet, SocketAddress routerAddress) throws Exception {

        int clientSequenceNumber = (int) packet.getSequenceNumber();

        while (clientSequenceNumber - serverSequenceNumber < 1000) {
            Random random = new Random();
            serverSequenceNumber = random.nextInt(1000, 20000);
        }

        Packet synAckPacket = constructReplyPacket((byte) Packet.SYN_ACK, serverSequenceNumber, packet, "SYN-ACK".getBytes());
        sendPacket(channel, synAckPacket, routerAddress);
        System.out.println("Server: SYN-ACK packet sent to client. Sequence Number sent: " + synAckPacket.getSequenceNumber() + " ACK sent: " + synAckPacket.getAckNumber());
    }

    private static Packet constructReplyPacket(byte type, long serverSequenceNumber, Packet packet, byte[] payload) {
        long clientACK = packet.getSequenceNumber() + 1;
        lastReceivedClientSequenceNumber = clientACK;

        Packet replyPacket = packet.toBuilder()
                .setType(type)
                .setSequenceNumber(serverSequenceNumber)
                .setPayload(payload)
                .setAckNumber(clientACK)
                .create();
        return replyPacket;
    }

    private static void sendPacket(DatagramChannel channel, Packet packet, SocketAddress destination) throws Exception {
        channel.send(packet.toBuffer(), destination);
//        System.out.println("Sent packet to: " + destination);
    }

    private static void handleAckPacket(Packet ackPacket) {
        if (ackPacket.getAckNumber() == serverSequenceNumber + 1 && ackPacket.getType() == Packet.ACK) {
            System.out.println("Server: Received ACK packet from client. | Sequence Number received: " + ackPacket.getSequenceNumber() + " | Handshake complete. | " + " ACK number received: " + ackPacket.getAckNumber());
            lastReceivedSequenceNumber = ackPacket.getSequenceNumber();


            for (int i = 1; i <= windowSizeServer; i++) {
                if (receivedWindowPackets.size() == windowSizeServer) {
                    break;
                }
                receivedWindowPackets.put((int) (lastReceivedClientSequenceNumber + i), null);
            }
            serverSequenceNumber = ackPacket.getAckNumber();
        }
//        System.out.println("Server: Last Received Sequence Number: " + lastReceivedSequenceNumber);
    }

    private static void handleDataPacket(DatagramChannel channel, Packet dataPacket, SocketAddress routerAddress) throws Exception {
        System.out.println("DATA Packet Received from client. | Sequence Number: " + dataPacket.getSequenceNumber() + " | ACK number received: " + dataPacket.getAckNumber());

        byte[] payload = dataPacket.getPayload();

        // Convert the payload to a String (assuming it contains text data)
        String payloadString = new String(payload, StandardCharsets.UTF_8);
        System.out.println("request: " + payloadString);

        //Have to fix this and put all the code below to parent call
        receivedWindowPackets.put((int) dataPacket.getSequenceNumber(), dataPacket);

        if (receivedWindowPackets.containsKey((int) dataPacket.getSequenceNumber()) && dataPacket.getType() == Packet.DATA) {


            if (!containsPacket(receivedPackets, dataPacket)) {
                Packet dataAckPacket = constructReplyPacket((byte) Packet.DATA_ACK, serverSequenceNumber, dataPacket, "DATA_ACK".getBytes());
                System.out.println("Server: DATA_ACK packet sent to client. Sequence Number sent: " + dataAckPacket.getSequenceNumber() + " ACK sent: " + dataAckPacket.getAckNumber());
                sendPacket(channel, dataAckPacket, routerAddress);


                receivedPackets.add(receivedWindowPackets.remove((int) dataPacket.getSequenceNumber()));

                expectedDataSequenceNumber++;
                serverSequenceNumber++;
            } else {
                System.out.println();
                System.err.println("Duplicate Packet received: " + dataPacket.getSequenceNumber() + " | Hence Dropped!");
            }
        } else {
            Packet dataAckPacket = constructReplyPacket((byte) Packet.DATA_ACK, serverSequenceNumber, dataPacket, "DATA_ACK".getBytes());
            sendPacket(channel, dataAckPacket, routerAddress);
            System.err.println("Server: Out-of-order packet received. Ignoring. Expected: " + expectedDataSequenceNumber + ", Received: " + dataPacket.getSequenceNumber());
        }
    }

    private static boolean handshakeComplete() {
        return lastReceivedSequenceNumber >= 0;
    }

    private static Packet constructPacket(byte packetType, long sequenceNumber,
                                          InetAddress peerAddress, int peerPort, byte[] payload) {
        Packet p = new Packet.Builder()
                .setType(packetType)
                .setSequenceNumber(sequenceNumber)
                .setPortNumber(peerPort)
                .setPeerAddress(peerAddress)
                .setPayload(payload)
                .create();
        return p;
    }

    public static Boolean Timout(DatagramChannel channel) throws IOException {
        // Try to receive a packet within timeout.
        channel.configureBlocking(false);
        Selector selector = Selector.open();
        channel.register(selector, OP_READ);
        System.out.println("Waiting for Response:");
        selector.select(7000);
        System.out.println();

        Set<SelectionKey> keys = selector.selectedKeys();
        if (keys.isEmpty()) {
            System.out.println("No response after timout");
            System.out.println();
            return true;
        }
        keys.clear();
        return false;
    }

    private static boolean containsPacket(List<Packet> packetList, Packet targetPacket) {
        for (Packet packet : packetList) {
            if (packet.getSequenceNumber() == targetPacket.getSequenceNumber() &&
                    packet.getAckNumber() == targetPacket.getAckNumber()) {
                return true;
            }
        }
        return false;
    }
}
