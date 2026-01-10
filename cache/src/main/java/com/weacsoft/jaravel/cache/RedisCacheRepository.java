package com.weacsoft.jaravel.cache;

import com.alibaba.fastjson.JSON;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisException;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class RedisCacheRepository implements CacheRepository {

    private final JedisPool pool;

    private final long defaultTtl;

    public RedisCacheRepository() {
        this("localhost", 6379);
    }

    public RedisCacheRepository(String host, int port) {
        this(host, port, 3600);
    }

    public RedisCacheRepository(String host, int port, long defaultTtl) {
        this.defaultTtl = defaultTtl;
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(128);
        config.setMaxIdle(64);
        config.setMinIdle(8);
        this.pool = new JedisPool(config, host, port);
    }

    public RedisCacheRepository(JedisPool pool, long defaultTtl) {
        this.pool = pool;
        this.defaultTtl = defaultTtl;
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
        try (Jedis jedis = pool.getResource()) {
            String jsonValue = JSON.toJSONString(value);
            jedis.setex(key, (int) timeUnit.toSeconds(ttl), jsonValue);
            return true;
        } catch (JedisException e) {
            throw new RuntimeException("Failed to put value into Redis cache", e);
        }
    }

    @Override
    public Object get(String key) {
        try (Jedis jedis = pool.getResource()) {
            String value = jedis.get(key);
            if (value == null) {
                return null;
            }
            return JSON.parse(value);
        } catch (JedisException e) {
            throw new RuntimeException("Failed to get value from Redis cache", e);
        }
    }

    @Override
    public boolean has(String key) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.exists(key);
        } catch (JedisException e) {
            throw new RuntimeException("Failed to check key existence in Redis cache", e);
        }
    }

    @Override
    public boolean forget(String key) {
        try (Jedis jedis = pool.getResource()) {
            jedis.del(key);
            return true;
        } catch (JedisException e) {
            throw new RuntimeException("Failed to delete key from Redis cache", e);
        }
    }

    @Override
    public boolean flush() {
        try (Jedis jedis = pool.getResource()) {
            jedis.flushDB();
            return true;
        } catch (JedisException e) {
            throw new RuntimeException("Failed to flush Redis cache", e);
        }
    }

    @Override
    public boolean putMany(Map<String, Object> values) {
        try (Jedis jedis = pool.getResource()) {
            String[] keyValuePairs = new String[values.size() * 2];
            int i = 0;
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                keyValuePairs[i++] = entry.getKey();
                keyValuePairs[i++] = JSON.toJSONString(entry.getValue());
            }
            jedis.mset(keyValuePairs);
            return true;
        } catch (JedisException e) {
            throw new RuntimeException("Failed to put many values into Redis cache", e);
        }
    }

    @Override
    public Map<String, Object> getMany(Collection<String> keys) {
        try (Jedis jedis = pool.getResource()) {
            List<String> values = jedis.mget(keys.toArray(new String[0]));
            Map<String, Object> result = new HashMap<>();
            int i = 0;
            for (String key : keys) {
                String value = values.get(i++);
                if (value != null) {
                    result.put(key, JSON.parse(value));
                }
            }
            return result;
        } catch (JedisException e) {
            throw new RuntimeException("Failed to get many values from Redis cache", e);
        }
    }

    @Override
    public boolean forgetMany(Collection<String> keys) {
        if (keys.isEmpty()) {
            return true;
        }
        try (Jedis jedis = pool.getResource()) {
            jedis.del(keys.toArray(new String[0]));
            return true;
        } catch (JedisException e) {
            throw new RuntimeException("Failed to delete many keys from Redis cache", e);
        }
    }

    @Override
    public boolean add(String key, Object value) {
        return add(key, value, defaultTtl);
    }

    @Override
    public boolean add(String key, Object value, long ttl) {
        return add(key, value, ttl, TimeUnit.SECONDS);
    }

    @Override
    public boolean add(String key, Object value, long ttl, TimeUnit timeUnit) {
        try (Jedis jedis = pool.getResource()) {
            String jsonValue = JSON.toJSONString(value);
            long result = jedis.setnx(key, jsonValue);
            if (result == 1) {
                jedis.expire(key, (int) timeUnit.toSeconds(ttl));
                return true;
            }
            return false;
        } catch (JedisException e) {
            throw new RuntimeException("Failed to add value to Redis cache", e);
        }
    }

    @Override
    public long increment(String key) {
        return increment(key, 1);
    }

    @Override
    public long increment(String key, long value) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.incrBy(key, value);
        } catch (JedisException e) {
            throw new RuntimeException("Failed to increment value in Redis cache", e);
        }
    }

    @Override
    public long decrement(String key) {
        return decrement(key, 1);
    }

    @Override
    public long decrement(String key, long value) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.decrBy(key, value);
        } catch (JedisException e) {
            throw new RuntimeException("Failed to decrement value in Redis cache", e);
        }
    }

    public void close() {
        if (pool != null) {
            pool.close();
        }
    }
}
