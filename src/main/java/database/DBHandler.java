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
    private Connection conn = null;

    private static final String selectProxyString = "SELECT proxyList FROM " + USER_TABLE + " WHERE userId=?";
    private static final String updateProxyString = "UPDATE " + USER_TABLE + " SET proxyList=proxyList || ',' || ? WHERE userId=?";
    private static final String selectTimeStampString = "SELECT timeStamp FROM " + URL_TABLE + " WHERE servAddr=?";
    private static final String insertTimeStampString = "INSERT INTO " + URL_TABLE + " VALUES (?,?,?)";
    private static final String selectTimeoutString = "SELECT customTimeout FROM " + USER_TABLE + " WHERE userId=?";
    private static final String updateTimeoutString = "UPDATE " + USER_TABLE + " SET customTimeout=? WHERE userId=?";
    private static final String insertUserString = "INSERT INTO " + USER_TABLE + " VALUES (?, ?, ?)";


    public DBHandler() {
        try {
            conn = DriverManager.getConnection("jdbc:sqlite:" + DB_FILENAME);
            Statement createIfAbsent = conn.createStatement();
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
        } catch (SQLException connFailed) {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException closeFailed) {
                    // meh, nothing will help
                }
            }
        } // catch connFailed
    }

    public boolean addUser(long userId) {
        try {
            PreparedStatement insertUserQuery = conn.prepareStatement(insertUserString);
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
            PreparedStatement selectProxyQuery = conn.prepareStatement(selectProxyString);
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
            PreparedStatement updateProxyQuery = conn.prepareStatement(updateProxyString);
            updateProxyQuery.setString(1, proxy);
            updateProxyQuery.setLong(2, userId);
            updateProxyQuery.executeUpdate();
            return true;
        } catch (SQLException addFailed) {
            return false;
        }
    }

    // List of UNIX timestamps (millis from Epoch)
    private List<Long> getLastAccessTimes(String url) {
        try {
            PreparedStatement selectTimeStampQuery = conn.prepareStatement(selectTimeStampString);
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
            PreparedStatement insertTimeStampQuery = conn.prepareStatement(insertTimeStampString);
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
            PreparedStatement setTimeoutQuery = conn.prepareStatement(updateTimeoutString);
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
            PreparedStatement selectTimeoutQuery = conn.prepareStatement(selectTimeoutString);
            selectTimeoutQuery.setLong(1, userId);
            ResultSet resultSet = selectTimeoutQuery.executeQuery();
            return resultSet.getInt("customTimeout");
        } catch (SQLException insertFailed) {
            return CONNECTION_WAIT_MILLIS;
        }
    }




}
