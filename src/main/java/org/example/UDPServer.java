package org.example;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;

import static java.nio.channels.SelectionKey.OP_READ;

public class UDPServer {

    private static long lastReceivedSequenceNumber = -1;
    private static boolean handshakeComplete = false;

    public static void main(String[] args) throws IOException {
        InetSocketAddress serverAddress = new InetSocketAddress("localhost", 8007);

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
                        buf.flip();
                        Packet receivedPacket = Packet.fromBuffer(buf);

                        handleReceivedPacket(readChannel, clientAddress, receivedPacket);
                    }

                    keyIterator.remove();
                }
            }
        }
    }

    private static void handleReceivedPacket(DatagramChannel channel, SocketAddress clientAddress, Packet receivedPacket) throws IOException {
        // Handle the received packet based on its type
        switch (receivedPacket.getType()) {
            case Packet.SYN:
                System.out.println("Server: SYN packet received from client");
                handleSynPacket(channel, clientAddress);
                break;
            case Packet.ACK:
                handleAckPacket(channel, clientAddress, receivedPacket);
                break;
            case Packet.DATA:
                if (handshakeComplete) {
                    handleDataPacket(channel, clientAddress, receivedPacket);
                } else {
                    System.out.println("Server: Ignoring DATA packet. Handshake not completed.");
                }
                break;
            // Add more cases as needed for other packet types
            default:
                System.out.println("Server: Unexpected packet type received.");
        }
    }

    private static void handleSynPacket(DatagramChannel channel, SocketAddress clientAddress) throws IOException {
        // Respond with SYN-ACK
        Packet synAckPacket = new Packet.Builder()
                .setType(Packet.SYN_ACK)
                .setSequenceNumber(1L)  // You may need to adjust the sequence number
                .setPortNumber(((InetSocketAddress) channel.getLocalAddress()).getPort())
                .setPeerAddress(((InetSocketAddress) clientAddress).getAddress())
                .setPayload("SYN-ACK".getBytes())
                .create();

        channel.send(synAckPacket.toBuffer(), clientAddress);
        System.out.println("Server: SYN-ACK packet sent to client.");
    }

    private static void handleAckPacket(DatagramChannel channel, SocketAddress clientAddress, Packet ackPacket) {
        // Handle ACK packet as needed
        System.out.println("Server: Received ACK packet from client. Handshake complete.");
        lastReceivedSequenceNumber = ackPacket.getSequenceNumber();
        handshakeComplete = true;
        // Continue with the rest of your application logic
    }

    private static void handleDataPacket(DatagramChannel channel, SocketAddress clientAddress, Packet dataPacket) throws IOException {
        // Handle DATA packet as needed
        System.out.println("Server: Received DATA packet from client. Sequence Number: " + dataPacket.getSequenceNumber());

        // Process the received data (you can replace this with your custom logic)
        String receivedData = new String(dataPacket.getPayload());
        System.out.println("Server: Received data: " + receivedData);

        // Respond with a DATA_ACK
        sendDataAckPacket(channel, clientAddress, dataPacket.getSequenceNumber());
    }

    private static void sendDataAckPacket(DatagramChannel channel, SocketAddress clientAddress, long sequenceNumber) throws IOException {
        // Respond with DATA_ACK
        Packet dataAckPacket = new Packet.Builder()
                .setType(Packet.DATA_ACK)
                .setSequenceNumber(sequenceNumber)
                .setPortNumber(((InetSocketAddress) channel.getLocalAddress()).getPort())
                .setPeerAddress(((InetSocketAddress) clientAddress).getAddress())
                .setPayload("DATA_ACK".getBytes())  // You can customize the payload if needed
                .create();

        // Send the DATA_ACK packet
        channel.send(dataAckPacket.toBuffer(), clientAddress);
        System.out.println("Server: Sent DATA_ACK packet to client. Sequence Number: " + sequenceNumber);
    }
}
