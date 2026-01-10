# Jaravel Cache Module

A flexible and powerful caching module inspired by Laravel's cache system, supporting multiple cache drivers.

## Features

- **Memory Cache**: In-memory caching using ExpiryMap with automatic expiration
- **File Cache**: File-based caching with automatic expiration
- **Database Cache**: Database-backed caching with automatic table creation
- **Redis Cache**: Redis-based caching for distributed scenarios
- **Unified API**: Consistent interface across all cache drivers
- **Cache Manager**: Manage multiple cache stores with different configurations
- **Facade Pattern**: Static access methods for convenient usage

## Installation

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.weacsoft</groupId>
    <artifactId>cache</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

For Redis support, add:

```xml
<dependency>
    <groupId>redis.clients</groupId>
    <artifactId>jedis</artifactId>
    <version>4.3.1</version>
</dependency>
```

## Basic Usage

### 1. Setup Cache Manager

```java
CacheManager manager = new CacheManager();

// Add cache stores
manager.addStore("array", new ArrayCache(3600));
manager.addStore("file", new FileCache("/path/to/cache", 3600));
manager.addStore("redis", new RedisCache("localhost", 6379, 3600));

// Set default store
manager.setDefaultStore("array");

// Initialize facade
CacheFacade.setManager(manager);
```

### 2. Basic Operations

```java
// Put value
CacheFacade.put("key", "value");

// Put with TTL (in seconds)
CacheFacade.put("key", "value", 60);

// Put with TTL and TimeUnit
CacheFacade.put("key", "value", 2, TimeUnit.HOURS);

// Get value
Object value = CacheFacade.get("key");

// Get with type casting
String name = CacheFacade.get("name", String.class);

// Check if key exists
boolean exists = CacheFacade.has("key");

// Delete key
CacheFacade.forget("key");

// Clear all cache
CacheFacade.flush();
```

### 3. Advanced Operations

```java
// Put multiple values
Map<String, Object> values = new HashMap<>();
values.put("key1", "value1");
values.put("key2", "value2");
CacheFacade.putMany(values);

// Get multiple values
Map<String, Object> result = CacheFacade.getMany(Arrays.asList("key1", "key2"));

// Delete multiple keys
CacheFacade.forgetMany(Arrays.asList("key1", "key2"));

// Remember (cache if not exists)
CacheFacade.remember("expensive_data", 300, () -> {
    return computeExpensiveData();
});

// Remember forever
CacheFacade.rememberForever("static_data", () -> {
    return getStaticData();
});

// Pull (get and delete)
Object value = CacheFacade.pull("key");

// Add (only if not exists)
CacheFacade.add("unique_key", "value");

// Increment/Decrement
long counter = CacheFacade.increment("counter");
counter = CacheFacade.increment("counter", 5);
counter = CacheFacade.decrement("counter");
```

### 4. Using Multiple Stores

```java
// Use specific store
CacheFacade.store("redis").put("key", "value");
CacheFacade.store("file").put("key", "value");

// Switch default store
CacheFacade.setDefaultStore("redis");
```

### 5. Key Prefixing

```java
// Set prefix for all keys
CacheFacade.setPrefix("app:");

// Now all keys will be prefixed
CacheFacade.put("user:1", "data"); // Stores as "app:user:1"

// Clear prefix
CacheFacade.setPrefix("");
```

## Cache Drivers

### Array Cache (Memory)

Simple in-memory cache using ExpiryMap.

```java
ArrayCache cache = new ArrayCache(); // Default TTL: 3600 seconds
ArrayCache cache = new ArrayCache(7200); // Custom TTL
ArrayCache cache = new ArrayCache(7200, 1000); // TTL and initial capacity
```

### File Cache

File-based cache with automatic expiration.

```java
FileCache cache = new FileCache(); // Default directory and TTL
FileCache cache = new FileCache("/path/to/cache");
FileCache cache = new FileCache("/path/to/cache", 3600);
```

### Database Cache

Database-backed cache with automatic table creation.

```java
Connection connection = DriverManager.getConnection(url, username, password);
DatabaseCache cache = new DatabaseCache(connection);
DatabaseCache cache = new DatabaseCache(connection, "cache_table");
DatabaseCache cache = new DatabaseCache(connection, "cache_table", 3600);

// Cleanup expired entries
cache.cleanupExpired();
```

### Redis Cache

Redis-based cache for distributed caching.

```java
RedisCache cache = new RedisCache();
RedisCache cache = new RedisCache("localhost", 6379);
RedisCache cache = new RedisCache("localhost", 6379, 3600);

// Using custom JedisPool
JedisPool pool = new JedisPool(config, host, port);
RedisCache cache = new RedisCache(pool, 3600);

// Close connection when done
cache.close();
```

## Migration Support

For database cache, you can generate migration files:

```java
// Generate cache table migration
CacheMigrationGenerator.generateCacheTableMigration(
    "output/directory",
    "com.example.migrations"
);
```

Note: This requires the `database-migration` dependency.

## Architecture

The cache module follows a layered architecture:

1. **Cache Interface**: Defines the contract for all cache implementations
2. **CacheRepository Interface**: Internal repository interface for storage operations
3. **AbstractCache**: Base implementation with prefix support
4. **CacheRepository Implementations**: Storage-specific implementations
5. **Cache Implementations**: Public cache classes (ArrayCache, FileCache, etc.)
6. **CacheManager**: Manages multiple cache stores
7. **CacheFacade**: Static facade for convenient access

## Thread Safety

- **ArrayCache**: Thread-safe (uses synchronized ExpiryMap)
- **FileCache**: Not thread-safe (use external synchronization)
- **DatabaseCache**: Thread-safe (depends on JDBC connection)
- **RedisCache**: Thread-safe (uses JedisPool)

## Best Practices

1. **Choose the right driver**:
   - Use ArrayCache for simple, short-lived caching
   - Use FileCache for persistent local caching
   - Use DatabaseCache for distributed caching with database
   - Use RedisCache for high-performance distributed caching

2. **Set appropriate TTLs**:
   - Use short TTLs for frequently changing data
   - Use long TTLs for static data
   - Use `rememberForever()` for truly static data

3. **Use prefixes**:
   - Prefix keys to avoid collisions
   - Use consistent prefix patterns (e.g., "app:", "user:", "session:")

4. **Handle failures**:
   - Cache operations may throw RuntimeException
   - Implement fallback logic for critical operations

## License

MIT License
