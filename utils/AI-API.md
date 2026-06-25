# utils AI-API Reference

> Module: `utils` | Package: `com.weacsoft.jaravel.vendor.utils` | Version: 0.1.0

## Overview
utils 模块提供通用的内存编译工具，包含 MemoryClassLoader、MemoryFileManager 和 SourceCodeJavaFileObject 三个核心类。它们协同工作，将 Java 源代码字符串在内存中编译为 class 字节码并直接加载，无需写入磁盘文件。这套机制被 jblade 模板引擎和 migration 迁移系统等用于动态编译和加载 Java 代码。

## Classes & Interfaces

### MemoryClassLoader
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.utils.memory`
- **Description**: 自定义类加载器，从内存读取 class 字节码。接收一个 `Map<String, byte[]>`（类名 -> 字节码），在 findClass 时从内存中查找并 defineClass。
- **Extends**: `java.lang.ClassLoader`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `MemoryClassLoader` | `Map<String, byte[]> classBytes, ClassLoader parent` | 构造方法 | 构造内存类加载器，指定字节码映射和父类加载器 |
| `findClass` | `String name` | `Class<?>` | 从内存中查找并定义类；内存中不存在时委托父类加载器查找 |

#### Usage Example
```java
Map<String, byte[]> compiledClasses = new ConcurrentHashMap<>();
// 编译后填充 compiledClasses ...
MemoryClassLoader loader = new MemoryClassLoader(compiledClasses, getClass().getClassLoader());
Class<?> clazz = loader.loadClass("com.example.MyMigration");
Object instance = clazz.getDeclaredConstructor().newInstance();
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
