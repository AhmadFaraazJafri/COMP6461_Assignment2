package org.example;

import com.google.gson.*;

import java.io.*;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class HttpServer {

    public static void main(String[] args) {
    }

    public static byte[] handleRequest(String method, String path, Map<String, String> headers, OutputStream out, boolean isVerbose, String inlinedata) throws IOException {

        if ("GET".equalsIgnoreCase(method)) {
            return (processGETRequest(path, headers, out, isVerbose));
        } else if ("POST".equalsIgnoreCase(method)) {
            return (processPOSTRequest(path, headers, inlinedata, out, isVerbose));
        } else {
            return (sendResponse(501, "Not Implemented", "HTTP method not supported", out, isVerbose));
        }
    }


    private static byte[] processGETRequest(String path, Map<String, String> headers, OutputStream out, boolean isVerbose) throws IOException {
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
            return (sendVerboseJSONResponse(200, "OK", jsonResponse));
        } else {
            return (sendJSONResponse(200, "OK", jsonResponse));
        }
    }

    private static byte[] processPOSTRequest(String path, Map<String, String> headers, String requestBody, OutputStream out, boolean isVerbose) throws IOException {
        String host = headers.getOrDefault("Host", "localhost");
        String userAgent = headers.getOrDefault("User-Agent", "Java-http-client/21.0.1");

//        sout

        int contentLength = Integer.parseInt(headers.getOrDefault("Content-Length", "0"));



        String requestBodyContent = new String(requestBody);

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
            return (sendVerboseJSONResponse(200, "OK", jsonResponse));
        } else {
            return (sendJSONResponse(200, "OK", jsonResponse));
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

    public static byte[] sendJSONResponse(int statusCode, String statusText, JsonObject json) throws IOException {
        String jsonString = json.toString();
        String response = "HTTP/1.1 " + statusCode + " " + statusText + "\r\n";
        response += "Date: " + new Date() + "\r\n";
        response += "Content-Type: application/json\r\n";
        response += "Content-Length: " + jsonString.length() + "\r\n";
        response += "\r\n" + jsonString;

//        System.out.println(response);
        return response.getBytes();
    }


    public static byte[] sendVerboseJSONResponse(int statusCode, String statusText, JsonObject json) throws IOException {
        String response = "HTTP/1.1 " + statusCode + " " + statusText + "\r\n";
        response += "Server: CNAssgn2LocalHTTPServer\r\n";
        response += "Date: " + new Date() + "\r\n";
        response += "Content-Type: application/json\r\n";
        response += "Content-Length: " + json.toString().length() + "\r\n";
        response += "Connection: close\r\n";
        response += "Access-Control-Allow-Origin: *\r\n";
        response += "Access-Control-Allow-Credentials: true\r\n";
        response += "\r\n" + json.toString();

//        System.out.println(response);
        return response.getBytes();
    }

    public static byte[] sendResponse(int statusCode, String statusText, String content, OutputStream out, boolean isVerbose) throws IOException {
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

//        System.out.println(response);
        return response.getBytes();
    }

}
