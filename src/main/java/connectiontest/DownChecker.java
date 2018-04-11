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

    private String proxyToString(Proxy proxy) {
        String proxyStr = proxy.address().toString();
        int semicolonIdx = proxyStr.indexOf(':');
        if (semicolonIdx == -1)
            return proxyStr;
        String ip = proxyStr.substring(0, semicolonIdx);
        return proxyStr + " (" + dbConnection.getCountry(IsDownCheckHelper.ipStringToLong(ip)) + ")";
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
        long haveChecked = dbConnection.accessedLastNMinutes(hostName, 120);
        return message + "\n\n" + "Checked last two hours by "
                + haveChecked + ((haveChecked % 10 == 1) ? " user." : " users.");
    }

    public String fullCheck() {
        dbConnection.setUrlAccessed(hostName, userId);

        StringBuilder message = new StringBuilder();
        message.append("Website: ").append(hostName).append("\nServers` IPs: ");
        if (addresses == null)
            return message.toString() + "none found";
        message.append('\n');
        // print server IPs
        Stream.of(addresses).forEach(ip -> message.append(ip.getHostAddress()).append('\n'));

        message.append("Your proxies: ");
        List<Proxy> proxies = dbConnection.getUserProxyList(userId);
        if (proxies.isEmpty()) {
            message.append("none");
        } else {
            proxies.forEach(proxy -> message.append('\n').append(proxyToString(proxy)));
        }
        message.append('\n');
        for (InetAddress address : addresses) {
            message.append("Trying ").append(address.getHostAddress()).append(":80...\n");
            message.append("Without proxy: ").append(
                    ( (IsDownCheckHelper.available(address, timeOut)) ? "OK" : "FAILED")
            );
            for (Proxy proxy : proxies) {
                message.append("\nWith proxy ").append(proxyToString(proxy)).append(": ").append(
                        ( (IsDownCheckHelper.availableWithProxy(address, timeOut, proxy)) ? "OK" : "FAILED")
                );
            }
            message.append('\n');
        }
        long usersChecked = dbConnection.accessedLastNMinutes(hostName, 60*24);
        message.append("\nChecked last 24 hours by ").append(usersChecked).append(" user");
        // plural
        if (usersChecked % 10 != 1) {
            message.append('s');
        }
        return message.toString();
    }

}
