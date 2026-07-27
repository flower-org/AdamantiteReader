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
        String bindIpAddress = "172.16.1.1";

        // Unicast target variables
        String targetIpAddress = "172.16.1.2";           // Target specific IP

        // 1) Run UDP Server on a background thread
        Thread serverThread = new Thread(() -> {
            try (DatagramSocket serverSocket = new DatagramSocket(port, InetAddress.getByName(bindIpAddress))) {
                System.out.println("UDP Server listening tightly on " + bindIpAddress + ":" + port + " for unicast packets");
                byte[] receiveBuffer = new byte[4096];

                long windowStart = System.currentTimeMillis();
                long totalBytesReceived = 0;
                long totalReceivedPackets = 0;
                long packetsReceivedInWindow = 0;
                long expectedPacketNum = -1;
                long totalLostPackets = 0;
                long lostPacketsInWindow = 0;
                String lastPacketNumStr = "?";

                while (true) {
                    DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                    serverSocket.receive(receivePacket);

                    // Overhead: Ethernet Header(14) + FCS(4) + IPv4 Header(20) + UDP Header(8) = 46 bytes
                    int totalPacketSize = receivePacket.getLength() + 46;
                    totalBytesReceived += totalPacketSize;
                    packetsReceivedInWindow++;
                    totalReceivedPackets++;

                    // Parse out packet number on each receive to fulfill requirement, but don't print every time
                    String message = new String(receivePacket.getData(), 0, receivePacket.getLength(), StandardCharsets.UTF_8).trim();
                    if (message.contains("Packet #")) {
                        int startIndex = message.indexOf("Packet #") + 8;
                        int endIndex = message.indexOf(" at", startIndex);
                        if (startIndex != -1 && endIndex != -1) {
                            lastPacketNumStr = message.substring(startIndex, endIndex);
                            try {
                                long currentPacketNum = Long.parseLong(lastPacketNumStr);
                                if (expectedPacketNum != -1) {
                                    if (currentPacketNum > expectedPacketNum) {
                                        long lost = currentPacketNum - expectedPacketNum;
                                        totalLostPackets += lost;
                                        lostPacketsInWindow += lost;
                                    }
                                }
                                // Update expected sequence if we are moving forward or initializing
                                if (currentPacketNum >= expectedPacketNum || expectedPacketNum == -1) {
                                    expectedPacketNum = currentPacketNum + 1;
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                    }

                    long now = System.currentTimeMillis();
                    long elapsed = now - windowStart;
                    if (elapsed >= 1000) {
                        double mbps = (totalBytesReceived * 8.0 * 1000.0) / (elapsed * 1000000.0);
                        long windowExpected = packetsReceivedInWindow + lostPacketsInWindow;
                        long totalExpected = totalReceivedPackets + totalLostPackets;

                        double windowLostPct = windowExpected > 0 ? (lostPacketsInWindow * 100.0 / windowExpected) : 0.0;
                        double totalLostPct = totalExpected > 0 ? (totalLostPackets * 100.0 / totalExpected) : 0.0;

                        System.out.printf("[SERVER] Receive Rate: %.2f Mbps | %d pkts/sec | Lost: %d / %d (%.2f%%) in window (Total Lost: %d / %d (%.2f%%)) | Last Read Packet #%s%n",
                                mbps, packetsReceivedInWindow, lostPacketsInWindow, windowExpected, windowLostPct, totalLostPackets, totalExpected, totalLostPct, lastPacketNumStr);

                        windowStart = now;
                        totalBytesReceived = 0;
                        packetsReceivedInWindow = 0;
                        lostPacketsInWindow = 0;
                    }
                }
            } catch (Exception e) {
                System.err.println("Server error: " + e.getMessage());
                e.printStackTrace();
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        // 2) Send UDP packets periodically (every second)
        try (DatagramSocket senderSocket = new DatagramSocket(0, InetAddress.getByName(bindIpAddress))) {
            InetAddress targetUnicast = InetAddress.getByName(targetIpAddress);

            System.out.println("UDP Sender unicasting to " + targetIpAddress + ":" + port + "...");

            long windowStart = System.currentTimeMillis();
            long totalBytesSent = 0;
            long packetsSentInWindow = 0;

            int counter = 0;
            byte[] sendBuffer = new byte[1024];
            DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, targetUnicast, port);

            while (true) {
                // Keep string allocation minimal
                String payload = "Packet #" + (++counter) + " at " + System.currentTimeMillis();
                byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);

                // Copy the payload into our pre-allocated padded buffer (to increase bytes per syscall)
                System.arraycopy(payloadBytes, 0, sendBuffer, 0, payloadBytes.length);

                senderSocket.send(sendPacket);

                // Overhead: Ethernet Header(14) + FCS(4) + IPv4 Header(20) + UDP Header(8) = 46 bytes
                int totalPacketSize = sendPacket.getLength() + 46;
                totalBytesSent += totalPacketSize;
                packetsSentInWindow++;

                long now = System.currentTimeMillis();
                long elapsed = now - windowStart;
                if (elapsed >= 1000) {
                    double mbps = (totalBytesSent * 8.0 * 1000.0) / (elapsed * 1000000.0);
                    System.out.printf("[SENDER] Send Rate: %.2f Mbps | %d pkts/sec | Last Sent Packet #%d%n",
                            mbps, packetsSentInWindow, counter);
                    windowStart = now;
                    totalBytesSent = 0;
                    packetsSentInWindow = 0;
                }
            }
        } catch (Exception e) {
            System.err.println("Sender error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
