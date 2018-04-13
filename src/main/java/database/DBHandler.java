package database;

import connectiontest.IsDownCheckHelper;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.sql.*;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static cfg.Configuration.*;

public class DBHandler {
    private Connection botDbConnection = null;
    private Connection ip2countryDbConnection = null;
    private static final Logger logger = Logger.getLogger(DBHandler.class.getName());

    private static final String selectProxyString = "SELECT proxyList FROM " + USER_TABLE + " WHERE userId=?";
    private static final String updateProxyString = "UPDATE " + USER_TABLE + " SET proxyList=proxyList || ',' || ? WHERE userId=?";
    private static final String selectTimeStampString = "SELECT timeStamp FROM " + URL_TABLE + " WHERE servAddr=?"; // TODO: WHERE timeStamp > x
    private static final String insertTimeStampString = "INSERT INTO " + URL_TABLE + " VALUES (?,?,?)";
    private static final String selectTimeoutString = "SELECT customTimeout FROM " + USER_TABLE + " WHERE userId=?";
    private static final String updateTimeoutString = "UPDATE " + USER_TABLE + " SET customTimeout=? WHERE userId=?";
    private static final String insertUserString = "INSERT INTO " + USER_TABLE + " VALUES (?, ?, ?)";
    private static final String deleteProxyString = "UPDATE " + USER_TABLE + " SET proxyList=\"\" WHERE userId=?";

    //http://www.ip2nation.com/ip2nation/Sample_Scripts/Output_Full_Country_Name
    private static final String selectCountryString = "SELECT c.country FROM ip2nationCountries c, ip2nation i"
                                                    + " WHERE i.ip < ? AND c.code = i.country"
                                                    + " ORDER BY i.ip DESC LIMIT 0,1";


    public DBHandler() {
        try {
            botDbConnection = DriverManager.getConnection("jdbc:sqlite:" + BOT_DB_FILENAME);
            Statement createIfAbsent = botDbConnection.createStatement();
            createIfAbsent.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + USER_TABLE + " (" +
                            "userId INTEGER PRIMARY KEY," +
                            "proxyList TEXT," +
                            "customTimeout INTEGER)"
            );
            createIfAbsent.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + URL_TABLE + " (" +
                            "servAddr TEXT NOT NULL," +
                            "userId INTEGER," +
                            "timeStamp INTEGER NOT NULL)"
            );
            createIfAbsent.close();

