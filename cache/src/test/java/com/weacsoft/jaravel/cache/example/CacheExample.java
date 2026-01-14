package com.weacsoft.jaravel.cache.example;

import com.weacsoft.jaravel.cache.ArrayCacheDriver;
import com.weacsoft.jaravel.cache.Cache;
import com.weacsoft.jaravel.cache.DefaultCacheStore;
import com.weacsoft.jaravel.cache.FileCacheDriver;

import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

public class CacheExample {

    public static void main(String[] args) throws Exception {


        //加入内存缓存
        Cache.addStore("array", new DefaultCacheStore(new ArrayCacheDriver()));

        //加入内存缓存

        Cache.addStore("file", new DefaultCacheStore(new FileCacheDriver()));

        Cache.setDefaultStore("array");


        basicUsage();


        multipleStores();


        advancedFeatures();


        Cache.flush();
    }

    private static void basicUsage() {
        System.out.println("=== Basic Usage ===");


        Cache.put("name", "John Doe");
        System.out.println("Get name: " + Cache.get("name"));


        Cache.put("age", 30, 60);
        System.out.println("Get age: " + Cache.get("age"));


        Cache.put("config", Arrays.asList("key1", "value1", "key2", "value2"), 2, TimeUnit.HOURS);
        System.out.println("Get config: " + Cache.get("config"));


        System.out.println("Has name: " + Cache.has("name"));
        System.out.println("Has unknown: " + Cache.has("unknown"));


        Cache.forget("name");
        System.out.println("After forget, has name: " + Cache.has("name"));
    }

    private static void multipleStores() {
        System.out.println("\n=== Multiple Stores ===");


        Cache.store("array").put("array_key", "array_value");
        Cache.store("file").put("file_key", "file_value");
//        CacheFacade.store("redis").put("redis_key", "redis_value");


        System.out.println("Array store: " + Cache.store("array").get("array_key"));
        System.out.println("File store: " + Cache.store("file").get("file_key"));
//        System.out.println("Redis store: " + CacheFacade.store("redis").get("redis_key"));


        Cache.setDefaultStore("file");
        System.out.println("Default store (now file): " + Cache.get("file_key"));
    }

    private static void advancedFeatures() {
        System.out.println("\n=== Advanced Features ===");


        Cache.putMany(new HashMap<String, Object>() {{
            put("user:1", "Alice");
            put("user:2", "Bob");
            put("user:3", "Charlie");
        }});
        System.out.println("Get many users: " + Cache.getMany(Arrays.asList("user:1", "user:2", "user:3")));


        Cache.remember("expensive_operation", 300, () -> {
            System.out.println("Computing expensive operation...");
            return "Result of expensive operation";
        });
        System.out.println("Remembered value: " + Cache.get("expensive_operation"));


        Cache.add("unique_key", "unique_value");
        System.out.println("Added unique_key: " + Cache.get("unique_key"));
        boolean addedAgain = Cache.add("unique_key", "new_value");
        System.out.println("Added again (should be false): " + addedAgain);


        long counter = Cache.increment("counter");
        System.out.println("Counter after increment: " + counter);
        counter = Cache.increment("counter", 5);
        System.out.println("Counter after increment by 5: " + counter);
        counter = Cache.decrement("counter");
        System.out.println("Counter after decrement: " + counter);


        Object pulled = Cache.pull("pull_key");
        System.out.println("Pulled value: " + pulled);
        System.out.println("After pull, has pull_key: " + Cache.has("pull_key"));


        Cache.setPrefix("app:");
        Cache.put("key1", "value1");
        System.out.println("With prefix, get key1: " + Cache.get("key1"));
        Cache.setPrefix("");
    }
}
