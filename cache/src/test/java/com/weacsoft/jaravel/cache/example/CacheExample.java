package com.weacsoft.jaravel.cache.example;

import com.weacsoft.jaravel.cache.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class CacheExample {

    public static void main(String[] args) throws Exception {

        CacheManager manager = new CacheManager();


        ArrayCache arrayCache = new ArrayCache(3600);
        manager.addStore("array", arrayCache);


        FileCache fileCache = new FileCache("D:/cache", 3600);
        manager.addStore("file", fileCache);


//        Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/test", "root", "password");
//        DatabaseCache databaseCache = new DatabaseCache(connection, "cache", 3600);
//        manager.addStore("database", databaseCache);
//
//
//        RedisCache redisCache = new RedisCache("localhost", 6379, 3600);
//        manager.addStore("redis", redisCache);


        manager.setDefaultStore("array");
        CacheFacade.setManager(manager);


        basicUsage();


        multipleStores();


        advancedFeatures();


        manager.flushAll();
    }

    private static void basicUsage() {
        System.out.println("=== Basic Usage ===");


        CacheFacade.put("name", "John Doe");
        System.out.println("Get name: " + CacheFacade.get("name"));


        CacheFacade.put("age", 30, 60);
        System.out.println("Get age: " + CacheFacade.get("age"));


        CacheFacade.put("config", Arrays.asList("key1", "value1", "key2", "value2"), 2, TimeUnit.HOURS);
        System.out.println("Get config: " + CacheFacade.get("config"));


        System.out.println("Has name: " + CacheFacade.has("name"));
        System.out.println("Has unknown: " + CacheFacade.has("unknown"));


        CacheFacade.forget("name");
        System.out.println("After forget, has name: " + CacheFacade.has("name"));
    }

    private static void multipleStores() {
        System.out.println("\n=== Multiple Stores ===");


        CacheFacade.store("array").put("array_key", "array_value");
        CacheFacade.store("file").put("file_key", "file_value");
//        CacheFacade.store("redis").put("redis_key", "redis_value");


        System.out.println("Array store: " + CacheFacade.store("array").get("array_key"));
        System.out.println("File store: " + CacheFacade.store("file").get("file_key"));
//        System.out.println("Redis store: " + CacheFacade.store("redis").get("redis_key"));


        CacheFacade.setDefaultStore("file");
        System.out.println("Default store (now file): " + CacheFacade.get("file_key"));
    }

    private static void advancedFeatures() {
        System.out.println("\n=== Advanced Features ===");


        CacheFacade.putMany(new HashMap<String, Object>() {{
            put("user:1", "Alice");
            put("user:2", "Bob");
            put("user:3", "Charlie");
        }});
        System.out.println("Get many users: " + CacheFacade.getMany(Arrays.asList("user:1", "user:2", "user:3")));


        CacheFacade.remember("expensive_operation", 300, () -> {
            System.out.println("Computing expensive operation...");
            return "Result of expensive operation";
        });
        System.out.println("Remembered value: " + CacheFacade.get("expensive_operation"));


        CacheFacade.add("unique_key", "unique_value");
        System.out.println("Added unique_key: " + CacheFacade.get("unique_key"));
        boolean addedAgain = CacheFacade.add("unique_key", "new_value");
        System.out.println("Added again (should be false): " + addedAgain);


        long counter = CacheFacade.increment("counter");
        System.out.println("Counter after increment: " + counter);
        counter = CacheFacade.increment("counter", 5);
        System.out.println("Counter after increment by 5: " + counter);
        counter = CacheFacade.decrement("counter");
        System.out.println("Counter after decrement: " + counter);


        Object pulled = CacheFacade.pull("pull_key");
        System.out.println("Pulled value: " + pulled);
        System.out.println("After pull, has pull_key: " + CacheFacade.has("pull_key"));


        CacheFacade.setPrefix("app:");
        CacheFacade.put("key1", "value1");
        System.out.println("With prefix, get key1: " + CacheFacade.get("key1"));
        CacheFacade.setPrefix("");
    }
}
