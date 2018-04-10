package connectiontest;

import database.DBHandler;

import java.net.InetAddress;
import java.net.Proxy;
import java.net.UnknownHostException;
import java.util.List;
import java.util.stream.Stream;

public class DownChecker {
    private DBHandler dbConnection;
    private String hostName;
    private InetAddress[] addresses = null;
    private int timeOut;
    private long userId;

    public DownChecker(DBHandler dbConnection, String hostName, long telegramUserId) {
        this.dbConnection = dbConnection;
        this.hostName = IsDownCheckHelper.parseUrl(hostName);
        this.userId = telegramUserId;
        timeOut = dbConnection.getCustomTimeout(telegramUserId);
        try {
            addresses = IsDownCheckHelper.getByHostName(this.hostName);
        } catch (UnknownHostException dnsLookupFailed) {
            // address will be null
        }

    }

    // checks just one ip without proxy
    public String quickCheck() {
        dbConnection.setUrlAccessed(hostName, userId);
        String message = "Website: " + hostName + "\nServer IP: " + (
                (addresses == null) ? "Not found" : addresses[0].getHostAddress()
        );
        if (addresses != null) {
            message += "\nServer status: " + (
                    IsDownCheckHelper.available(addresses[0], timeOut) ? "On" : "Off"
            ) + "line";
        }
        return message + "\n\n" + "Checked last two hours by " +
                dbConnection.accessedLastNMinutes(hostName, 120) + " users.";
    }

    public String fullCheck() {
        dbConnection.setUrlAccessed(hostName, userId);
        StringBuilder message = new StringBuilder();
        message.append("Website: ").append(hostName).append("\nServers` IPs: ");
        if (addresses == null)
            return message.toString() + "none found";
        message.append('\n');
        Stream.of(addresses).forEach(ip -> message.append(ip.getHostAddress()).append('\n'));
        message.append("Your proxies: ");
        List<Proxy> proxies = dbConnection.getUserProxyList(userId);
        if (proxies.isEmpty()) {
            message.append("none");
        } else {
            proxies.forEach(proxy -> message.append('\n').append(proxy));
        }
        message.append('\n');
        for (InetAddress address : addresses) {
            message.append("Trying ").append(address.getHostAddress()).append(":80...\n");
            message.append("Without proxy: ").append(
                    ( (IsDownCheckHelper.available(address, timeOut)) ? "OK" : "FAILED")
            );
            for (Proxy proxy : proxies) {
                message.append("\nWith proxy ").append(proxy).append(": ").append(
                        ( (IsDownCheckHelper.availableWithProxy(address, timeOut, proxy)) ? "OK" : "FAILED")
                );
            }
            message.append('\n');
        }
        return message.toString();
    }

}
