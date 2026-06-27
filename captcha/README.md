# captcha 模块

> Jaravel-Vendor 的验证码模块，提供图片数字、算术、滑动、旋转四种验证码。核心层为纯 Java 实现（基于 `java.awt` 与 `java.util.Base64`），无 SpringBoot 依赖，可独立使用；同时提供 SpringBoot 3 自动装配适配层，开箱即用。包名统一为 `com.weacsoft.jaravel.vendor.captcha`。

---

## 目录

- [1. 核心特性](#1-核心特性)
- [2. 架构设计](#2-架构设计)
- [3. 依赖信息](#3-依赖信息)
- [4. 快速开始](#4-快速开始)
- [5. 四种验证码详解](#5-四种验证码详解)
- [6. 无状态 token 验证](#6-无状态-token-验证)
- [7. 配置说明](#7-配置说明)
- [8. 存储选择](#8-存储选择)
- [9. 自定义验证码类型](#9-自定义验证码类型)

---

## 1. 核心特性

- **四种验证码**：图片数字（`number`）、算术（`arithmetic`）、滑动拼图（`slider`）、旋转（`rotate`）
- **核心层零 SpringBoot 依赖**：纯 Java 实现，图像生成基于 `java.awt`，编码基于 `java.util.Base64`，可独立嵌入任意 Java 项目
- **模板方法模式**：`AbstractCaptcha` 封装生成/验证的模板流程，子类只需实现 `doGenerate` 与 `doVerify` 两个钩子方法
- **存储解耦（SPI）**：通过 `CaptchaStore` 接口与具体存储解耦，内置 `MemoryCaptchaStore`（内存）与 `CacheStoreCaptchaStore`（适配 jaravel cache 模块，支持 Redis 等）
- **无状态 token 验证**：生成结果携带 token（Base64 编码 captchaKey + answer + expireTime），可在不依赖服务端存储的场景下完成验证
- **答案防泄露**：答案仅存于 `CaptchaStore`，不下发到前端；`CaptchaResult` 中只包含 base64 图片、token 与额外展示数据
- **统一管理器**：`CaptchaManager` 维护 `type -> Captcha` 映射，提供按类型生成/验证的统一入口，`createDefault()` 开箱注册四种验证码
- **SpringBoot 3 自动装配**：引入依赖即自动创建 `CaptchaManager` Bean，并根据是否引入 cache 模块智能选择存储

---

## 2. 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                      适配层（springboot 包）                  │
│   CaptchaAutoConfiguration  +  CaptchaProperties(@ConfProps) │
│   - 自动装配 CaptchaManager Bean                              │
│   - 智能选择存储（CacheStore / Memory）                       │
│   - 将 yml 配置转为核心层 CaptchaProperties                    │
└───────────────────────────┬─────────────────────────────────┘
                            │ 依赖（optional）
┌───────────────────────────┴─────────────────────────────────┐
│                      核心层（纯 Java）                        │
│                                                              │
│   CaptchaManager  ──管理──▶  Captcha（接口）                  │
│        │                        ▲                            │
│        │ 注册                   │ 实现                       │
│        ▼                        │                            │
│   type -> Captcha        AbstractCaptcha（模板方法）          │
│                                 │                            │
│              ┌──────────────────┼──────────────────┐         │
│              │                  │                  │         │
│        NumberCaptcha   ArithmeticCaptcha   SliderCaptcha   RotateCaptcha
│                                                              │
│   CaptchaStore（SPI）◀── MemoryCaptchaStore / CacheStoreCaptchaStore
│   CaptchaProperties（配置）   CaptchaResult（结果）           │
└──────────────────────────────────────────────────────────────┘
```

### 分层说明

| 层 | 包 | 职责 | 依赖 |
| --- | --- | --- | --- |
| 核心层 | `com.weacsoft.jaravel.vendor.captcha` | 验证码接口、抽象基类、四种实现、存储、配置、管理器 | 纯 JDK（`java.awt`） |
| 适配层 | `com.weacsoft.jaravel.vendor.captcha.springboot` | SpringBoot 自动装配、`@ConfigurationProperties` 绑定 | SpringBoot 3（optional） |

核心层的 `CaptchaStore` 通过 SPI 与存储解耦：默认使用 `MemoryCaptchaStore`；当项目引入 jaravel `cache` 模块时，可使用 `CacheStoreCaptchaStore` 将答案存入 Redis / 数据库，实现跨进程验证码共享。

---

## 3. 依赖信息

### Maven 坐标

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>captcha</artifactId>
    <version>0.1.0</version>
</dependency>
```

### 独立使用（无 SpringBoot）

核心层无 SpringBoot 依赖，仅需 JDK 17+。`spring-boot-autoconfigure` 与 `cache` 均为 optional 依赖，不引入也不影响核心层使用：

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>captcha</artifactId>
    <version>0.1.0</version>
</dependency>
<!-- 无需引入 spring-boot，核心层可独立运行 -->
```

### SpringBoot 使用

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>captcha</artifactId>
    <version>0.1.0</version>
</dependency>
<!-- 引入 spring-boot-starter 即可触发自动装配 -->
```

### 跨进程存储（可选）

引入 jaravel `cache` 模块后，自动装配会自动切换为 `CacheStoreCaptchaStore`，答案存入 Redis / 数据库：

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>captcha</artifactId>
    <version>0.1.0</version>
</dependency>
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>cache</artifactId>
    <version>0.1.0</version>
</dependency>
```

### 传递依赖

| 依赖 | 用途 | 是否必须 |
| --- | --- | --- |
| `io.github.lijialong1313:cache` | `CacheStoreCaptchaStore` 的跨进程存储后端 | 可选（optional） |
| `org.springframework.boot:spring-boot-autoconfigure` | SpringBoot 自动装配 | 可选（optional） |
| `org.springframework.boot:spring-boot-configuration-processor` | 配置元数据生成 | 可选（optional） |

> 运行环境要求：JDK 17+。图像生成基于 `java.awt`，在无图形界面的服务器环境（如 Linux headless）需确保启用 headless 模式（`-Djava.awt.headless=true`，SpringBoot 默认已设置）。

---

## 4. 快速开始

### 4.1 独立使用（无 SpringBoot）

通过 `CaptchaManager.createDefault()` 获取开箱即用的管理器，已注册数字、算术、滑动、旋转四种验证码：

```java
import com.weacsoft.jaravel.vendor.captcha.CaptchaManager;
import com.weacsoft.jaravel.vendor.captcha.CaptchaResult;

// 1. 创建默认管理器（内存存储 + 默认配置，注册四种验证码）
CaptchaManager manager = CaptchaManager.createDefault();

// 2. 生成验证码（type + captchaKey）
String captchaKey = java.util.UUID.randomUUID().toString();
CaptchaResult result = manager.generate("number", captchaKey);

// 3. 将结果下发前端（JSON 序列化）
//    result.getImageBase64() -> base64 图片
//    result.getToken()       -> 无状态 token
//    result.getCaptchaKey()  -> 验证码标识
System.out.println(result.toMap());

// 4. 验证用户输入（一次性消费，验证后答案从存储中删除）
boolean ok = manager.verify("number", captchaKey, "ABCD");
```

### 4.2 SpringBoot 使用

引入依赖后自动装配 `CaptchaManager` Bean，直接注入即可：

```java
import com.weacsoft.jaravel.vendor.captcha.CaptchaManager;
import com.weacsoft.jaravel.vendor.captcha.CaptchaResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/captcha")
public class CaptchaController {

    @Autowired
    private CaptchaManager captchaManager;

    // 生成验证码
    @GetMapping("/generate")
    public CaptchaResult generate(@RequestParam String type) {
        String captchaKey = java.util.UUID.randomUUID().toString();
        return captchaManager.generate(type, captchaKey);
    }

    // 验证
    @PostMapping("/verify")
    public boolean verify(@RequestParam String type,
                          @RequestParam String captchaKey,
                          @RequestParam String userInput) {
        return captchaManager.verify(type, captchaKey, userInput);
    }
}
```

无需任何配置即可启动，默认使用内存存储与默认图片参数。通过 `application.yml` 可自定义配置（见[第 7 节](#7-配置说明)）。

### 4.3 自定义配置的独立使用

不使用 SpringBoot 时，可手动构造配置与存储：

```java
import com.weacsoft.jaravel.vendor.captcha.*;

// 自定义配置
CaptchaProperties properties = CaptchaProperties.createDefault();
properties.setWidth(200);
properties.setHeight(60);
properties.setLength(5);
properties.setExpireSeconds(120);
properties.setCaseSensitive(true);

// 自定义存储
CaptchaStore store = new MemoryCaptchaStore();

// 构造管理器并注册验证码
CaptchaManager manager = new CaptchaManager(store, properties);
manager.register(new NumberCaptcha(store, properties));
manager.register(new ArithmeticCaptcha(store, properties));
manager.register(new SliderCaptcha(store, properties));
manager.register(new RotateCaptcha(store, properties));

CaptchaResult result = manager.generate("number", "my-key");
boolean ok = manager.verify("number", "my-key", "ABC23");
```

---

## 5. 四种验证码详解

四种验证码共享同一套 `generate(captchaKey)` / `verify(captchaKey, userInput)` 接口，区别在于类型名、图片内容、用户输入含义与额外数据。

### 5.1 图片数字验证码（`number`）

随机字母 + 数字字符串（排除易混淆字符 0/O/1/I/L），带噪点、干扰线、随机旋转。

| 项 | 说明 |
| --- | --- |
| 类型名 | `number` |
| 答案 | 随机字符串（默认 4 位） |
| 用户输入 | 用户识别的字符 |
| 验证规则 | 按 `caseSensitive` 决定是否区分大小写 |
| 图片 | `imageBase64`（带 `data:image/png;base64,` 前缀） |

```java
CaptchaResult result = manager.generate("number", key);
// 前端展示 result.getImageBase64()，用户输入识别结果
boolean ok = manager.verify("number", key, "AB23");
```

### 5.2 算术验证码（`arithmetic`）

随机生成 `a op b = ?` 形式的算式（加 / 减 / 乘），答案为运算结果。

| 项 | 说明 |
| --- | --- |
| 类型名 | `arithmetic` |
| 答案 | 运算结果（数字字符串） |
| 用户输入 | 计算结果 |
| 验证规则 | 去除空格后字符串相等 |
| 图片 | `imageBase64`，内容如 `12 + 5 = ?` |

```java
CaptchaResult result = manager.generate("arithmetic", key);
// 图片显示如 "12 + 5 = ?"，用户输入 "17"
boolean ok = manager.verify("arithmetic", key, "17");
```

### 5.3 滑动验证码（`slider`）

在背景图上随机位置抠出缺口，前端拖动滑块拼回缺口。

| 项 | 说明 |
| --- | --- |
| 类型名 | `slider` |
| 答案 | 缺口横坐标 `gapX`（像素） |
| 用户输入 | 滑块拖动的 x 坐标 |
| 验证规则 | 按 `tolerance` 像素容差判定（`|gapX - input| <= tolerance`） |
| `imageBase64` | 带缺口的背景图 |
| `extra.sliderImage` | 滑块拼图小块（带透明通道的 PNG base64） |
| `extra.gapY` | 缺口纵坐标（前端固定滑块纵坐标用） |
| `extra.blockSize` | 滑块边长 |

```java
CaptchaResult result = manager.generate("slider", key);
// 前端用 imageBase64 作背景，sliderImage 作滑块，gapY 固定纵坐标
// 用户拖动后提交 x 坐标
boolean ok = manager.verify("slider", key, "123");  // 用户拖动到 x=123
```

### 5.4 旋转验证码（`rotate`）

将一张带明显朝向（向上箭头）的图片随机旋转，前端拖动将其转回正方向。

| 项 | 说明 |
| --- | --- |
| 类型名 | `rotate` |
| 答案 | 旋转角度（0~359） |
| 用户输入 | 用户旋转的角度 |
| 验证规则 | 按 `tolerance` 角度容差判定（双向最短角差） |
| `imageBase64` | 旋转后的图片（正方形） |
| `extra.size` | 图片边长 |

```java
CaptchaResult result = manager.generate("rotate", key);
// 前端展示旋转后的图片，用户拖动转回正方向并提交角度
boolean ok = manager.verify("rotate", key, "90");  // 用户旋转 90 度
```

---

## 6. 无状态 token 验证

除了默认的「基于 `CaptchaStore` 存储答案」的有状态验证外，`AbstractCaptcha` 还提供无状态 token 机制，适用于不希望（或无法）在服务端维护验证码存储的场景。

### 原理

`generate` 时会同时生成一个 token，其结构为：

```
base64(captchaKey) . base64(answer) . base64(expireTime)
```

- 三段以 `.` 拼接
- 每段为 UTF-8 字符串的 Base64 编码
- `expireTime` 为毫秒时间戳

> 注意：token 仅做 Base64 编码，**非加密安全**。如需更高安全性可由子类覆盖 `generateToken` / `verifyToken` 方法（如改用 HMAC 签名）。

### 使用方式

由于 `verifyToken` 为 `protected` 方法，无状态验证需通过 `Captcha` 实现实例调用。典型做法是在自定义验证码或控制器中调用：

```java
import com.weacsoft.jaravel.vendor.captcha.*;

CaptchaManager manager = CaptchaManager.createDefault();
CaptchaResult result = manager.generate("number", "my-key");

// 前端同时拿到 imageBase64 与 token
String token = result.getToken();
String userInput = "AB23";

// 无状态验证：通过 Captcha 实例的 verifyToken（需子类暴露或反射）
// 默认 verify() 走的是 store 有状态验证（一次性消费）：
boolean ok1 = manager.verify("number", "my-key", userInput);

// 若要使用 token 无状态验证，需扩展 Captcha 暴露 verifyToken，
// 或直接使用 AbstractCaptcha 子类实例（protected 方法）
```

> **有状态 vs 无状态**：
> - 有状态（`verify`）：从 `CaptchaStore` 取出答案并一次性删除，验证后不可重复使用，更安全。
> - 无状态（`verifyToken`）：不依赖服务端存储，token 自带答案与过期时间，但 token 被截获后可在有效期内重复使用。

---

## 7. 配置说明

### SpringBoot 配置（`application.yml`）

配置前缀为 `jaravel.captcha`，对应 `springboot.CaptchaProperties`：

```yaml
jaravel:
  captcha:
    enabled: true              # 是否启用自动装配
    width: 160                 # 图片宽度（像素）
    height: 50                 # 图片高度（像素）
    length: 4                  # 验证码字符长度（数字/算术）
    expire-seconds: 300        # 过期秒数，默认 5 分钟
    case-sensitive: false      # 是否区分大小写（数字验证码）
    tolerance: 5.0             # 滑动/旋转容差（滑动为像素，旋转为角度）
    noise: 50                  # 噪点数量
    interfere-lines: 30        # 干扰线数量
```

### 配置项说明

| 属性 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `jaravel.captcha.enabled` | `boolean` | `true` | 是否启用自动装配 |
| `jaravel.captcha.width` | `int` | `160` | 图片宽度（像素） |
| `jaravel.captcha.height` | `int` | `50` | 图片高度（像素） |
| `jaravel.captcha.length` | `int` | `4` | 验证码字符长度（适用于数字/算术验证码） |
| `jaravel.captcha.expire-seconds` | `long` | `300` | 过期秒数，默认 5 分钟 |
| `jaravel.captcha.case-sensitive` | `boolean` | `false` | 是否区分大小写（适用于数字验证码） |
| `jaravel.captcha.tolerance` | `double` | `5.0` | 滑动/旋转验证的容差范围（滑动为像素，旋转为角度） |
| `jaravel.captcha.noise` | `int` | `50` | 噪点数量 |
| `jaravel.captcha.interfere-lines` | `int` | `30` | 干扰线数量 |

> **注意**：SpringBoot 配置类（`springboot.CaptchaProperties`）的字段名 `noise` / `interfereLines` 与核心层配置类（`CaptchaProperties`）的字段名 `noiseCount` / `interfereCount` 不同。自动装配时通过 `toCoreProperties()` 方法完成映射转换。

### 核心层配置（独立使用）

独立使用时直接操作核心层 `CaptchaProperties`：

```java
CaptchaProperties properties = CaptchaProperties.createDefault();
properties.setWidth(200);
properties.setHeight(60);
properties.setLength(5);
properties.setExpireSeconds(120);
properties.setCaseSensitive(true);
properties.setTolerance(8.0);
properties.setNoiseCount(80);
properties.setInterfereCount(40);
```

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `width` | `int` | `160` | 图片宽度（像素） |
| `height` | `int` | `50` | 图片高度（像素） |
| `length` | `int` | `4` | 验证码字符长度 |
| `expireSeconds` | `long` | `300` | 过期秒数 |
| `caseSensitive` | `boolean` | `false` | 是否区分大小写 |
| `tolerance` | `double` | `5.0` | 容差范围 |
| `interfereCount` | `int` | `30` | 干扰线数量 |
| `noiseCount` | `int` | `50` | 噪点数量 |

---

## 8. 存储选择

验证码答案的存储通过 `CaptchaStore` 接口抽象，内置两种实现：

### 8.1 MemoryCaptchaStore（默认）

| 项 | 说明 |
| --- | --- |
| 实现类 | `com.weacsoft.jaravel.vendor.captcha.MemoryCaptchaStore` |
| 底层 | `ConcurrentHashMap` |
| 线程安全 | 是 |
| TTL | 自带过期机制，读取时惰性清理，另提供 `cleanup()` 主动清理 |
| 适用场景 | 单机、低并发场景 |
| 跨进程 | 不支持（进程重启即失） |

### 8.2 CacheStoreCaptchaStore（跨进程）

| 项 | 说明 |
| --- | --- |
| 实现类 | `com.weacsoft.jaravel.vendor.captcha.CacheStoreCaptchaStore` |
| 底层 | jaravel `cache` 模块的 `CacheStore`（可为 Redis、数据库等） |
| key 前缀 | `captcha:`（避免与其他业务缓存冲突） |
| 适用场景 | 分布式、多实例部署，需要跨进程共享验证码答案 |
| 前置条件 | 需引入 jaravel `cache` 模块 |

### 自动选择策略

SpringBoot 自动装配（`CaptchaAutoConfiguration`）会根据 classpath 智能选择存储：

| 条件 | 选择的存储 |
| --- | --- |
| classpath 存在 `com.weacsoft.jaravel.vendor.cache.CacheStore` 且容器中有 `CacheStore` Bean | `CacheStoreCaptchaStore`（跨进程模式） |
| classpath 不存在 cache 模块 | `MemoryCaptchaStore`（内存模式） |
| classpath 存在 cache 模块但容器中无 `CacheStore` Bean | 回退到 `MemoryCaptchaStore` |

### 手动指定存储

无论是否使用 SpringBoot，均可手动注入自定义存储：

```java
// 独立使用：手动构造 CacheStoreCaptchaStore
import com.weacsoft.jaravel.vendor.cache.CacheStore;
import com.weacsoft.jaravel.vendor.cache.DefaultCacheStore;
import com.weacsoft.jaravel.vendor.cache.ArrayCacheDriver;

CacheStore cacheStore = new DefaultCacheStore(new ArrayCacheDriver(), "myapp");
CaptchaStore captchaStore = new CacheStoreCaptchaStore(cacheStore);

CaptchaManager manager = new CaptchaManager(captchaStore, CaptchaProperties.createDefault());
manager.register(new NumberCaptcha(captchaStore, manager.getProperties()));
// ... 注册其他类型
```

```java
// SpringBoot：覆盖默认存储 Bean
@Bean
public CaptchaStore captchaStore(CacheStore cacheStore) {
    return new CacheStoreCaptchaStore(cacheStore);
}
```

### 自定义存储实现

实现 `CaptchaStore` 接口即可接入任意存储（如 Redis 直连）：

```java
public class RedisCaptchaStore implements CaptchaStore {
    private final Jedis jedis;

    public RedisCaptchaStore(Jedis jedis) {
        this.jedis = jedis;
    }

    @Override
    public void put(String captchaKey, String answer, long ttlSeconds) {
        jedis.setex("captcha:" + captchaKey, ttlSeconds, answer);
    }

    @Override
    public String get(String captchaKey) {
        return jedis.get("captcha:" + captchaKey);
    }

    @Override
    public String pull(String captchaKey) {
        String key = "captcha:" + captchaKey;
        String value = jedis.get(key);
        if (value != null) {
            jedis.del(key);
        }
        return value;
    }

    @Override
    public void remove(String captchaKey) {
        jedis.del("captcha:" + captchaKey);
    }
}
```

---

## 9. 自定义验证码类型

通过继承 `AbstractCaptcha` 实现自定义验证码，只需实现两个钩子方法：

| 方法 | 职责 |
| --- | --- |
| `doGenerate(CaptchaContext context)` | 生成图片并通过 `context.setAnswer()` 回写答案，返回填充了 `imageBase64` 与 `extra` 的 `CaptchaResult` |
| `doVerify(String answer, String userInput)` | 比对真实答案与用户输入，返回是否匹配 |
| `getType()` | 返回类型名（用于 `CaptchaManager` 注册与查找） |

### 示例：自定义中文成语验证码

```java
import com.weacsoft.jaravel.vendor.captcha.*;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Random;

public class IdiomCaptcha extends AbstractCaptcha {

    private static final String[] IDIOMS = {
        "一帆风顺", "万事如意", "心想事成", "马到成功", "春暖花开"
    };

    public IdiomCaptcha() {
        super();
    }

    public IdiomCaptcha(CaptchaStore store, CaptchaProperties properties) {
        super(store, properties);
    }

    @Override
    public String getType() {
        return "idiom";
    }

    @Override
    protected CaptchaResult doGenerate(CaptchaContext context) {
        CaptchaProperties p = context.getProperties();
        Random random = new Random();

        // 随机选取成语作为答案
        String answer = IDIOMS[random.nextInt(IDIOMS.length)];
        context.setAnswer(answer);

        // 绘制图片
        BufferedImage image = createImage(p.getWidth(), p.getHeight());
        Graphics2D g = image.createGraphics();
        try {
            addNoise(g, p.getWidth(), p.getHeight(), p.getNoiseCount(), random);
            drawText(g, answer, p.getWidth(), p.getHeight(), random);
        } finally {
            g.dispose();
        }

        CaptchaResult result = new CaptchaResult();
        result.setImageBase64(toBase64(image));
        return result;
    }

    @Override
    protected boolean doVerify(String answer, String userInput) {
        if (answer == null || userInput == null) {
            return false;
        }
        return answer.equals(userInput.trim());
    }
}
```

### 注册并使用

```java
// 独立使用
CaptchaManager manager = CaptchaManager.createDefault();
manager.register(new IdiomCaptcha(manager.getStore(), manager.getProperties()));

CaptchaResult result = manager.generate("idiom", key);
boolean ok = manager.verify("idiom", key, "一帆风顺");
```

```java
// SpringBoot：在配置类中注册自定义验证码
@Bean
public CaptchaManager captchaManager(CaptchaProperties properties,
                                      ObjectProvider<CaptchaStore> storeProvider) {
    CaptchaStore store = storeProvider.getIfAvailable(() -> new MemoryCaptchaStore());
    CaptchaProperties coreProps = properties.toCoreProperties();
    CaptchaManager manager = new CaptchaManager();
    manager.register(new NumberCaptcha(store, coreProps));
    manager.register(new ArithmeticCaptcha(store, coreProps));
    manager.register(new SliderCaptcha(store, coreProps));
    manager.register(new RotateCaptcha(store, coreProps));
    manager.register(new IdiomCaptcha(store, coreProps));  // 注册自定义类型
    return manager;
}
```

### 模板方法流程

`AbstractCaptcha.generate()` 的完整流程：

```
generate(captchaKey)
  │
  ├─ 1. 构造 CaptchaContext（携带 captchaKey + properties）
  ├─ 2. 调用 doGenerate(context)  ← 子类实现
  │      └─ 子类通过 context.setAnswer() 回写答案
  │      └─ 子类返回 CaptchaResult（含 imageBase64 + extra）
  ├─ 3. 填充 result 的 captchaKey / type / expireTime
  ├─ 4. 将答案存入 CaptchaStore（带 TTL）
  └─ 5. 生成无状态 token 并写入 result
```

`AbstractCaptcha.verify()` 的完整流程：

```
verify(captchaKey, userInput)
  │
  ├─ 1. 从 CaptchaStore 一次性取出答案（pull，取出即删除）
  ├─ 2. 答案为 null 则返回 false（不存在或已过期）
  └─ 3. 调用 doVerify(answer, userInput)  ← 子类实现
```

> `AbstractCaptcha` 还提供丰富的图像工具方法供子类复用：`createImage`、`createArgbImage`、`randomColor`、`drawRandomBackground`、`addNoise`、`addInterfereLines`、`drawText`、`toBase64`、`randomString` 等。
