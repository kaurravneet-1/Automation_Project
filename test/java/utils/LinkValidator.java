package utils;

import java.net.HttpURLConnection;
import java.net.URI;

public class LinkValidator {

    public static int getStatus(String url) {
        try {
            HttpURLConnection connection =
                    (HttpURLConnection) new URI(url).toURL().openConnection();

            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000); // 10 sec
            connection.setReadTimeout(10000);    // 10 sec
            connection.connect();

            return connection.getResponseCode();

        } catch (Exception e) {
            return -1; // unreachable
        }
    }



    public static boolean isOk(int code) {
        return code >= 200 && code < 400;
    }

    public static String getStatusMessage(int code) {
        switch (code) {
            case -1:
                return "No Response (General Error)";
            case -3:
                return "SSL Handshake Failed (Insecure Certificate)";
            case -4:
                return "Timeout (Server Took Too Long)";
            case -5:
                return "Connection Refused";
            case -6:
                return "Unknown Host (Domain Not Found)";
            case -7:
                return "Connection Reset by Server";
            case -8:
                return "Connection Closed by Server";
            default:
                return (code >= 200 && code < 400) ? "OK" : "HTTP Error " + code;
        }
    }

	public static int getResponseCode(String url) {
		// TODO Auto-generated method stub
		return 0;
	}

	public static int getStatusCode(String url) {
		// TODO Auto-generated method stub
		return 0;
	}
}
