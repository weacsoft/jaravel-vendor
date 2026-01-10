package com.weacsoft.jaravel.cache;

import com.alibaba.fastjson.JSON;

import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class DatabaseCacheRepository implements CacheRepository {

    private final Connection connection;

    private final String table;

    private final long defaultTtl;

    public DatabaseCacheRepository(Connection connection) {
        this(connection, "cache", 3600);
    }

    public DatabaseCacheRepository(Connection connection, String table) {
        this(connection, table, 3600);
    }

    public DatabaseCacheRepository(Connection connection, String table, long defaultTtl) {
        this.connection = connection;
        this.table = table;
        this.defaultTtl = defaultTtl;
        ensureTableExists();
    }

    private void ensureTableExists() {
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            ResultSet tables = metaData.getTables(null, null, table, null);
            if (!tables.next()) {
                createTable();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check cache table existence", e);
        }
    }

    private void createTable() {
        String sql = String.format(
                "CREATE TABLE %s (" +
                        "key VARCHAR(255) PRIMARY KEY, " +
                        "value TEXT NOT NULL, " +
                        "expiration BIGINT NOT NULL" +
                        ")",
                table
        );

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create cache table", e);
        }
    }

    @Override
    public boolean put(String key, Object value) {
        return put(key, value, defaultTtl);
    }

    @Override
    public boolean put(String key, Object value, long ttl) {
        return put(key, value, ttl, TimeUnit.SECONDS);
    }

    @Override
    public boolean put(String key, Object value, long ttl, TimeUnit timeUnit) {
        long expiration = System.currentTimeMillis() / 1000 + timeUnit.toSeconds(ttl);
        String sql = String.format(
                "INSERT INTO %s (key, value, expiration) VALUES (?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE value = ?, expiration = ?",
                table
        );

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            String jsonValue = JSON.toJSONString(value);
            stmt.setString(1, key);
            stmt.setString(2, jsonValue);
            stmt.setLong(3, expiration);
            stmt.setString(4, jsonValue);
            stmt.setLong(5, expiration);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to put value into cache", e);
        }
    }

    @Override
    public Object get(String key) {
        String sql = String.format("SELECT value, expiration FROM %s WHERE key = ?", table);

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, key);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                long expiration = rs.getLong("expiration");
                long currentTime = System.currentTimeMillis() / 1000;

                if (expiration > 0 && expiration < currentTime) {
                    forget(key);
                    return null;
                }

                String jsonValue = rs.getString("value");
                return JSON.parse(jsonValue);
            }

            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get value from cache", e);
        }
    }

    @Override
    public boolean has(String key) {
        String sql = String.format("SELECT expiration FROM %s WHERE key = ?", table);

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, key);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                long expiration = rs.getLong("expiration");
                long currentTime = System.currentTimeMillis() / 1000;

                if (expiration > 0 && expiration < currentTime) {
                    forget(key);
                    return false;
                }

                return true;
            }

            return false;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check key existence", e);
        }
    }

    @Override
    public boolean forget(String key) {
        String sql = String.format("DELETE FROM %s WHERE key = ?", table);

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, key);
            return stmt.executeUpdate() >= 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete key from cache", e);
        }
    }

    @Override
    public boolean flush() {
        String sql = String.format("DELETE FROM %s", table);

        try (Statement stmt = connection.createStatement()) {
            return stmt.executeUpdate(sql) >= 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to flush cache", e);
        }
    }

    @Override
    public boolean putMany(Map<String, Object> values) {
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
        return true;
    }

    @Override
    public Map<String, Object> getMany(Collection<String> keys) {
        Map<String, Object> result = new HashMap<>();
        for (String key : keys) {
            Object value = get(key);
            if (value != null) {
                result.put(key, value);
            }
        }
        return result;
    }

    @Override
    public boolean forgetMany(Collection<String> keys) {
        if (keys.isEmpty()) {
            return true;
        }

        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) {
                placeholders.append(",");
            }
            placeholders.append("?");
        }

        String sql = String.format("DELETE FROM %s WHERE key IN (%s)", table, placeholders);

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            int index = 1;
            for (String key : keys) {
                stmt.setString(index++, key);
            }
            return stmt.executeUpdate() >= 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete multiple keys from cache", e);
        }
    }

    @Override
    public boolean add(String key, Object value) {
        if (has(key)) {
            return false;
        }
        return put(key, value);
    }

    @Override
    public boolean add(String key, Object value, long ttl) {
        if (has(key)) {
            return false;
        }
        return put(key, value, ttl);
    }

    @Override
    public boolean add(String key, Object value, long ttl, TimeUnit timeUnit) {
        if (has(key)) {
            return false;
        }
        return put(key, value, ttl, timeUnit);
    }

    @Override
    public long increment(String key) {
        return increment(key, 1);
    }

    @Override
    public long increment(String key, long value) {
        Object current = get(key);
        long newValue;
        if (current == null) {
            newValue = value;
        } else if (current instanceof Number) {
            newValue = ((Number) current).longValue() + value;
        } else {
            throw new IllegalArgumentException("Cannot increment non-numeric value");
        }
        put(key, newValue);
        return newValue;
    }

    @Override
    public long decrement(String key) {
        return decrement(key, 1);
    }

    @Override
    public long decrement(String key, long value) {
        return increment(key, -value);
    }

    public void cleanupExpired() {
        String sql = String.format("DELETE FROM %s WHERE expiration > 0 AND expiration < ?", table);

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, System.currentTimeMillis() / 1000);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to cleanup expired cache entries", e);
        }
    }
}
