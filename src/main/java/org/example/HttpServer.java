package org.example;

import com.google.gson.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class HttpServer {

    public static void main(String[] args) {
        int port = 8080;
        try {
            ServerSocket serverSocket = new ServerSocket(port);
            System.out.println("Server is listening on port " + port);

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

                if ("GET".equalsIgnoreCase(method)) {
                    processGETRequest(path, headers, out, isVerbose);
                } else if ("POST".equalsIgnoreCase(method)) {
                    processPOSTRequest(path, headers, clientSocket.getInputStream(), out, isVerbose);
                } else {
                    sendResponse(501, "Not Implemented", "HTTP method not supported", out, isVerbose);
                }
            }
        }

        in.close();
        out.close();
        clientSocket.close();
    }

    private static void processGETRequest(String path, Map<String, String> headers, OutputStream out, boolean isVerbose) throws IOException {
        Map<String, String> queryParameters = getQueryParameters(path);

        String host = headers.getOrDefault("Host", "localhost");
        String userAgent = headers.getOrDefault("User-Agent", "Java-http-client/21.0.1");

        JsonObject jsonResponse = new JsonObject();

        JsonObject args = new JsonObject();
        for (Map.Entry<String, String> entry : queryParameters.entrySet()) {
            args.add(entry.getKey(), new JsonPrimitive(entry.getValue()));
        }

        jsonResponse.add("args", args);

        JsonObject headerInfo = new JsonObject();
        headerInfo.addProperty("Host", host);
        headerInfo.addProperty("User-Agent", userAgent);
        jsonResponse.add("headers", headerInfo);

        jsonResponse.addProperty("url", "http://localhost" + path);

        if (isVerbose) {
            sendVerboseJSONResponse(200, "OK", jsonResponse, out);
        } else {
            sendJSONResponse(200, "OK", jsonResponse, out);
        }
    }

    private static void processPOSTRequest(String path, Map<String, String> headers, InputStream requestBody, OutputStream out, boolean isVerbose) throws IOException {
        String host = headers.getOrDefault("Host", "localhost");
        String userAgent = headers.getOrDefault("User-Agent", "Java-http-client/21.0.1");

        StringBuilder requestHeaders = new StringBuilder();
        String line;
        while ((line = readLine(requestBody)) != null) {
            if (line.isEmpty()) {
                break;
            }
            requestHeaders.append(line).append("\r\n");
        }

        StringBuilder requestBodyContent = new StringBuilder();
        while ((line = readLine(requestBody)) != null) {
            requestBodyContent.append(line);
        }

        JsonObject requestBodyJson = parseJSON(requestBodyContent.toString());

        JsonObject jsonResponse = new JsonObject();
        jsonResponse.add("args", new JsonObject());
        JsonObject headerInfo = new JsonObject();
        headerInfo.addProperty("Host", host);
        headerInfo.addProperty("User-Agent", userAgent);
        jsonResponse.add("headers", headerInfo);

        if (isVerbose) {
            jsonResponse.add("data", requestBodyJson);
            jsonResponse.addProperty("url", "http://" + host + path);
        }

        if (isVerbose) {
            sendVerboseJSONResponse(200, "OK", jsonResponse, out);
        } else {
            sendJSONResponse(200, "OK", jsonResponse, out);
        }
    }

    private static String readLine(InputStream inputStream) throws IOException {
        StringBuilder line = new StringBuilder();
        int c;
        while ((c = inputStream.read()) != -1) {
            if (c == '\r') {
                continue;
            }
            if (c == '\n') {
                break;
            }
            line.append((char) c);
        }
        if (line.length() == 0) {
            return null;
        }
        return line.toString();
    }


    private static JsonObject parseJSON(String jsonContent) {
        JsonParser jsonParser = new JsonParser();
        return jsonParser.parse(jsonContent).getAsJsonObject();
    }

    private static Map<String, String> getQueryParameters(String path) {
        Map<String, String> queryParameters = new HashMap<>();
        int questionMarkIndex = path.indexOf("?");
        if (questionMarkIndex != -1) {
            String queryString = path.substring(questionMarkIndex + 1);
            String[] parameterPairs = queryString.split("&");
            for (String parameterPair : parameterPairs) {
                String[] keyValue = parameterPair.split("=");
                if (keyValue.length == 2) {
                    queryParameters.put(keyValue[0], keyValue[1]);
                }
            }
        }
        return queryParameters;
    }

    public static void sendJSONResponse(int statusCode, String statusText, JsonObject json, OutputStream out) throws IOException {
        String jsonString = json.toString();
        String response = "HTTP/1.1 " + statusCode + " " + statusText + "\r\n";
        response += "Date: " + new Date() + "\r\n";
        response += "Content-Type: application/json\r\n";
        response += "Content-Length: " + jsonString.length() + "\r\n";
        response += "\r\n" + jsonString;

        out.write(response.getBytes());
    }


    public static void sendVerboseJSONResponse(int statusCode, String statusText, JsonObject json, OutputStream out) throws IOException {
        String response = "HTTP/1.1 " + statusCode + " " + statusText + "\r\n";
        response += "Server: CNAssgn2LocalHTTPServer\r\n";
        response += "Date: " + new Date() + "\r\n";
        response += "Content-Type: application/json\r\n";
        response += "Content-Length: " + json.toString().length() + "\r\n";
        response += "Connection: close\r\n";
        response += "Access-Control-Allow-Origin: *\r\n";
        response += "Access-Control-Allow-Credentials: true\r\n";
        response += "\r\n" + json.toString();

        out.write(response.getBytes());
    }

    public static void sendResponse(int statusCode, String statusText, String content, OutputStream out, boolean isVerbose) throws IOException {
        String response = "HTTP/1.1 " + statusCode + " " + statusText + "\r\n";
        response += "Date: " + new Date() + "\r\n";
        response += "Content-Type: text/plain\r\n";
        response += "Content-Length: " + content.length() + "\r\n";
        response += "\r\n" + content;

        out.write(response.getBytes());
    }
}