            ip2countryDbConnection = DriverManager.getConnection("jdbc:sqlite:" + COUNTRY_DB_FILENAME);
        } catch (SQLException connFailed) {

            logger.severe("Database connection failed: " + connFailed.getMessage());
            if (botDbConnection != null) {
                try {
                    logger.info("Closing " + BOT_DB_FILENAME + " connection...");
                    botDbConnection.close();
                } catch (SQLException closeFailed) {
                    logger.severe(BOT_DB_FILENAME + " closing failed: " + closeFailed.getMessage());
                }
            }
            if (ip2countryDbConnection != null) {
                try {
                    logger.info("Closing " + COUNTRY_DB_FILENAME + " connection...");
                    ip2countryDbConnection.close();
                } catch (SQLException closeFailed) {
                    logger.severe(COUNTRY_DB_FILENAME + " closing failed: " + closeFailed.getMessage());
                }
            }

        }
    }

    public boolean addUser(long userId) {
        try {
            PreparedStatement insertUserQuery = botDbConnection.prepareStatement(insertUserString);
            insertUserQuery.setLong(1, userId);
            insertUserQuery.setString(2, "");
            insertUserQuery.setInt(3, CONNECTION_WAIT_MILLIS);
            insertUserQuery.execute();
            insertUserQuery.close();
            logger.fine("Userid " + userId + " inserted into " + BOT_DB_FILENAME);
            return true;
        } catch (SQLException insertFailed) {
            logger.warning("Failed to insert userid " + userId + " into " + BOT_DB_FILENAME
                                                            + ": " + insertFailed.getMessage());
            return false;
        }
    }

    public List<Proxy> getUserProxyList(long userId) {
        try {
            PreparedStatement selectProxyQuery = botDbConnection.prepareStatement(selectProxyString);
            selectProxyQuery.setLong(1, userId);
            ResultSet result = selectProxyQuery.executeQuery();
            List<Proxy> userProxies = Stream.of(
                    result.getString("proxyList")
                            .split(","))
                    .filter(str -> !str.isEmpty())
                    .map(proxyStr -> {
                        String[] IpPortPair = proxyStr.split(":");
                        return IpPortPair.length == 2
                                ? InetSocketAddress.createUnresolved(IpPortPair[0], Integer.parseInt(IpPortPair[1]))
                                : null;
                    })
                    .filter(Objects::nonNull)
                    .map(inetAddr -> new Proxy(Proxy.Type.SOCKS, inetAddr))
                    .collect(Collectors.toList());
            logger.fine("Obtained " + userProxies.size() + " proxies for userid " + userId);
            return userProxies;
        } catch (SQLException getFailed) {
            logger.warning("Failed to get proxy list for userid " + userId + ": " + getFailed.getMessage()
                                                                        + "\nReturning empty list instead");
            return Collections.emptyList();
        }
    }

    public boolean addUserProxy(long userId, String proxy) {
        if (!IsDownCheckHelper.checkProxy(proxy)) {
            logger.warning("Proxy " + proxy + " unreachable, failed to add for userid " + userId);
            return false;
        }
        try {
            PreparedStatement updateProxyQuery = botDbConnection.prepareStatement(updateProxyString);
            updateProxyQuery.setString(1, proxy);
            updateProxyQuery.setLong(2, userId);
            updateProxyQuery.executeUpdate();
            logger.fine("Proxy " + proxy + " successfully set for userid " + userId);
            return true;
        } catch (SQLException addFailed) {
            logger.warning("Failed to add proxy " + proxy + " for userid " + userId + ": " + addFailed.getMessage());
            return false;
        }
    }

    public boolean clearUserProxy(long userId) {
        try {
            PreparedStatement deleteProxyQuery = botDbConnection.prepareStatement(deleteProxyString);
            deleteProxyQuery.setLong(1, userId);
            deleteProxyQuery.executeUpdate();
            logger.fine("Proxy for " + userId + " cleaned");
            return true;
        } catch (SQLException deleteFailed) {
            logger.warning("Failed to clear proxy for userid " + userId + ": " + deleteFailed.getMessage());
            return false;
        }
    }

    // List of UNIX timestamps (millis from Epoch)
    private List<Long> getLastAccessTimes(String url) {
        try {
            PreparedStatement selectTimeStampQuery = botDbConnection.prepareStatement(selectTimeStampString);
            selectTimeStampQuery.setString(1, url);
            ResultSet timeStamps = selectTimeStampQuery.executeQuery();
            List<Long> resultSet = new LinkedList<>();
            while (timeStamps.next()) {
                resultSet.add(timeStamps.getLong("timeStamp"));
            }
            logger.fine("Obtained " + resultSet.size() + " last accesses for " + url);
            return resultSet;

        } catch (SQLException getFailed) {
            logger.warning("Failed to fetch last access times for " + url + ": " + getFailed.getMessage());
            return Collections.emptyList();
        }
    }

    public long accessedLastNMinutes(String url, long minutes) {
        long now = System.currentTimeMillis();
        return getLastAccessTimes(url)
                .parallelStream()
                .filter(ts -> Math.abs(ts - now) < minutes * 1000 * 60) // minutes are converted to millis
                .count();
    }

    public boolean setUrlAccessed(String url, long userId) {
        try {
            PreparedStatement insertTimeStampQuery = botDbConnection.prepareStatement(insertTimeStampString);
            insertTimeStampQuery.setString(1, url);
            insertTimeStampQuery.setLong(2, userId);
            insertTimeStampQuery.setLong(3, System.currentTimeMillis());
            insertTimeStampQuery.executeUpdate();
            logger.fine("Added " + userId + " to " + url + "`s visitors");
            return true;
        } catch (SQLException insertFailed) {
            logger.warning("Failed to set " + url + " accessed: " + insertFailed.getMessage());
            return false;
        }
    }

    public boolean setCustomTimeout(int newTimeout, long userId) {
        try {
            PreparedStatement setTimeoutQuery = botDbConnection.prepareStatement(updateTimeoutString);
            setTimeoutQuery.setInt(1, newTimeout);
            setTimeoutQuery.setLong(2, userId);
            setTimeoutQuery.executeUpdate();
            logger.fine("Set custom timeout for " + userId + ": " + newTimeout);
            return true;
        } catch (SQLException updateFailed) {
            logger.warning("Failed to set new timeout for " + userId + ": " + updateFailed.getMessage());
            return false;
        }
    }

    public int getCustomTimeout(long userId) {
        try {
            PreparedStatement selectTimeoutQuery = botDbConnection.prepareStatement(selectTimeoutString);
            selectTimeoutQuery.setLong(1, userId);
            ResultSet resultSet = selectTimeoutQuery.executeQuery();
            logger.fine("Obtained custom timeout for userid " + userId);
            return resultSet.getInt("customTimeout");
        } catch (SQLException insertFailed) {
            logger.warning("Failed to obtain customTimeout value for userid " + userId
                                                        + ": " + insertFailed.getMessage());
            return CONNECTION_WAIT_MILLIS;
        }
    }

    public String getCountry(long ipValue) {
        try {
            PreparedStatement selectCountryQuery = ip2countryDbConnection.prepareStatement(selectCountryString);
            selectCountryQuery.setLong(1, ipValue);
            ResultSet country = selectCountryQuery.executeQuery();
            logger.fine("Transformed IPvalue " + ipValue + " to country name");
            return country.getString("country");
        } catch (SQLException selectFailed) {
            logger.warning("Failed to transform IPvalue " + ipValue + " to a country name: " + selectFailed.getMessage());
            return "Unknown";
        }
    }




}
