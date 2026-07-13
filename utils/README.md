# utils 模块

> Jaravel-Vendor 的通用工具模块，提供内存编译基础设施。包含 `MemoryClassLoader`、`MemoryFileManager`、`SourceCodeJavaFileObject` 三个核心类，用于在运行时将 Java 源代码字符串编译为字节码并加载到内存中，无需写入磁盘。包名统一为 `com.weacsoft.jaravel.vendor.utils.memory`。

---

## 目录

- [1. 模块概述](#1-模块概述)
- [2. 依赖信息](#2-依赖信息)
- [3. 类总览](#3-类总览)
- [4. 内存编译工作原理](#4-内存编译工作原理)
- [5. SourceCodeJavaFileObject —— 源代码文件对象](#5-sourcecodejavafileobject--源代码文件对象)
- [6. MemoryFileManager —— 内存文件管理器](#6-memoryfilemanager--内存文件管理器)
- [7. MemoryClassLoader —— 内存类加载器](#7-memoryclassloader--内存类加载器)
- [8. 使用示例](#8-使用示例)
- [9. 线程安全说明](#9-线程安全说明)

---

## 1. 模块概述

`utils` 模块提供 Java 内存编译的基础设施，使应用程序能够在运行时将源代码字符串编译为字节码并直接加载到内存中，无需生成中间磁盘文件。核心特性如下：

| 特性 | 说明 |
| --- | --- |
| 内存编译 | 通过 `javax.tools.JavaCompiler` 在运行时编译源代码，字节码直接存入内存 |
| 内存加载 | 通过自定义 `ClassLoader` 从内存字节码定义并加载类 |
| 无磁盘 I/O | 编译输入（源码）与输出（字节码）均在内存中流转，不产生临时文件 |
| 零外部依赖 | 仅依赖 JDK 标准库（`javax.tools`）与 `slf4j-api` 日志门面 |

### 使用场景

本模块被以下模块使用：

| 使用方 | 用途 |
| --- | --- |
| `migration` 模块 | DIRECTORY 模式下运行时编译 `.java` 迁移文件，加载后执行迁移 |
| `jblade` 模块 | 运行时编译动态源码 |

---

## 2. 依赖信息

### Maven 坐标

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>utils</artifactId>
    <version>0.1.2</version>
</dependency>
```

### 传递依赖

| 依赖 | 用途 |
| --- | --- |
| `org.slf4j:slf4j-api` | 日志门面 |

> 运行环境要求：JDK 17+。内存编译功能依赖 `javax.tools.JavaCompiler`，仅在完整 JDK 环境下可用（JRE 中不包含编译器）。类加载功能（`MemoryClassLoader`）在 JRE 环境下也可使用。

---

## 3. 类总览

```
com.weacsoft.jaravel.vendor.utils.memory
├── SourceCodeJavaFileObject    // 包装源代码字符串的 JavaFileObject（编译输入）
├── MemoryFileManager           // 拦截编译器输出到内存的 JavaFileManager（编译输出）
└── MemoryClassLoader           // 从内存字节码加载类的 ClassLoader（类加载）
```

### 三类协作关系

```
源代码字符串
    │
    ▼
SourceCodeJavaFileObject          ← 包装源码为编译单元
    │
    ▼
JavaCompiler.compile()             ← JDK 编译器
    │
    ▼
MemoryFileManager                 ← 拦截编译输出
    │  (getJavaFileForOutput)
    ▼
ClassFileJavaFileObject            ← 字节码写入 ByteArrayOutputStream
    │
    ▼
Map<String, byte[]>               ← 类名 -> 字节码
    │
    ▼
MemoryClassLoader                 ← 从内存字节码加载类
    │  (findClass -> defineClass)
    ▼
Class<?> 对象
```

---

## 4. 内存编译工作原理

Java 编译器 API（`javax.tools.JavaCompiler`）通过 `JavaFileManager` 抽象文件 I/O。本模块通过自定义 `JavaFileManager` 与 `JavaFileObject`，将编译的输入与输出都重定向到内存：

1. **输入**：`SourceCodeJavaFileObject` 将源代码字符串包装为 `JavaFileObject`，编译器通过 `getCharContent()` 读取源码
2. **编译**：`JavaCompiler` 编译源码，生成 class 字节码
3. **输出拦截**：`MemoryFileManager` 重写 `getJavaFileForOutput()`，将编译器输出的字节码拦截到 `ClassFileJavaFileObject`（内部使用 `ByteArrayOutputStream`），而非写入磁盘
4. **加载**：`MemoryClassLoader` 重写 `findClass()`，从内存中的字节码通过 `defineClass()` 定义并加载类

---

## 5. SourceCodeJavaFileObject —— 源代码文件对象

`com.weacsoft.jaravel.vendor.utils.memory.SourceCodeJavaFileObject`

将 Java 源代码字符串包装为 `javax.tools.JavaFileObject`，供 `JavaCompiler` 编译时读取源码内容。继承 `SimpleJavaFileObject`。

### 构造方法

| 方法签名 | 说明 |
| --- | --- |
| `SourceCodeJavaFileObject(String className, String sourceCode)` | 构造内存源代码文件对象。`className` 用于构造虚拟 URI（`string:///` 协议），`sourceCode` 为 Java 源代码字符串 |

### 方法文档

| 方法签名 | 说明 |
| --- | --- |
| `CharBuffer getCharContent(boolean ignoreEncodingErrors)` | 返回源代码内容，供编译器读取。内部将字符串包装为 `CharBuffer` |

### URI 构造规则

构造时使用 `string:///` 协议创建虚拟 URI，将类全限定名中的 `.` 替换为 `/`，并追加 `.java` 扩展名：

```
类名: com.example.Hello
URI:  string:///com/example/Hello.java
```

### 使用示例

```java
String sourceCode = "package com.example; public class Hello {}";
SourceCodeJavaFileObject source = new SourceCodeJavaFileObject("com.example.Hello", sourceCode);

// 加入编译单元列表后交给 JavaCompiler 编译
List<JavaFileObject> compilationUnits = new ArrayList<>();
compilationUnits.add(source);
```

---

## 6. MemoryFileManager —— 内存文件管理器

`com.weacsoft.jaravel.vendor.utils.memory.MemoryFileManager`

内存文件管理器，继承 `ForwardingJavaFileManager<JavaFileManager>`。在 `getJavaFileForOutput()` 时将编译器输出的 class 字节码拦截到内存中的 `ClassFileJavaFileObject`，而非写入磁盘。编译完成后可通过方法获取所有生成的类名与字节码。

### 构造方法

| 方法签名 | 说明 |
| --- | --- |
| `MemoryFileManager(JavaFileManager fileManager)` | 构造内存文件管理器。`fileManager` 通常由 `compiler.getStandardFileManager()` 创建，作为委托对象 |

### 方法文档

| 方法签名 | 说明 |
| --- | --- |
| `JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling)` | 拦截编译器输出：当输出类型为 CLASS 时，返回内存中的 `ClassFileJavaFileObject`，使字节码写入内存而非磁盘 |
| `List<String> getGeneratedClassNames()` | 获取所有生成的类名列表 |
| `byte[] getGeneratedClass(String className)` | 获取指定类的字节码，不存在时返回 null |

### 内部类：ClassFileJavaFileObject

`MemoryFileManager.ClassFileJavaFileObject` 继承 `SimpleJavaFileObject`，存储编译后的字节码。

| 方法签名 | 说明 |
| --- | --- |
| `ClassFileJavaFileObject(String className)` | 构造内存 class 文件对象，URI 使用 `bytes:///` 协议 |
| `OutputStream openOutputStream()` | 返回字节码输出流（`ByteArrayOutputStream`），供编译器写入 |
| `byte[] getBytes()` | 获取已写入的字节码数组 |

### URI 构造规则

`ClassFileJavaFileObject` 使用 `bytes:///` 协议创建虚拟 URI，将类全限定名中的 `.` 替换为 `/`，并追加 `.class` 扩展名：

```
类名: com.example.Hello
URI:  bytes:///com/example/Hello.class
```

### 使用示例

```java
JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

try (MemoryFileManager fileManager = new MemoryFileManager(
        compiler.getStandardFileManager(diagnostics, null, null))) {

    List<JavaFileObject> compilationUnits = new ArrayList<>();
    compilationUnits.add(new SourceCodeJavaFileObject("com.example.Hello", sourceCode));

    JavaCompiler.CompilationTask task = compiler.getTask(
        null, fileManager, diagnostics, null, null, compilationUnits);
    Boolean success = task.call();

    if (success != null && success) {
        // 获取编译后的字节码
        for (String className : fileManager.getGeneratedClassNames()) {
            byte[] bytes = fileManager.getGeneratedClass(className);
            // bytes 可交给 MemoryClassLoader 加载
        }
    }
}
```

---

## 7. MemoryClassLoader —— 内存类加载器

`com.weacsoft.jaravel.vendor.utils.memory.MemoryClassLoader`

自定义类加载器，继承 `ClassLoader`。接收一个 `Map<String, byte[]>`（类名 -> 字节码），在 `findClass()` 时从内存中查找并 `defineClass()`。未在内存中的类委托父类加载器查找（双亲委派）。

### 构造方法

| 方法签名 | 说明 |
| --- | --- |
| `MemoryClassLoader(Map<String, byte[]> classBytes, ClassLoader parent)` | 构造内存类加载器。`classBytes` 为编译后的类字节码映射，`parent` 为父类加载器，用于双亲委派查找未在内存中的类 |

### 方法文档

| 方法签名 | 说明 |
| --- | --- |
| `Class<?> findClass(String name)` | 从内存中查找并定义类。若内存中存在该类的字节码，则调用 `defineClass()` 定义类；否则委托父类加载器查找 |

### 双亲委派机制

`findClass()` 仅在父类加载器无法加载时被调用（`loadClass()` 先走双亲委派）。查找顺序：

```
1. loadClass() 先委托父类加载器查找
2. 父类加载器找不到时，调用 findClass()
3. findClass() 从 classBytes 中查找字节码
4. 找到则 defineClass() 定义类
5. 找不到则委托 super.findClass() 抛出 ClassNotFoundException
```

### 使用示例

```java
Map<String, byte[]> compiledClasses = new ConcurrentHashMap<>();
// ... 编译后填充 compiledClasses ...

MemoryClassLoader loader = new MemoryClassLoader(compiledClasses, getClass().getClassLoader());
Class<?> clazz = loader.loadClass("com.example.Hello");
Object instance = clazz.getDeclaredConstructor().newInstance();
```

---

## 8. 使用示例

### 完整的内存编译与加载流程

```java
import com.weacsoft.jaravel.vendor.utils.memory.MemoryClassLoader;
import com.weacsoft.jaravel.vendor.utils.memory.MemoryFileManager;
import com.weacsoft.jaravel.vendor.utils.memory.SourceCodeJavaFileObject;

import javax.tools.*;
import java.util.*;

public class MemoryCompileExample {

    public static void main(String[] args) throws Exception {
        // 1. 准备源代码
        String className = "com.example.Hello";
        String sourceCode = ""
            + "package com.example;\n"
            + "public class Hello {\n"
            + "    public String greet() {\n"
            + "        return \"Hello, World!\";\n"
            + "    }\n"
            + "}\n";

        // 2. 获取编译器
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("无法获取 Java 编译器，请确保使用 JDK 而非 JRE");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        // 3. 内存编译
        Map<String, byte[]> compiledClasses = new java.util.concurrent.ConcurrentHashMap<>();
        try (MemoryFileManager fileManager = new MemoryFileManager(
                compiler.getStandardFileManager(diagnostics, null, null))) {

            List<JavaFileObject> compilationUnits = new ArrayList<>();
            compilationUnits.add(new SourceCodeJavaFileObject(className, sourceCode));

            // 添加 classpath 选项，确保编译器能找到依赖类
            List<String> options = new ArrayList<>();
            options.add("-classpath");
            options.add(System.getProperty("java.class.path"));

            JavaCompiler.CompilationTask task = compiler.getTask(
                null, fileManager, diagnostics, options, null, compilationUnits);
            Boolean success = task.call();

            if (success == null || !success) {
                StringBuilder errorMsg = new StringBuilder("编译错误: ");
                for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                    errorMsg.append(String.format("\n第%d行: %s",
                        diagnostic.getLineNumber(), diagnostic.getMessage(null)));
                }
                throw new RuntimeException(errorMsg.toString());
            }

            // 4. 收集编译后的字节码
            for (String name : fileManager.getGeneratedClassNames()) {
                compiledClasses.put(name, fileManager.getGeneratedClass(name));
            }
        }

        // 5. 从内存加载类
        MemoryClassLoader loader = new MemoryClassLoader(compiledClasses, MemoryCompileExample.class.getClassLoader());
        Class<?> clazz = loader.loadClass(className);

        // 6. 反射调用
        Object instance = clazz.getDeclaredConstructor().newInstance();
        String result = (String) clazz.getMethod("greet").invoke(instance);
        System.out.println(result);  // 输出: Hello, World!
    }
}
```

---

## 9. 线程安全说明

| 类 | 线程安全性 | 说明 |
| --- | --- | --- |
| `SourceCodeJavaFileObject` | 不可变 | 内部仅持有 `sourceCode` 字符串（不可变），构造后状态不变，可安全并发读取 |
| `MemoryFileManager` | 非线程安全 | 内部使用 `ConcurrentHashMap` 存储生成的类，但 `getJavaFileForOutput()` 在并发编译时可能产生竞态。设计为单次编译生命周期内使用，非线程安全 |
| `MemoryFileManager.ClassFileJavaFileObject` | 非线程安全 | 内部 `ByteArrayOutputStream` 非线程安全，由编译器在单线程内写入。编译完成后 `getBytes()` 可安全读取 |
| `MemoryClassLoader` | 需外部同步 | `findClass()` 读取 `Map`（若为 `ConcurrentHashMap` 则读取线程安全），但 `defineClass()` 对同一类名重复调用会抛出异常。应在单线程内加载，加载后的 `Class` 对象可安全并发使用 |
