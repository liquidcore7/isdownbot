package connectiontest;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static cfg.Configuration.CONNECTION_WAIT_MILLIS;

public class IsDownCheckHelper {

    private final static String googleDnsApiPrefix = "https://dns.google.com/resolve?name=";
    private final static String ipV4Regex = "\\d{1,3}.\\d{1,3}.\\d{1,3}.\\d{1,3}";
    private static InetAddress examplePage;
    static {
        try {
            examplePage = InetAddress.getByName("example.com");
        } catch (UnknownHostException willNotHappen) {}
    }
    private static final Logger logger = Logger.getLogger(IsDownCheckHelper.class.getName());

    // TODO: regex?
    static String parseUrl(String url) throws IllegalArgumentException {
        if (url.startsWith("http")) {
            int secondSlashPos = url.indexOf('/') + 2;
            if (secondSlashPos >= url.length() || secondSlashPos == 1) {// -1 (not found) + 2 == 1
                logger.warning("Invalid url parsing attempt: " + url);
                throw new IllegalArgumentException("Not a valid url, check the http prefix");
            }
            url = url.substring(secondSlashPos);
        }
        int path = url.indexOf('/');
        if (path != -1)
            url = url.substring(0, path);
        return url;
    }

    static long ipStringToLong(String ipStr) {
        long accumulator = 0L;
        long multiplier = 1L;
        String[] ipChunks = ipStr.split("\\.");
        for (int i = ipChunks.length - 1; i >= 0; i--) {
            accumulator += Integer.parseInt(ipChunks[i]) * multiplier;
            multiplier *= 256;
        }
        return accumulator;
    }

    static boolean isIPv4(String ip) {
        return ip.matches(ipV4Regex);
    }

    static InetAddress[] getByHostName(String hostName) throws UnknownHostException {
        try {
            URL apiCall = new URL(googleDnsApiPrefix + URLEncoder.encode(hostName, StandardCharsets.UTF_8.toString()));
            JSONTokener responseText = new JSONTokener(apiCall.openConnection().getInputStream());
            JSONObject response = new JSONObject(responseText);
            JSONArray responseData = response.getJSONArray("Answer");

            InetAddress[] serviceIps =
                    StreamSupport.stream(responseData.spliterator(), false) //array to stream of elements
                    .map(o -> ((JSONObject) o).getString("data")) // grab "data" from each element
                    .filter(IsDownCheckHelper::isIPv4)
                    .map(ipStr -> {
                        try {
                            return InetAddress.getByName(ipStr);
                        } catch (UnknownHostException e) {
                            return null;
                        }
                    }).filter(Objects::nonNull)
                    .toArray(InetAddress[]::new);
            logger.info("Got IPs for " + hostName + " from GoogleDNS: " + (serviceIps.length > 0));
            return serviceIps;
        } catch (Exception googleDnsFailed) {
            // use system dns
            logger.info("Google DNS failed for " + hostName + ", trying system DNS...");
            return Stream.of(InetAddress.getAllByName(hostName))
                    .filter(addr -> isIPv4(addr.getHostAddress()))
                    .toArray(InetAddress[]::new);
        }
    }

    static boolean available(InetAddress addr, int timeOutMs) {
        try {
            Socket sock = new Socket();
            sock.connect(new InetSocketAddress(addr, 80), timeOutMs);
            boolean success = sock.isConnected();
            logger.info("Address " + addr.getHostAddress() + "available: " + success);
            return success;
        } catch (Exception notConnected) {
            logger.info("Unreachable address: " + addr.getHostAddress() + "(" + addr.getHostName() + "): "
                                                                                + notConnected.getMessage());
            return false;
        }
        catch (InternalError notConnected) {
            logger.warning("Unreachable address " + addr.getHostAddress() + " thrown InternalError: " +
                                                                                notConnected.getMessage());
            return false;
        }
    }

    static boolean availableWithProxy(InetAddress addr, int timeOutMs, Proxy proxy) {
        try {
            Socket sock = new Socket(proxy);
            sock.connect(new InetSocketAddress(addr, 80), timeOutMs);
            boolean success = sock.isConnected();
            logger.info("Address " + addr.getHostAddress() + " available via proxy@" + proxy.address().toString() +
                                                                                                    ": " + success);
            return success;
        } catch (Exception notConnected) {
            logger.info("Unreachable address " + addr.getHostAddress() + " via proxy@" + proxy.address().toString()
                                                                + ": " + notConnected.getMessage());
            return false;
        }
        catch (InternalError notConnected) {
            logger.warning("Unreachable address " + addr.getHostAddress() + " via proxy@" + proxy.address().toString()
                                    + " thrown InternalError: " + notConnected.getMessage());
            return false;
        }
    }

    public static boolean checkProxy(String proxy) {
        String[] ipPort = proxy.split(":");
        if (ipPort.length != 2) {
            logger.warning("Incorrect proxy string given: " + proxy);
            return false;
        }
        return availableWithProxy(examplePage, CONNECTION_WAIT_MILLIS*2,
                new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(ipPort[0], Integer.parseInt(ipPort[1]) ))
        );
    }

}
