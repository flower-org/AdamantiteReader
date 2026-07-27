package com.adamantite;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class EthernetTest {
    public static void main(String[] args) {
        // Default port to 8888 if not provided as an argument
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8888;
        String ipAddress = "172.16.1.1";
        // Assuming a typical 255.255.255.0 subnet mask.
        // 255.255.255.255 can also be used as the universal broadcast address
        String broadcastAddress = "172.16.1.255";

        // 1) Run UDP Server on a background thread
        Thread serverThread = new Thread(() -> {
            try (DatagramSocket serverSocket = new DatagramSocket(null)) {
                // Bind to wildcard address to receive broadcast packets on this port
                serverSocket.bind(new java.net.InetSocketAddress(port));
                System.out.println("UDP Server listening on port " + port + " for broadcasts");
                byte[] receiveBuffer = new byte[4096];

                long windowStart = System.currentTimeMillis();
                long totalBytesReceived = 0;

                while (true) {
                    DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                    serverSocket.receive(receivePacket);

                    // Overhead: Ethernet Header(14) + FCS(4) + IPv4 Header(20) + UDP Header(8) = 46 bytes
                    int totalPacketSize = receivePacket.getLength() + 46;
                    totalBytesReceived += totalPacketSize;

                    long now = System.currentTimeMillis();
                    long elapsed = now - windowStart;
                    if (elapsed >= 1000) {
                        double mbps = (totalBytesReceived * 8.0 * 1000.0) / (elapsed * 1000000.0);
                        System.out.printf("[SERVER] Current Receive Rate: %.6f Mbps%n", mbps);
                        windowStart = now;
                        totalBytesReceived = 0;
                    }

                    String message = new String(receivePacket.getData(), 0, receivePacket.getLength(), StandardCharsets.UTF_8).trim();

                    String packetNumStr = "?";
                    if (message.contains("Packet #")) {
                        int startIndex = message.indexOf("Packet #") + 8;
                        int endIndex = message.indexOf(" at", startIndex);
                        if (startIndex != -1 && endIndex != -1) {
                            packetNumStr = message.substring(startIndex, endIndex);
                        }
                    }

                    System.out.println("[SERVER] Extracted Packet #" + packetNumStr + " | Received from "
                            + receivePacket.getAddress().getHostAddress() + ":"
                            + receivePacket.getPort() + " -> " + message);
                }
            } catch (Exception e) {
                System.err.println("Server error: " + e.getMessage());
                e.printStackTrace();
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        // 2) Send UDP packets periodically (every second)
        try (DatagramSocket senderSocket = new DatagramSocket(0, InetAddress.getByName(ipAddress))) {
            senderSocket.setBroadcast(true);
            InetAddress targetBroadcast = InetAddress.getByName(broadcastAddress);

            System.out.println("UDP Sender broadcasting to " + broadcastAddress + ":" + port + " every 1 second...");

            long windowStart = System.currentTimeMillis();
            long totalBytesSent = 0;

            int counter = 0;
            while (true) {
                String payload = "Ethernet Test Packet #" + (++counter) + " at " + new Date();
                byte[] sendData = payload.getBytes(StandardCharsets.UTF_8);

                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, targetBroadcast, port);
                senderSocket.send(sendPacket);

                // Overhead: Ethernet Header(14) + FCS(4) + IPv4 Header(20) + UDP Header(8) = 46 bytes
                int totalPacketSize = sendPacket.getLength() + 46;
                totalBytesSent += totalPacketSize;

                long now = System.currentTimeMillis();
                long elapsed = now - windowStart;
                if (elapsed >= 1000) {
                    double mbps = (totalBytesSent * 8.0 * 1000.0) / (elapsed * 1000000.0);
                    System.out.printf("[SENDER] Current Send Rate: %.6f Mbps%n", mbps);
                    windowStart = now;
                    totalBytesSent = 0;
                }

                System.out.println("[SENDER] Sent: " + payload);

                Thread.sleep(1000);
            }
        } catch (Exception e) {
            System.err.println("Sender error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
