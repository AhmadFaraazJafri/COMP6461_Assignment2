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
    }

    public static void handleRequest(String method, String path, Map<String, String> headers, OutputStream out, boolean isVerbose, Socket clientSocket) throws IOException {

        if ("GET".equalsIgnoreCase(method)) {
            processGETRequest(path, headers, out, isVerbose);
        } else if ("POST".equalsIgnoreCase(method)) {
            processPOSTRequest(path, headers, clientSocket.getInputStream(), out, isVerbose);
        } else {
            sendResponse(501, "Not Implemented", "HTTP method not supported", out, isVerbose);
        }
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

        jsonResponse.addProperty("url", path);

        System.out.println("REQUEST PARSED");
        if (isVerbose) {
            sendVerboseJSONResponse(200, "OK", jsonResponse, out);
        } else {
            sendJSONResponse(200, "OK", jsonResponse, out);
        }
    }

    private static void processPOSTRequest(String path, Map<String, String> headers, InputStream requestBody, OutputStream out, boolean isVerbose) throws IOException {
        String host = headers.getOrDefault("Host", "localhost");
        String userAgent = headers.getOrDefault("User-Agent", "Java-http-client/21.0.1");


        int contentLength = Integer.parseInt(headers.getOrDefault("Content-Length", "0"));

        char[] requestBodyData = new char[contentLength];
        int bytesRead = 0;
        while (bytesRead < contentLength) {
            int c = requestBody.read();
            if (c == -1) {
                break;
            }
            requestBodyData[bytesRead] = (char) c;
            bytesRead++;
        }

        String requestBodyContent = new String(requestBodyData);

        JsonObject requestBodyJson = parseJSON(requestBodyContent);

        JsonObject jsonResponse = new JsonObject();
        jsonResponse.add("args", new JsonObject());
        JsonObject headerInfo = new JsonObject();

        headerInfo.addProperty("Content-Length", contentLength);
        headerInfo.addProperty("Content-Type", (headers.getOrDefault("Content-Type", "application/json")));
        headerInfo.addProperty("Host", host);
        headerInfo.addProperty("User-Agent", userAgent);

        jsonResponse.add("headers", headerInfo);

        jsonResponse.addProperty("data", String.valueOf(requestBodyJson));
        jsonResponse.add("json", requestBodyJson);
        jsonResponse.addProperty("url", "http://" + host + path);



        if (isVerbose) {
            sendVerboseJSONResponse(200, "OK", jsonResponse, out);
        } else {
            sendJSONResponse(200, "OK", jsonResponse, out);
        }
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

        System.out.println(response);
//        out.write(response.getBytes());
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

        System.out.println(response);
//        out.write(response.getBytes());
    }

    public static void sendResponse(int statusCode, String statusText, String content, OutputStream out, boolean isVerbose) throws IOException {
        String response = "HTTP/1.1 " + statusCode + " " + statusText + "\r\n";
        response += "Date: " + new Date() + "\r\n";

        if (isVerbose) {
            response += "Server: CNAssgn2LocalHTTPServer\r\n";
            response += "Content-Type: application/json\r\n";
            response += "Content-Length: " + content.length() + "\r\n";
            response += "Connection: close\r\n";
            response += "Access-Control-Allow-Origin: *\r\n";
            response += "Access-Control-Allow-Credentials: true\r\n";
        } else {
            response += "Content-Type: text/plain\r\n";
            response += "Content-Length: " + content.length() + "\r\n";
        }

        response += "\r\n" + content;

        System.out.println(response);
//        out.write(response.getBytes());
    }

}
