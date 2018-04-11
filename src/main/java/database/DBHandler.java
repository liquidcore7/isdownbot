package database;

import connectiontest.IsDownCheckHelper;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.sql.*;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static cfg.Configuration.*;

public class DBHandler {
    private Connection botDbConnection = null;
    private Connection ip2countryDbConnection = null;

    private static final String selectProxyString = "SELECT proxyList FROM " + USER_TABLE + " WHERE userId=?";
    private static final String updateProxyString = "UPDATE " + USER_TABLE + " SET proxyList=proxyList || ',' || ? WHERE userId=?";
    private static final String selectTimeStampString = "SELECT timeStamp FROM " + URL_TABLE + " WHERE servAddr=?";
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
            if (botDbConnection != null) {
                try {
                    botDbConnection.close();
                } catch (SQLException closeFailed) {
                    // meh, nothing will help
                }
            }
            if (ip2countryDbConnection != null) {
                try {
                    ip2countryDbConnection.close();
                } catch (SQLException closeFailed) {
                }
            }        // catch connFailed
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
            return true;
        } catch (SQLException insertFailed) {
            return false;
        }
    }

    public List<Proxy> getUserProxyList(long userId) {
        try {
            PreparedStatement selectProxyQuery = botDbConnection.prepareStatement(selectProxyString);
            selectProxyQuery.setLong(1, userId);
            ResultSet result = selectProxyQuery.executeQuery();
            return Stream.of(
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

        } catch (SQLException getFailed) {
            return Collections.emptyList();
        }
    }

    public boolean addUserProxy(long userId, String proxy) {
        if (!IsDownCheckHelper.checkProxy(proxy)) {
            return false;
        }
        try {
            PreparedStatement updateProxyQuery = botDbConnection.prepareStatement(updateProxyString);
            updateProxyQuery.setString(1, proxy);
            updateProxyQuery.setLong(2, userId);
            updateProxyQuery.executeUpdate();
            return true;
        } catch (SQLException addFailed) {
            return false;
        }
    }

    public boolean clearUserProxy(long userId) {
        try {
            PreparedStatement deleteProxyQuery = botDbConnection.prepareStatement(deleteProxyString);
            deleteProxyQuery.setLong(1, userId);
            deleteProxyQuery.executeUpdate();
            return true;
        } catch (SQLException deleteFailed) {
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
            return resultSet;

        } catch (SQLException getFailed) {
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
            return true;
        } catch (SQLException insertFailed) {
            return false;
        }
    }

    public boolean setCustomTimeout(int newTimeout, long userId) {
        try {
            PreparedStatement setTimeoutQuery = botDbConnection.prepareStatement(updateTimeoutString);
            setTimeoutQuery.setInt(1, newTimeout);
            setTimeoutQuery.setLong(2, userId);
            setTimeoutQuery.executeUpdate();
            return true;
        } catch (SQLException updateFailed) {
            return false;
        }
    }

    public int getCustomTimeout(long userId) {
        try {
            PreparedStatement selectTimeoutQuery = botDbConnection.prepareStatement(selectTimeoutString);
            selectTimeoutQuery.setLong(1, userId);
            ResultSet resultSet = selectTimeoutQuery.executeQuery();
            return resultSet.getInt("customTimeout");
        } catch (SQLException insertFailed) {
            return CONNECTION_WAIT_MILLIS;
        }
    }

    public String getCountry(long ipValue) {
        try {
            PreparedStatement selectCountryQuery = ip2countryDbConnection.prepareStatement(selectCountryString);
            selectCountryQuery.setLong(1, ipValue);
            ResultSet country = selectCountryQuery.executeQuery();
            return country.getString("country");
        } catch (SQLException selectFailed) {
            return "Unknown";
        }
    }




}
