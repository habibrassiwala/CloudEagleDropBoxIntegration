package test;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import com.sun.net.httpserver.*;

public class DropboxTeamGetInfo {
    private static final String AUTHORIZE_URL = "https://www.dropbox.com/oauth2/authorize";
    private static final String TOKEN_URL = "https://api.dropboxapi.com/oauth2/token";
    private static final int PORT = 8080;
    private static final String REDIRECT_PATH = "/callback";

    public static void main(String[] args) throws Exception {
        
        String clientId = "l4mfyndfqygzish";
        String clientSecret = "p0nmjx3utcrcko4";

        // Build OAuth authorize URL
        String redirectUri = "http://localhost:" + PORT + REDIRECT_PATH;

        String authUrl = AUTHORIZE_URL + "?" +
                "client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8) +
                "&response_type=code" +
                "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) ;

        // Start  HTTP server to receive the OAuth callback
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        final String[] codeHolder = new String[1];

        server.createContext(REDIRECT_PATH, exchange -> {
            String query = exchange.getRequestURI().getQuery();
            int codeStart= query.indexOf("=")+1;
            String code = query.substring(codeStart);
            codeHolder[0] = code;
            String resp = "<html><body><h2>Authorization received you may close this window.</h2></body></html>";
            exchange.sendResponseHeaders(200, resp.getBytes().length);
            try(OutputStream os = exchange.getResponseBody()){
                os.write(resp.getBytes());
            }
        });
        server.setExecutor(null);
        server.start();


            System.out.println("Please open the following URL in your browser:\n" + authUrl);

        // Wait for code (simple polling)
        System.out.println("Waiting for OAuth callback...");
        int wait = 0;
        while (codeHolder[0] == null && wait < 300) { Thread.sleep(500); wait++; }
        server.stop(0);

        if (codeHolder[0] == null) {
            System.err.println("Did not receive authorization code in time.");
            System.exit(2);
        }
        String code = codeHolder[0];
        System.out.println("Got code: " + code);

        // Exchange code for token
        HttpClient client = HttpClient.newHttpClient();
        String form = "code=" + URLEncoder.encode(code, StandardCharsets.UTF_8) +
                      "&grant_type=authorization_code" +
                      "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8) +
                      "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8) +
                      "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);

        HttpRequest tokenReq = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        HttpResponse<String> tokenResp = client.send(tokenReq, HttpResponse.BodyHandlers.ofString());
        if (tokenResp.statusCode() != 200) {
            System.err.println("Token exchange failed: " + tokenResp.statusCode() + " " + tokenResp.body());
            System.exit(4);
        }
        // naive JSON parse to get access_token
        String body = tokenResp.body();
        String accessToken = extractAccessToken(body);
        System.out.println("Token Resp :" + body);
        System.out.println("Access Token : " + accessToken);
        // Call team API
        HttpRequest getInfoReq = HttpRequest.newBuilder()
                .uri(URI.create("https://api.dropboxapi.com/2/team/get_info"))
                .header("Authorization", "Bearer " + accessToken)
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        
        System.out.println(getInfoReq);

        HttpResponse<String> getInfoResp = client.send(getInfoReq, HttpResponse.BodyHandlers.ofString());
        System.out.println("team/get_info response (status " + getInfoResp.statusCode() + "):");
        System.out.println(getInfoResp.body().toString());
    }

    private static String extractAccessToken(String json) {
    	int firstQuoteIndex = json.indexOf('"');
    	int secondQuoteIndex = json.indexOf('"', firstQuoteIndex + 1);
    	int thirdQuoteIndex = json.indexOf('"', secondQuoteIndex + 1);
    	int fourthQuoteIndex = json.indexOf('"', thirdQuoteIndex + 1);
        return json.substring(thirdQuoteIndex+1, fourthQuoteIndex);
    }
}
