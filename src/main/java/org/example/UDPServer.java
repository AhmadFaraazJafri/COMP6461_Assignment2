package org.example;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import joptsimple.OptionParser;
import joptsimple.OptionSet;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;

public class UDPServer {

    private static long lastReceivedSequenceNumber = -1;

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

                String payload = new String(receivedPacket.getPayload(), UTF_8);
                logger.info("Packet: {}", receivedPacket);
                logger.info("Payload: {}", payload);
                logger.info("Router: {}", router);

                switch (receivedPacket.getType()) {
                    case Packet.SYN:
                        System.out.println("Server: SYN packet received from client. Sequence Number: " + receivedPacket.getSequenceNumber());
                        handleSynPacket(channel, receivedPacket, routerAddress);
                        break;
                    case Packet.ACK:
                        handleAckPacket(receivedPacket);
                        break;
                    case Packet.DATA:
                        if (handshakeComplete()) {
//                            handleDataPacket(channel, receivedPacket, receivedPacket);
                        } else {
                            System.out.println("Server: Ignoring DATA packet. Handshake not completed.");
                        }
                        break;
                    default:
                        System.out.println("Server: Unexpected packet type received.");
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
                System.out.println("Server: SYN packet received from client. Sequence Number: " + receivedPacket.getSequenceNumber());
                handleSynPacket(channel, receivedPacket, routerAddress);
                break;
            case Packet.ACK:
                handleAckPacket(receivedPacket);
                break;
            case Packet.DATA:
                if (handshakeComplete()) {
                    handleDataPacket(channel, clientAddress, receivedPacket);
                } else {
                    System.out.println("Server: Ignoring DATA packet. Handshake not completed.");
                }
                break;
            default:
                System.out.println("Server: Unexpected packet type received.");
        }
    }

    private static void handleSynPacket(DatagramChannel channel, Packet packet, SocketAddress routerAddress) throws Exception {
        Packet synAckPacket = constructReplyPacket((byte) Packet.SYN_ACK, serverSequenceNumber, packet, "SYN-ACK".getBytes());
        sendPacket(channel, synAckPacket, routerAddress);
        System.out.println("Server: SYN-ACK packet sent to client. Sequence Number: " + serverSequenceNumber);
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
        System.out.println("Sent packet to: " + destination);
    }

    private static void handleAckPacket(Packet ackPacket) {
        if (ackPacket.getAckNumber() == serverSequenceNumber + 1 && ackPacket.getType() == Packet.ACK) {
            System.out.println("Server: Received ACK packet from client. Handshake complete. Sequence Number: " + ackPacket.getSequenceNumber());
            System.out.println("Received ACK: " + ackPacket.getAckNumber());
            lastReceivedSequenceNumber = ackPacket.getSequenceNumber();
        }
        System.out.println("Server: Last Received Sequence Number: " + lastReceivedSequenceNumber);
    }

    private static void handleDataPacket(DatagramChannel channel, SocketAddress clientAddress, Packet dataPacket) throws IOException {
        if (dataPacket.getSequenceNumber() == expectedDataSequenceNumber) {
            System.out.println("Server: Received DATA packet from client. Sequence Number: " + dataPacket.getSequenceNumber());
            String receivedData = new String(dataPacket.getPayload());
            System.out.println("Server: Received data: " + receivedData);
            sendDataAckPacket(channel, clientAddress, dataPacket.getSequenceNumber());
            expectedDataSequenceNumber++;
        } else {
            System.err.println("Server: Out-of-order packet received. Ignoring. Expected: " + expectedDataSequenceNumber + ", Received: " + dataPacket.getSequenceNumber());
        }
    }

    private static void sendDataAckPacket(DatagramChannel channel, SocketAddress clientAddress, long sequenceNumber) throws IOException {
        Packet dataAckPacket = new Packet.Builder()
                .setType(Packet.DATA_ACK)
                .setSequenceNumber(sequenceNumber)
                .setPortNumber(((InetSocketAddress) channel.getLocalAddress()).getPort())
                .setPeerAddress(((InetSocketAddress) clientAddress).getAddress())
                .setPayload("DATA_ACK".getBytes())
                .create();

        channel.send(dataAckPacket.toBuffer(), clientAddress);
        System.out.println("Server: Sent DATA_ACK packet to client. Sequence Number: " + sequenceNumber);
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
}
