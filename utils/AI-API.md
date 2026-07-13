# utils AI-API Reference

> Module: `utils` | Package: `com.weacsoft.jaravel.vendor.utils` | Version: 0.1.2

## Overview
utils 模块提供两类基础设施工具：

1. **内存编译基础设施**：包含 MemoryClassLoader、MemoryFileManager 和 SourceCodeJavaFileObject 三个核心类。它们协同工作，将 Java 源代码字符串在内存中编译为 class 字节码并直接加载，无需写入磁盘文件。这套机制被 jblade 模板引擎和 migration 迁移系统等用于动态编译和加载 Java 代码。

2. **通用内存缓存**：包含 SimpleMemoryCache 类，基于 ConcurrentHashMap + TTL 过期机制的零依赖内存缓存。供 cache 模块的 ArrayCacheDriver、wechat-sdk 等模块作为 cache fallback 使用。

## Classes & Interfaces

### MemoryClassLoader
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.utils.memory`
- **Description**: 自定义类加载器，从内存读取 class 字节码。接收一个 `Map<String, byte[]>`（类名 -> 字节码），在 findClass 时从内存中查找并 defineClass。提供 `removeAll()` 清除所有已编译类字节码，以及 `getCompiledClassesName()` 获取所有已编译类的类名列表，便于资源管理与清理。
- **Extends**: `java.lang.ClassLoader`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `MemoryClassLoader` | `Map<String, byte[]> classBytes, ClassLoader parent` | 构造方法 | 构造内存类加载器，指定字节码映射和父类加载器 |
| `findClass` | `String name` | `Class<?>` | 从内存中查找并定义类；内存中不存在时委托父类加载器查找 |
| `removeAll` | 无 | `void` | 清除所有已编译的类字节码，释放内存 |
| `getCompiledClassesName` | 无 | `List<String>` | 获取所有已编译类的类名列表 |

#### Usage Example
```java
Map<String, byte[]> compiledClasses = new ConcurrentHashMap<>();
// 编译后填充 compiledClasses ...
MemoryClassLoader loader = new MemoryClassLoader(compiledClasses, getClass().getClassLoader());
Class<?> clazz = loader.loadClass("com.example.MyMigration");
Object instance = clazz.getDeclaredConstructor().newInstance();

// 获取所有已编译类的类名列表
List<String> classNames = loader.getCompiledClassesName();

// 清除所有已编译的类字节码（释放资源）
loader.removeAll();
```

---

### MemoryFileManager
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.utils.memory`
- **Description**: 内存文件管理器，收集编译后的类字节码。继承 ForwardingJavaFileManager，在 getJavaFileForOutput 时将编译器输出的 class 字节码拦截到内存中的 ClassFileJavaFileObject，而非写入磁盘。
- **Extends**: `javax.tools.ForwardingJavaFileManager<JavaFileManager>`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `MemoryFileManager` | `JavaFileManager fileManager` | 构造方法 | 构造内存文件管理器，传入被委托的标准文件管理器 |
| `getJavaFileForOutput` | `Location location, String className, JavaFileObject.Kind kind, FileObject sibling` | `JavaFileObject` | 拦截编译器输出，将 class 字节码写入内存 |
| `getGeneratedClassNames` | 无 | `List<String>` | 获取所有生成的类全限定名列表 |
| `getGeneratedClass` | `String className` | `byte[]` | 获取指定类的字节码，不存在时返回 null |

#### Usage Example
```java
JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
MemoryFileManager fileManager = new MemoryFileManager(
    compiler.getStandardFileManager(null, null, null));

List<JavaFileObject> compilationUnits = List.of(
    new SourceCodeJavaFileObject("com.example.Hello", sourceCode)
);
compiler.getTask(null, fileManager, null, null, null, compilationUnits).call();

// 获取编译结果
List<String> classNames = fileManager.getGeneratedClassNames();
byte[] bytes = fileManager.getGeneratedClass("com.example.Hello");

// 加载到内存
Map<String, byte[]> classBytes = new HashMap<>();
for (String name : classNames) {
    classBytes.put(name, fileManager.getGeneratedClass(name));
}
MemoryClassLoader loader = new MemoryClassLoader(classBytes, getClass().getClassLoader());
Class<?> clazz = loader.loadClass("com.example.Hello");
```

---

