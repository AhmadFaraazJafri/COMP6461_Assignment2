package org.example;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;

import static java.nio.channels.SelectionKey.OP_READ;

public class UDPServer {

    private static long lastReceivedSequenceNumber = -1;
    private static long serverSequenceNumber = 2000; // Initial server sequence number
    private static long expectedDataSequenceNumber = -1;

    public static void main(String[] args) throws IOException {
        InetSocketAddress serverAddress = new InetSocketAddress("localhost", 8007);
        InetSocketAddress routerAddress = new InetSocketAddress("localhost", 3000);
        try (DatagramChannel channel = DatagramChannel.open()) {
            channel.bind(serverAddress);
            channel.configureBlocking(false);

            Selector selector = Selector.open();
            channel.register(selector, OP_READ);

            System.out.println("Server: Waiting for incoming connections...");

            while (true) {
                selector.select();

                Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();

                    if (key.isReadable()) {
                        DatagramChannel readChannel = (DatagramChannel) key.channel();
                        ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
                        SocketAddress clientAddress = readChannel.receive(buf);
                        System.out.println(clientAddress);
                        buf.flip();
                        Packet receivedPacket = Packet.fromBuffer(buf);

                        handleReceivedPacket(readChannel, clientAddress, receivedPacket, routerAddress);
                    }

                    keyIterator.remove();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void handleReceivedPacket(DatagramChannel channel, SocketAddress clientAddress, Packet receivedPacket, InetSocketAddress routerAddress) throws Exception {
        switch (receivedPacket.getType()) {
            case Packet.SYN:
                System.out.println("Server: SYN packet received from client. Sequence Number: " + receivedPacket.getSequenceNumber());
                handleSynPacket(channel, (InetSocketAddress) clientAddress, routerAddress);
                break;
            case Packet.ACK:
                handleAckPacket(channel, clientAddress, receivedPacket);
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

    private static void handleSynPacket(DatagramChannel channel, InetSocketAddress clientAddress, InetSocketAddress routerAddress) throws Exception {
        DatagramPacket synAckPacket = constructPacket((byte) Packet.SYN_ACK, serverSequenceNumber, clientAddress.getAddress(),clientAddress.getPort(), "SYN-ACK".getBytes());
        sendPacket(synAckPacket, routerAddress);
        System.out.println("Server: SYN-ACK packet sent to client. Sequence Number: " + serverSequenceNumber);
    }

    private static void sendPacket(DatagramPacket packet, InetSocketAddress destination) throws Exception {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.send(packet);
            System.out.println("Sent packet to: " + destination);
        }
    }
    private static void handleAckPacket(DatagramChannel channel, SocketAddress clientAddress, Packet ackPacket) {
        System.out.println("Server: Received ACK packet from client. Handshake complete. Sequence Number: " + ackPacket.getSequenceNumber());
        lastReceivedSequenceNumber = ackPacket.getSequenceNumber();
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

    private static DatagramPacket constructPacket(byte packetType, long sequenceNumber,
                                                  InetAddress peerAddress, int peerPort, byte[] payload) {
        int totalSize = 1 + 8 + 4 + 2 + payload.length;

        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        buffer.put(packetType);
        buffer.putLong(sequenceNumber);
        buffer.put(peerAddress.getAddress());
        buffer.putShort((short) peerPort);
        buffer.put(payload);

        return new DatagramPacket(buffer.array(), buffer.position(), peerAddress, peerPort);
    }
}
