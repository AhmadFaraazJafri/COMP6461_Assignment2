package org.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

public class httpfs {

    public static void main(String[] args) {

        Map<String, String> headers = new HashMap<>();
        String serverUrl = "http://localhost:8081";
        String dir = null;
        boolean isVerbose = false;
        if (args.length < 1) {
            System.out.println("\n httpfs [method:get/post] [-v] [-p PORT] [-d PATH-TO-DIR]");
            return;
        }

        String method = args[0].toUpperCase();
        String path = "/";
        String requestBody = "";

        if (method.equals("GET/") && method.endsWith("/")) {
            method = "GET";
        } else if (method.startsWith("GET/")) {
            String[] newPath = method.split("/");
            path = "/" + newPath[1];
            method = "GET";
        }

        if (method.startsWith("POST/")) {
            String[] newPath = method.split("/");
            path = "/" + newPath[1];
            method = "POST";
        }

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-v")) {
                isVerbose = true;
            } else if (args[i].equals("-p") && i + 1 < args.length) {
                int port = Integer.parseInt(args[i + 1]);
                serverUrl = "http://localhost:" + port;
                i++;
            } else if (args[i].equals("-d") && i + 1 < args.length) {
                dir = args[i + 1];
                i++;
            } else if (args[i].equals("-h") && i + 1 < args.length) {
                while (i + 1 < args.length) {
                    String[] headerParts = args[++i].split(":");
                    if (headerParts.length == 2) {
                        headers.put(headerParts[0].trim(), headerParts[1].trim());
                    }
                    if (i < args.length || "-v".equals(args[i + 1]) || "-d".equals(args[i + 1])) {
                        break;
                    }
                }
            } else {
                requestBody = args[i];
            }
        }

        try {

            if (isVerbose) {
                headers.put("Verbose", "true");
            }

            String response = sendRequest(serverUrl, method, dir, path, requestBody, headers);
            System.out.println("Response:\n" + response);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static String sendRequest(String url, String method, String dir, String path, String requestBody, Map<String, String> headers) throws IOException, URISyntaxException {
        URI uri = new URI(url);
        String host = uri.getHost();
        int port = uri.getPort();
        if (port == -1) {
            port = 8080;
        }

        try (Socket socket = new Socket(host, port);
             OutputStream out = socket.getOutputStream();
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {


            String request = method + " " + path + " HTTP/1.1\r\n";
            request += "Host: " + host + "\r\n";
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                request += entry.getKey() + ": " + entry.getValue() + "\r\n";
            }
            if (requestBody != null) {
                request += "Content-Length: " + requestBody.length() + "\r\n";
            }
            if (dir != null) {
                request += "dir: " + dir + "\r\n";
            }
            request += "Request-Type: " + "httpfs" + "\r\n";

            request += "\r\n";

            if (requestBody != null) {
                request += requestBody;
            }
            out.write(request.getBytes());
            out.flush();


            StringBuilder response = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine).append("\n");
            }

            return response.toString();
        }
    }
}
