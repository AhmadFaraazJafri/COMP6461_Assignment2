package org.example;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

import static org.example.HttpServer.sendJSONResponse;
import static org.example.HttpServer.sendResponse;
import static org.example.HttpServer.sendVerboseJSONResponse;

public class FileServer {

    private static final String BASE_PATH = "C:\\Users\\afjaf\\IdeaProjects\\CN2";

    public static void main(String[] args) {
        int port = 8081;
        try {
            ServerSocket serverSocket = new ServerSocket(port);
            System.out.println("FileServer is listening on port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                handleRequest(clientSocket);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleRequest(Socket clientSocket) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        OutputStream out = clientSocket.getOutputStream();

        String requestLine = in.readLine();
        if (requestLine != null) {
            String[] requestTokens = requestLine.split(" ");
            if (requestTokens.length == 3) {
                String method = requestTokens[0];
                String path = requestTokens[1];

                Map<String, String> headers = new HashMap<>();

                String line;
                while ((line = in.readLine()) != null && !line.isEmpty()) {
                    String[] headerTokens = line.split(": ");
                    if (headerTokens.length == 2) {
                        headers.put(headerTokens[0], headerTokens[1]);
                    }
                }

                boolean isVerbose = false;
                if (headers.containsKey("Verbose") && headers.get("Verbose").equalsIgnoreCase("true")) {
                    isVerbose = true;
                }

                if ("GET".equalsIgnoreCase(method) && path.startsWith("/")) {
                    String filePath = BASE_PATH + path;
                    if ("/".equals(path)) {
                        processListFilesRequest(filePath, headers, out, isVerbose);
                    }
                    else {
                        processServeFileRequest(filePath, out, isVerbose);
                    }
                }
                if ("POST".equalsIgnoreCase(method) && path.startsWith("/")) {
                    String[] pathTokens = path.split("/");
                    if (pathTokens.length > 1) {
                        String fileName = pathTokens[1];

                        boolean overwrite = false;
                        if (headers.containsKey("Overwrite") && headers.get("Overwrite").equalsIgnoreCase("true")) {
                            overwrite = true;
                        }

                        StringBuilder requestBody = new StringBuilder();
                        int contentLength = 0;

                        if (headers.containsKey("Content-Length")) {
                            contentLength = Integer.parseInt(headers.get("Content-Length"));
                            char[] buffer = new char[contentLength];
                            in.read(buffer, 0, contentLength);
                            requestBody.append(buffer);
                        }

                        String content = requestBody.toString();

                        String filePath = BASE_PATH + File.separator + fileName;

                        File file = new File(filePath);

                        if (overwrite || !file.exists()) {
                            try (FileWriter writer = new FileWriter(file)) {
                                writer.write(content);
                            }

                            sendResponse(200, "OK", "File created or overwritten", out, isVerbose);
                        } else {
                            sendResponse(409, "Conflict", "File already exists, and overwrite is not allowed", out, isVerbose);
                        }
                    } else {
                        sendResponse(400, "Bad Request", "Invalid request format", out, isVerbose);
                    }
                }

            }
        }

        in.close();
        out.close();
        clientSocket.close();
    }

    private static void processListFilesRequest(String directoryPath, Map<String, String> headers, OutputStream out, boolean isVerbose) throws IOException {
        File directory = new File(directoryPath);
        File[] files = directory.listFiles();

        if (files != null) {
            String acceptHeaderValue = headers.get("Accept");
            String responseContent = "";

            if (acceptHeaderValue != null && acceptHeaderValue.contains("application/json")) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();

                JsonArray fileArray = new JsonArray();
                for (File file : files) {
                    JsonObject fileObject = new JsonObject();
                    fileObject.addProperty("name", file.getName());
                    fileObject.addProperty("size", file.length());
                    fileArray.add(fileObject);
                }

                JsonObject jsonResponse = new JsonObject();
                jsonResponse.add("files", fileArray);
                responseContent = gson.toJson(jsonResponse);
            }
            else if (acceptHeaderValue != null && acceptHeaderValue.contains("application/xml")) {
                responseContent = "<files>\n";
                for (File file : files) {
                    responseContent += "\t<file>\n";
                    responseContent += "\t\t<name>" + file.getName() + "</name>\n";
                    responseContent += "\t\t<size>" + file.length() + "</size>\n";
                    responseContent += "\t</file>\n";
                }
                responseContent += "</files>";
            } else {
                responseContent = "Files:\n";
                for (File file : files) {
                    responseContent += file.getName() + " (" + file.length() + " bytes)\n";
                }
            }

            String contentType;
            if (acceptHeaderValue != null && acceptHeaderValue.contains("application/xml")) {
                contentType = "application/xml";
            } else if (acceptHeaderValue != null && acceptHeaderValue.contains("application/html")) {
                contentType = "application/html";
            }else {
                contentType = "application/text/plain";
            }

            sendResponseWithContentType(200, "OK", responseContent, contentType, out, isVerbose);
        } else {
            sendResponse(404, "Not Found", "Directory not found", out, isVerbose);
        }
    }

    private static void sendResponseWithContentType(int statusCode, String statusText, String content, String contentType, OutputStream out, boolean isVerbose) throws IOException {
        String response;
        if (isVerbose) {
            response = "HTTP/1.1 " + statusCode + " " + statusText + "\r\n";
            response += "Server: CNAssgn2LocalHTTPFileServer\r\n";
            response += "Date: " + new Date() + "\r\n";
            response += "Content-Type: " + contentType + "\r\n";
            response += "Content-Length: " + content.length() + "\r\n";
            response += "Connection: close\r\n";
            response += "Access-Control-Allow-Origin: *\r\n";
            response += "Access-Control-Allow-Credentials: true\r\n";
            response += "\r\n" + content;
        } else {
            response = "HTTP/1.1 " + statusCode + " " + statusText + "\r\n";
            response += "Date: " + new Date() + "\r\n";
            response += "Content-Type: " + contentType + "\r\n";
            response += "Content-Length: " + content.length() + "\r\n";
            response += "\r\n" + content;
        }

        out.write(response.getBytes());
    }



    private static void processServeFileRequest(String filePath, OutputStream out, boolean isVerbose) throws IOException {
        File file = new File(filePath);

        if (file.exists()) {
            if (isVerbose) {
                sendVerboseFileResponse(200, "OK", file, out);
            } else {
                sendFileResponse(200, "OK", file, out);
            }
        } else {
            sendResponse(404, "Not Found", "File not found", out, isVerbose);
        }
    }

    private static void sendFileResponse(int statusCode, String statusText, File file, OutputStream out) throws IOException {
        FileInputStream fileInputStream = new FileInputStream(file);
        byte[] buffer = new byte[1024];
        int bytesRead;

        String response = "HTTP/1.1 " + statusCode + " " + statusText + "\r\n";
        response += "Content-Length: " + file.length() + "\r\n";
        response += "\r\n";
        out.write(response.getBytes());

        while ((bytesRead = fileInputStream.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
        }

        fileInputStream.close();
    }

    private static void sendVerboseFileResponse(int statusCode, String statusText, File file, OutputStream out) throws IOException {
        FileInputStream fileInputStream = new FileInputStream(file);
        byte[] buffer = new byte[1024];
        int bytesRead;

        String response = "HTTP/1.1 " + statusCode + " " + statusText + "\r\n";
        response += "Server: CNAssgn2LocalHTTPServer\r\n";
        response += "Date: " + new Date() + "\r\n";
        response += "Content-Type: application/octet-stream\r\n";
        response += "Content-Length: " + file.length() + "\r\n";
        response += "Connection: close\r\n";
        response += "Access-Control-Allow-Origin: *\r\n";
        response += "Access-Control-Allow-Credentials: true\r\n";
        response += "\r\n";
        out.write(response.getBytes());

        while ((bytesRead = fileInputStream.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
        }

        fileInputStream.close();
    }
}
