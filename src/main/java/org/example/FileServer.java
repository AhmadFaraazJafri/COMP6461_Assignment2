//package org.example;
//
//import java.io.*;
//import java.net.ServerSocket;
//import java.net.Socket;
//import com.google.gson.JsonObject;
//import com.google.gson.JsonArray;
//
//public class FileServer {
//
//    public static void main(String[] args) {
//        int port = 8081;
//        try {
//            ServerSocket serverSocket = new ServerSocket(port);
//            System.out.println("FileServer is listening on port " + port);
//
//            while (true) {
//                Socket clientSocket = serverSocket.accept();
//                handleRequest(clientSocket);
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
//
//    private static void handleRequest(Socket clientSocket) throws IOException {
//        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
//        OutputStream out = clientSocket.getOutputStream();
//
//        String requestLine = in.readLine();
//        if (requestLine != null) {
//            String[] requestTokens = requestLine.split(" ");
//            if (requestTokens.length == 3) {
//                String method = requestTokens[0];
//                String path = requestTokens[1];
//
//                if ("GET".equalsIgnoreCase(method) && "/".equals(path)) {
//                    processGETRequest(out);
//                } else {
//                    HttpServer.sendResponse(501, "Not Implemented", "HTTP method not supported", out);
//                }
//            }
//        }
//
//        in.close();
//        out.close();
//        clientSocket.close();
//    }
//
//    private static void processGETRequest(OutputStream out) throws IOException {
//        File currentDir = new File(System.getProperty("user.dir"));
//        File[] files = currentDir.listFiles();
//
//        if (files == null) {
//            HttpServer.sendResponse(500, "Internal Server Error", "Error reading directory", out);
//            return;
//        }
//
//        JsonObject jsonResponse = new JsonObject();
//        JsonArray fileList = new JsonArray();
//
//        for (File file : files) {
//            fileList.add(file.getName());
//        }
//
//        jsonResponse.add("files", fileList);
//        HttpServer.sendJSONResponse(200, "OK", jsonResponse, out);
//    }
//}