### MemoryFileManager.ClassFileJavaFileObject
- **Type**: class (static nested)
- **Package**: `com.weacsoft.jaravel.vendor.utils.memory`
- **Description**: 内存中的 class 文件对象，存储编译后的字节码。编译器通过 openOutputStream 获取输出流，将字节码写入 ByteArrayOutputStream。
- **Extends**: `javax.tools.SimpleJavaFileObject`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `ClassFileJavaFileObject` | `String className` | 构造方法 | 构造内存 class 文件对象 |
| `openOutputStream` | 无 | `OutputStream` | 返回字节码输出流，供编译器写入 |
| `getBytes` | 无 | `byte[]` | 获取已写入的字节码 |

---

### SourceCodeJavaFileObject
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.utils.memory`
- **Description**: 内存中的源代码 JavaFileObject。将 Java 源代码字符串包装为 JavaFileObject，供 JavaCompiler 编译时读取源码内容。
- **Extends**: `javax.tools.SimpleJavaFileObject`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `SourceCodeJavaFileObject` | `String className, String sourceCode` | 构造方法 | 构造内存源代码文件对象 |
| `getCharContent` | `boolean ignoreEncodingErrors` | `CharBuffer` | 返回源代码内容，供编译器读取 |

#### Usage Example
```java
String sourceCode = """
    package com.example;
    public class Hello {
        public String greet() { return "Hello, World!"; }
    }
    """;
SourceCodeJavaFileObject source = new SourceCodeJavaFileObject("com.example.Hello", sourceCode);

// 加入编译单元列表后交给 JavaCompiler 编译
JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
MemoryFileManager fileManager = new MemoryFileManager(
    compiler.getStandardFileManager(null, null, null));
compiler.getTask(null, fileManager, null, null, null, List.of(source)).call();
```

---

### SimpleMemoryCache
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.utils.cache`
- **Description**: 通用内存缓存，基于 ConcurrentHashMap + TTL 过期机制。零外部依赖，仅依赖 JDK 标准库。适用于各模块在 cache 模块不可用时的 fallback 存储、单机场景下的轻量内存缓存、单元测试中的 mock 缓存。线程安全，读取 / 存在性判断时惰性清理过期条目；另提供 `cleanup()` 主动清理。cache 模块的 `ArrayCacheDriver` 内部基于此类实现；wechat-sdk 等模块在 CacheManager 未注入时通过 `CacheManager.createDefaultStore()` 间接使用此类作为 fallback。
- **Note**: 此类不依赖 cache 模块，可独立使用。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `put` | `String key, Object value, long ttlSeconds` | `void` | 写入缓存，指定过期秒数（<=0 永不过期） |
| `put` | `String key, Object value` | `void` | 永久写入缓存（永不过期） |
| `get` | `String key` | `Object` | 读取缓存值，不存在或已过期返回 null（惰性清理） |
| `get` | `String key, Class<T> type` | `<T> T` | 读取并按指定类型转换，类型不匹配返回 null |
| `exists` | `String key` | `boolean` | 判断缓存键是否存在（未过期，惰性清理） |
| `remove` | `String key` | `boolean` | 移除指定缓存键，返回是否确实移除 |
| `removeAll` | 无 | `void` | 清空所有缓存条目 |
| `allKeys` | 无 | `Collection<String>` | 返回所有未过期的缓存键（惰性清理） |
| `add` | `String key, Object value, long ttlSeconds` | `boolean` | 仅当键不存在时写入，返回是否实际写入 |
| `pull` | `String key` | `Object` | 读取后立即删除，不存在或已过期返回 null |
| `cleanup` | 无 | `int` | 主动清理所有已过期条目，返回实际清理的条目数 |
| `size` | 无 | `int` | 返回当前条目数（含可能已过期但未触发清理的） |
| `increment` | `String key` | `long` | 自增 1，键不存在或非数字时按 0 起算 |
| `increment` | `String key, long amount` | `long` | 自增指定步长 |
| `decrement` | `String key` | `long` | 自减 1 |
| `decrement` | `String key, long amount` | `long` | 自减指定步长 |

#### Usage Example
```java
SimpleMemoryCache cache = new SimpleMemoryCache();
cache.put("key", "value", 60);    // 60 秒后过期
cache.put("key2", "value2");       // 永不过期

Object v = cache.get("key");
String typed = cache.get("key", String.class);
boolean exists = cache.exists("key");

cache.remove("key");
int removed = cache.cleanup();     // 主动清理所有过期项

// 自增/自减
long hits = cache.increment("hits");
cache.increment("hits", 10);
cache.decrement("hits");
```
