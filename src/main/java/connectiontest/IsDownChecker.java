package connectiontest;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

class LogStruct {
    public String host;
    public String ip;
    public boolean available;
    public String currentProxy;

    public LogStruct(String host, String ip, boolean available, String currentProxy) {
        this.host = host;
        this.ip = ip;
        this.available = available;
        this.currentProxy = currentProxy;
    }
}

public class IsDownChecker {

    private String hostName;
    private int timeOutMs = 4000;
    private final static String googleDnsApiPrefix = "https://dns.google.com/resolve?name=";


    public IsDownChecker(String host) {
        this.hostName = host.substring(0, host.indexOf('/',
                host.startsWith("http") ? 7 : 0));
    }

    public IsDownChecker(String host, int timeOut) {
        this(host);
        this.timeOutMs = timeOut;
    }

    public void setTimeOut(int timeOut) {
        this.timeOutMs = timeOut;
    }

    public static InetAddress getByHostName(String hostName) throws UnknownHostException {
        try {
            URL apiCall = new URL(googleDnsApiPrefix + URLEncoder.encode(hostName, StandardCharsets.UTF_8.toString()));
            JSONTokener responseText = new JSONTokener(apiCall.openConnection().getInputStream());
            JSONObject response = new JSONObject(responseText);
            return InetAddress.getByName(
                    response.getJSONArray("Answer")
                            .getJSONObject(0)
                            .getString("data")
            );
        } catch (Exception googleDnsFailed) {
            // use system dns
            return InetAddress.getByName(hostName);
        }
    }

    public boolean available() {
        try {
            return getByHostName(hostName).isReachable(timeOutMs);
        } catch (IOException notConnected) {
            return false;
        }
    }

    public void createLog() {
        String filename = hostName.startsWith("http")
                ? hostName.substring(hostName.lastIndexOf('/'))
                : hostName;
        ConnectionLogger logger = new ConnectionLogger(filename);
        // TODO: add logging
    }



}
