# captcha 模块

> Jaravel-Vendor 的验证码模块，提供图片数字、算术、滑动、旋转、文字点选五种验证码。核心层为纯 Java 实现（基于 `java.awt` 与 `java.util.Base64`），无 SpringBoot 依赖，可独立使用；同时提供 SpringBoot 3 自动装配适配层，开箱即用。前端提供 `jaravel-captcha.js` 库（零依赖），采用事件驱动模式，用户完成验证操作后触发 `complete` 事件，由业务方决定后续处理。包名统一为 `com.weacsoft.jaravel.vendor.captcha`。

---

## 目录

- [1. 核心特性](#1-核心特性)
- [2. 架构设计](#2-架构设计)
- [3. 依赖信息](#3-依赖信息)
- [4. 快速开始](#4-快速开始)
- [5. 五种验证码详解](#5-五种验证码详解)
- [6. 无状态 token 验证](#6-无状态-token-验证)
- [7. 配置说明](#7-配置说明)
- [8. 轨迹验证](#8-轨迹验证)
- [9. 可配置性](#9-可配置性)
- [10. 自定义背景图](#10-自定义背景图)
- [11. 水印](#11-水印)
- [12. 存储选择](#12-存储选择)
- [13. 自定义验证码类型](#13-自定义验证码类型)
- [14. 前端 JavaScript API](#14-前端-javascript-api)

---

## 1. 核心特性

- **五种验证码**：图片数字（`number`）、算术（`arithmetic`）、滑动拼图（`slider`）、旋转（`rotate`）、文字点选（`click`）
- **事件驱动前端**：`jaravel-captcha.js` 前端库（零依赖），用户完成验证操作后触发 `complete` 事件（不自动提交到后端），由业务方决定后续处理。支持 `on`/`off` 注册/移除事件监听器，支持 `beforeGet`、`afterGet`、`complete` 三个事件
- **轨迹行为分析**：滑动/旋转验证码不仅校验最终位置，还校验拖动轨迹的人类行为特征——点数、时长、连续性、非匀速、加速度方向多样性，有效防范自动化脚本直接提交最终值
- **核心层零 SpringBoot 依赖**：纯 Java 实现，图像生成基于 `java.awt`，编码基于 `java.util.Base64`，可独立嵌入任意 Java 项目
- **模板方法模式**：`AbstractCaptcha` 封装生成/验证的模板流程，子类只需实现 `doGenerate` 与 `doVerify` 两个钩子方法
- **存储解耦（SPI）**：通过 `CaptchaStore` 接口与具体存储解耦，内置 `MemoryCaptchaStore`（内存）与 `CacheStoreCaptchaStore`（适配 jaravel cache 模块，支持 Redis 等）
- **无状态 token 验证**：生成结果携带 token（Base64 编码 captchaKey + answer + expireTime），可在不依赖服务端存储的场景下完成验证
- **答案防泄露**：答案仅存于 `CaptchaStore`，不下发到前端；`CaptchaResult` 中只包含 base64 图片、token 与额外展示数据
- **统一管理器**：`CaptchaManager` 维护 `type -> Captcha` 映射，提供按类型生成/验证的统一入口，`createDefault()` 开箱注册五种验证码
- **SpringBoot 3 自动装配**：引入依赖即自动创建 `CaptchaManager` Bean，并根据是否引入 cache 模块智能选择存储
- **视觉高度可配置**：字体名称/样式/大小、字符旋转角度、自定义字符集、弧线干扰（比直线更难被 OCR 识别）等均可通过配置项调整
- **自定义背景图**：滑动与旋转验证码支持自定义背景图，可通过文件路径 / classpath 资源或 base64 数据指定，未配置时使用随机生成的渐变背景
- **文字/图片水印**：五种验证码均支持叠加文字水印（可配置内容、字体、颜色、位置、旋转角度）与图片水印（可配置透明度、缩放比例），水印在验证码内容绘制完成后叠加

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
│        NumberCaptcha   ArithmeticCaptcha   SliderCaptcha   RotateCaptcha   ClickCaptcha
│                                                              │
│   CaptchaStore（SPI）◀── MemoryCaptchaStore / CacheStoreCaptchaStore
│   CaptchaProperties（配置）   CaptchaResult（结果）           │
└──────────────────────────────────────────────────────────────┘
```

### 分层说明

| 层 | 包 | 职责 | 依赖 |
| --- | --- | --- | --- |
| 核心层 | `com.weacsoft.jaravel.vendor.captcha` | 验证码接口、抽象基类、五种实现、存储、配置、管理器 | 纯 JDK（`java.awt`） |
| 适配层 | `com.weacsoft.jaravel.vendor.captcha.springboot` | SpringBoot 自动装配、`@ConfigurationProperties` 绑定 | SpringBoot 3（optional） |

核心层的 `CaptchaStore` 通过 SPI 与存储解耦：默认使用 `MemoryCaptchaStore`；当项目引入 jaravel `cache` 模块时，可使用 `CacheStoreCaptchaStore` 将答案存入 Redis / 数据库，实现跨进程验证码共享。

---

## 3. 依赖信息

### Maven 坐标

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>captcha</artifactId>
    <version>0.1.2</version>
</dependency>
```

### 独立使用（无 SpringBoot）

核心层无 SpringBoot 依赖，仅需 JDK 17+。`spring-boot-autoconfigure` 与 `cache` 均为 optional 依赖，不引入也不影响核心层使用：

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>captcha</artifactId>
    <version>0.1.2</version>
</dependency>
<!-- 无需引入 spring-boot，核心层可独立运行 -->
```

### SpringBoot 使用

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>captcha</artifactId>
    <version>0.1.2</version>
</dependency>
<!-- 引入 spring-boot-starter 即可触发自动装配 -->
```

### 跨进程存储（可选）

引入 jaravel `cache` 模块后，自动装配会自动切换为 `CacheStoreCaptchaStore`，答案存入 Redis / 数据库：

```xml
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>captcha</artifactId>
    <version>0.1.2</version>
</dependency>
<dependency>
    <groupId>io.github.lijialong1313</groupId>
    <artifactId>cache</artifactId>
    <version>0.1.2</version>
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

通过 `CaptchaManager.createDefault()` 获取开箱即用的管理器，已注册数字、算术、滑动、旋转、文字点选五种验证码：

```java
import com.weacsoft.jaravel.vendor.captcha.CaptchaManager;
import com.weacsoft.jaravel.vendor.captcha.CaptchaResult;

// 1. 创建默认管理器（内存存储 + 默认配置，注册五种验证码）
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

> **前端示例**：模块内置了一个完整的演示页面 `src/main/resources/static/captcha-demo.html`，涵盖数字、算术、滑动、旋转、文字点选五种验证码的生成、展示、事件驱动验证全流程。滑动/旋转验证码的轨迹采集逻辑可直接参考该文件（详见[第 8.4 节](#84-前端轨迹采集示例)）。在 SpringBoot 项目中引入本模块后，访问 `http://localhost:8080/captcha-demo.html` 即可打开演示页（需配置静态资源访问）。

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
manager.register(new ClickCaptcha(store, properties));

CaptchaResult result = manager.generate("number", "my-key");
boolean ok = manager.verify("number", "my-key", "ABC23");
```

---

## 5. 五种验证码详解

五种验证码共享同一套 `generate(captchaKey)` / `verify(captchaKey, userInput)` 接口，区别在于类型名、图片内容、用户输入含义与额外数据。

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

在背景图上随机位置抠出缺口，前端拖动滑块拼回缺口。启用轨迹验证时还需校验拖动轨迹的人类行为特征。支持[自定义背景图](#10-自定义背景图)与[水印](#11-水印)。

| 项 | 说明 |
| --- | --- |
| 类型名 | `slider` |
| 答案 | 缺口横坐标 `gapX`（像素） |
| 用户输入 | 滑块拖动的 x 坐标。启用轨迹验证时需提交 JSON（见[第 8 节](#8-轨迹验证)） |
| 验证规则 | 1. 位置校验：`Math.abs(gapX - value) <= tolerance`；2. 轨迹校验（可选）：通过 `TrajectoryValidator` 校验拖动行为 |
| `imageBase64` | 带缺口的背景图 |
| `extra.sliderImage` | 滑块拼图小块（带透明通道的 PNG base64） |
| `extra.gapY` | 缺口纵坐标（前端固定滑块纵坐标用） |
| `extra.blockSize` | 滑块边长 |
| `extra.trajectoryEnabled` | 是否启用了轨迹验证（前端可据此决定提交格式） |

```java
CaptchaResult result = manager.generate("slider", key);
// 前端用 imageBase64 作背景，sliderImage 作滑块，gapY 固定纵坐标
// 用户拖动后提交 JSON：
// {"value": 123, "trajectory": [{"t":0,"v":0},{"t":50,"v":5},...]}
boolean ok = manager.verify("slider", key,
        "{\"value\":123,\"trajectory\":[{\"t\":0,\"v\":0},{\"t\":50,\"v\":5}]}");

// 未启用轨迹验证时，也可直接提交数字字符串（向后兼容）
boolean ok2 = manager.verify("slider", key, "123");
```

### 5.4 旋转验证码（`rotate`）

将一张带明显朝向（向上箭头）的图片随机旋转，前端拖动将其转回正方向。启用轨迹验证时还需校验旋转轨迹的人类行为特征。支持[自定义背景图](#10-自定义背景图)与[水印](#11-水印)。

| 项 | 说明 |
| --- | --- |
| 类型名 | `rotate` |
| 答案 | 旋转角度（0~359） |
| 用户输入 | 用户旋转的角度。启用轨迹验证时需提交 JSON（见[第 8 节](#8-轨迹验证)） |
| 验证规则 | 1. 角度校验：双向最短角差 `diff <= tolerance`；2. 轨迹校验（可选）：通过 `TrajectoryValidator` 校验旋转行为 |
| `imageBase64` | 旋转后的图片（正方形） |
| `extra.size` | 图片边长 |
| `extra.trajectoryEnabled` | 是否启用了轨迹验证（前端可据此决定提交格式） |
| 朝向标记 | 顶部居中向上箭头（白色杆 + 蓝色三角头部），便于用户判断正方向 |

```java
CaptchaResult result = manager.generate("rotate", key);
// 前端展示旋转后的图片，用户拖动转回正方向并提交 JSON：
// {"value": 90, "trajectory": [{"t":0,"v":0},{"t":50,"v":3},...]}
boolean ok = manager.verify("rotate", key,
        "{\"value\":90,\"trajectory\":[{\"t\":0,\"v\":0},{\"t\":50,\"v\":3}]}");

// 未启用轨迹验证时，也可直接提交数字字符串（向后兼容）
boolean ok2 = manager.verify("rotate", key, "90");
```

### 5.5 文字点选验证码（`click`）

在背景图上随机生成若干汉字（目标文字 + 干扰文字），要求用户按提示顺序依次点击指定文字。出于安全考虑，不再向前端返回文字坐标，前端需完全依赖视觉识别后点击。支持[水印](#11-水印)。

| 项 | 说明 |
| --- | --- |
| 类型名 | `click` |
| 答案 | 目标文字坐标，分号分隔（`"x1,y1;x2,y2;x3,y3"`），顺序与提示一致 |
| 用户输入 | 用户点击的坐标序列（JSON 或分号分隔字符串） |
| 验证规则 | 依次比对每一对点击坐标与目标坐标，距离需 ≤ 容差半径（`tolerance`，最小 20 像素） |
| `imageBase64` | 包含所有展示文字的背景图 |
| `extra.prompt` | 点选提示文字，如 `"请依次点击：天、地、人"` |
| `extra.clickCount` | 需要点击的文字数量 |
| `extra.width` | 图片宽度（前端用于坐标换算） |
| `extra.height` | 图片高度（前端用于坐标换算） |
| 文字池 | 千字文节选汉字（`天地玄黄宇宙洪荒...`） |

```java
// 生成文字点选验证码
CaptchaResult result = manager.generate("click", key);
// 前端展示 imageBase64，显示提示 "请依次点击：天、地、人"
// 用户依次点击 3 个文字后提交 JSON：
// {"clicks":[{"x":120,"y":80},{"x":200,"y":150},{"x":300,"y":100}]}
boolean ok = manager.verify("click", key,
        "{\"clicks\":[{\"x\":120,\"y\":80},{\"x\":200,\"y\":150},{\"x\":300,\"y\":100}]}");

// 或分号分隔坐标字符串（向后兼容）
boolean ok2 = manager.verify("click", key, "120,80;200,150;300,100");
```

点击验证码的配置通过 `clickTargetCount`（目标文字数量，默认 3）和 `clickDecoyCount`（干扰文字数量，默认 3）控制：

```java
CaptchaProperties props = CaptchaProperties.createDefault();
props.setWidth(300);           // 建议点击验证码使用更大画布
props.setHeight(180);
props.setClickTargetCount(4);  // 需要点击 4 个文字
props.setClickDecoyCount(6);   // 6 个干扰文字
props.setTolerance(25);        // 25 像素容差
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
    # 点击验证码配置
    click-target-count: 3      # 文字点选目标文字数量（1~8）
    click-decoy-count: 3       # 文字点选干扰文字数量（0~10）
    # 视觉配置
    font-family: Arial         # 字体名称（null=系统默认）
    font-style: 1              # 字体样式：PLAIN=0, BOLD=1, ITALIC=2, BOLD|ITALIC=3
    min-font-size: 28          # 最小字体大小（0=自动按图片高度计算）
    max-font-size: 36          # 最大字体大小（0=自动按图片高度计算）
    max-rotation-degree: 30    # 每个字符最大旋转角度（0~90）
    char-set: null             # 自定义字符集（null=默认字符集，排除易混淆字符 0/O/1/I/L）
    arc-interfere: true        # 是否添加弧线干扰（比直线更难被 OCR 识别）
    arc-interfere-count: 5     # 弧线干扰数量
    # 轨迹验证配置
    trajectory-enabled: true            # 是否启用轨迹验证（滑动/旋转验证码）
    min-trajectory-points: 5           # 轨迹最少点数（低于此值判定为机器行为）
    min-trajectory-duration-ms: 500    # 轨迹最短持续时间（毫秒）
    max-trajectory-duration-ms: 30000  # 轨迹最长持续时间（毫秒）
    max-jump-distance: 80              # 相邻轨迹点最大跳变距离
    # 自定义背景图配置（滑动/旋转验证码）
    background-image-path: null         # 背景图路径（文件路径或 classpath 资源）
    background-image-base64: null       # 背景图 base64 数据（优先级高于 path）
    # 水印配置
    watermark-text: null                # 文字水印内容（null=不添加文字水印）
    watermark-font-family: Arial        # 文字水印字体名称
    watermark-font-size: 12             # 文字水印字体大小
    watermark-color: 0x80666666         # 文字水印颜色（ARGB，0x80666666=半透明灰色）
    watermark-position: bottom-right    # 文字水印位置（top-left/top-right/bottom-left/bottom-right/center）
    watermark-rotation: 0               # 文字水印旋转角度
    watermark-image-base64: null        # 图片水印 base64 数据（null=不添加图片水印）
    watermark-opacity: 0.3              # 图片水印透明度（0.0~1.0）
    watermark-scale: 0.2                # 图片水印缩放比例（0.0~1.0，相对于画布宽度）
```

### 配置项说明

#### 基础配置

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
| `jaravel.captcha.click-target-count` | `int` | `3` | 文字点选验证码目标文字数量（1~8） |
| `jaravel.captcha.click-decoy-count` | `int` | `3` | 文字点选验证码干扰文字数量（0~10） |

#### 视觉配置

| 属性 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `jaravel.captcha.font-family` | `String` | `Arial` | 字体名称（`null` 表示使用系统默认） |
| `jaravel.captcha.font-style` | `int` | `1` | 字体样式：`Font.PLAIN=0`、`Font.BOLD=1`、`Font.ITALIC=2`、`Font.BOLD\|Font.ITALIC=3` |
| `jaravel.captcha.min-font-size` | `int` | `0` | 最小字体大小（`0` 表示自动按图片高度计算） |
| `jaravel.captcha.max-font-size` | `int` | `0` | 最大字体大小（`0` 表示自动按图片高度计算） |
| `jaravel.captcha.max-rotation-degree` | `int` | `30` | 每个字符最大旋转角度（0~90） |
| `jaravel.captcha.char-set` | `String` | `null` | 自定义字符集（`null` 表示使用默认字符集 `ABCDEFGHJKMNPQRSTUVWXYZ23456789`） |
| `jaravel.captcha.arc-interfere` | `boolean` | `true` | 是否添加弧线干扰（比直线更难被 OCR 识别） |
| `jaravel.captcha.arc-interfere-count` | `int` | `5` | 弧线干扰数量 |

#### 轨迹验证配置

| 属性 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `jaravel.captcha.trajectory-enabled` | `boolean` | `true` | 是否启用轨迹验证（滑动/旋转验证码） |
| `jaravel.captcha.min-trajectory-points` | `int` | `5` | 轨迹最少点数（低于此值判定为机器行为） |
| `jaravel.captcha.min-trajectory-duration-ms` | `long` | `500` | 轨迹最短持续时间（毫秒，低于此值判定为机器行为） |
| `jaravel.captcha.max-trajectory-duration-ms` | `long` | `30000` | 轨迹最长持续时间（毫秒，超过此值判定为可疑行为） |
| `jaravel.captcha.max-jump-distance` | `double` | `80` | 相邻轨迹点最大跳变距离（超过此值判定为非连续操作） |

#### 自定义背景图配置

| 属性 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `jaravel.captcha.background-image-path` | `String` | `null` | 滑动/旋转验证码的自定义背景图路径（文件路径或 classpath 资源路径），`null` 表示不使用 |
| `jaravel.captcha.background-image-base64` | `String` | `null` | 自定义背景图的 base64 数据（支持带 `data:image/...;base64,` 前缀），优先级高于 `background-image-path` |

#### 水印配置

| 属性 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `jaravel.captcha.watermark-text` | `String` | `null` | 文字水印内容（`null` 表示不添加文字水印） |
| `jaravel.captcha.watermark-font-family` | `String` | `"Arial"` | 文字水印字体名称 |
| `jaravel.captcha.watermark-font-size` | `int` | `12` | 文字水印字体大小 |
| `jaravel.captcha.watermark-color` | `int` | `0x80666666` | 文字水印颜色（ARGB 格式，如 `0x80808080` 表示半透明灰色） |
| `jaravel.captcha.watermark-position` | `String` | `"bottom-right"` | 文字水印位置：`top-left`、`top-right`、`bottom-left`、`bottom-right`、`center` |
| `jaravel.captcha.watermark-rotation` | `int` | `0` | 文字水印旋转角度（度） |
| `jaravel.captcha.watermark-image-base64` | `String` | `null` | 图片水印的 base64 数据（`null` 表示不添加图片水印） |
| `jaravel.captcha.watermark-opacity` | `float` | `0.3` | 图片水印透明度（0.0~1.0） |
| `jaravel.captcha.watermark-scale` | `float` | `0.2` | 图片水印缩放比例（0.0~1.0，相对于画布宽度） |

> **注意**：SpringBoot 配置类（`springboot.CaptchaProperties`）的字段名 `noise` / `interfereLines` 与核心层配置类（`CaptchaProperties`）的字段名 `noiseCount` / `interfereCount` 不同。自动装配时通过 `toCoreProperties()` 方法完成映射转换。其余视觉、轨迹、背景图与水印字段在两层中名称一致。

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
// 点击验证码配置
properties.setClickTargetCount(3);
properties.setClickDecoyCount(5);
// 视觉配置
properties.setFontFamily("Arial");
properties.setFontStyle(1);          // Font.BOLD
properties.setMinFontSize(28);
properties.setMaxFontSize(36);
properties.setMaxRotationDegree(30);
properties.setCharSet(null);         // null=默认字符集
properties.setArcInterfere(true);
properties.setArcInterfereCount(5);
// 轨迹验证配置
properties.setTrajectoryEnabled(true);
properties.setMinTrajectoryPoints(5);
properties.setMinTrajectoryDurationMs(500);
properties.setMaxTrajectoryDurationMs(30000);
properties.setMaxJumpDistance(80);
// 自定义背景图配置（滑动/旋转验证码）
properties.setBackgroundImagePath("backgrounds/bg1.png");  // 文件路径或 classpath 资源
properties.setBackgroundImageBase64(null);                  // base64 数据（优先级更高）
// 水印配置
properties.setWatermarkText("MyWatermark");                // 文字水印内容
properties.setWatermarkFontFamily("Arial");
properties.setWatermarkFontSize(12);
properties.setWatermarkColor(0x80666666);                   // ARGB，半透明灰色
properties.setWatermarkPosition("bottom-right");            // 位置
properties.setWatermarkRotation(0);                         // 旋转角度
properties.setWatermarkImageBase64(null);                   // 图片水印 base64
properties.setWatermarkOpacity(0.3f);                       // 图片水印透明度
properties.setWatermarkScale(0.2f);                         // 图片水印缩放比例
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
| `clickTargetCount` | `int` | `3` | 文字点选目标文字数量（1~8） |
| `clickDecoyCount` | `int` | `3` | 文字点选干扰文字数量（0~10） |
| `fontFamily` | `String` | `"Arial"` | 字体名称 |
| `fontStyle` | `int` | `1` | 字体样式（`Font.PLAIN=0`、`Font.BOLD=1`、`Font.ITALIC=2`、`Font.BOLD\|Font.ITALIC=3`） |
| `minFontSize` | `int` | `0` | 最小字体大小（`0` = 自动按图片高度计算） |
| `maxFontSize` | `int` | `0` | 最大字体大小（`0` = 自动按图片高度计算） |
| `maxRotationDegree` | `int` | `30` | 每个字符最大旋转角度（0~90） |
| `charSet` | `String` | `null` | 自定义字符集（`null` = 默认字符集） |
| `arcInterfere` | `boolean` | `true` | 是否添加弧线干扰 |
| `arcInterfereCount` | `int` | `5` | 弧线干扰数量 |
| `trajectoryEnabled` | `boolean` | `true` | 是否启用轨迹验证 |
| `minTrajectoryPoints` | `int` | `5` | 轨迹最少点数 |
| `minTrajectoryDurationMs` | `long` | `500` | 轨迹最短持续时间（毫秒） |
| `maxTrajectoryDurationMs` | `long` | `30000` | 轨迹最长持续时间（毫秒） |
| `maxJumpDistance` | `double` | `80` | 相邻轨迹点最大跳变距离 |
| `backgroundImagePath` | `String` | `null` | 滑动/旋转验证码的自定义背景图路径（文件路径或 classpath 资源），`null` 表示不使用 |
| `backgroundImageBase64` | `String` | `null` | 自定义背景图的 base64 数据（优先级高于 `backgroundImagePath`） |
| `watermarkText` | `String` | `null` | 文字水印内容（`null` 表示不添加文字水印） |
| `watermarkFontFamily` | `String` | `"Arial"` | 文字水印字体名称 |
| `watermarkFontSize` | `int` | `12` | 文字水印字体大小 |
| `watermarkColor` | `int` | `0x80666666` | 文字水印颜色（ARGB 格式） |
| `watermarkPosition` | `String` | `"bottom-right"` | 文字水印位置（`top-left`/`top-right`/`bottom-left`/`bottom-right`/`center`） |
| `watermarkRotation` | `int` | `0` | 文字水印旋转角度（度） |
| `watermarkImageBase64` | `String` | `null` | 图片水印的 base64 数据（`null` 表示不添加图片水印） |
| `watermarkOpacity` | `float` | `0.3` | 图片水印透明度（0.0~1.0） |
| `watermarkScale` | `float` | `0.2` | 图片水印缩放比例（0.0~1.0，相对于画布宽度） |

> 核心层 `CaptchaProperties` 还提供 `getEffectiveChars()` 方法：当 `charSet` 为空时返回默认字符集 `ABCDEFGHJKMNPQRSTUVWXYZ23456789`，否则返回自定义字符集的字符数组。默认字符集常量 `DEFAULT_CHARS` 也定义在本类中（包级可见）。

---

## 8. 轨迹验证

滑动（`slider`）与旋转（`rotate`）验证码在启用 `trajectory-enabled` 后，不仅校验用户提交的最终位置/角度，还会校验拖动过程产生的轨迹是否具有人类行为特征，从而有效防范自动化脚本直接提交最终值绕过验证。

### 8.1 用户输入格式变更

启用轨迹验证时，前端必须以 **JSON 字符串** 提交用户输入，格式如下：

```json
{
  "value": 123,
  "trajectory": [
    {"t": 0, "v": 0},
    {"t": 50, "v": 5},
    {"t": 120, "v": 18},
    {"t": 200, "v": 42},
    {"t": 280, "v": 78},
    {"t": 350, "v": 110},
    {"t": 420, "v": 123}
  ]
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `value` | `number` | 最终值。滑动为滑块 x 坐标（像素），旋转为最终角度（0~360） |
| `trajectory` | `array` | 拖动过程中采集的轨迹点序列 |
| `trajectory[].t` | `number` | 相对拖动开始的时间戳（毫秒），首个点通常为 `0` |
| `trajectory[].v` | `number` | 该时刻的值。滑动为 x 坐标，旋转为角度 |

### 8.2 向后兼容

当 `trajectory-enabled: false` 时（或轨迹点为空且未启用轨迹验证），`SliderCaptcha` / `RotateCaptcha` 的 `doVerify` 仍支持直接提交纯数字字符串（如 `"123"`、`"90"`），与旧版本完全兼容。`TrajectoryValidator.extractValue()` 会自动识别输入是 JSON 还是纯数字。

### 8.3 五个验证维度

`TrajectoryValidator.validate()` 按以下顺序依次校验，任一维度不通过即判定为机器行为：

| 维度 | 检查内容 | 判定规则 |
| --- | --- | --- |
| 1. 点数检查 | 轨迹点数 | 必须 ≥ `min-trajectory-points`（默认 5）。机器行为往往点数过少 |
| 2. 时长检查 | 总拖动时长 | 必须在 `[min-trajectory-duration-ms, max-trajectory-duration-ms]`（默认 500ms ~ 30000ms）区间内。过快或过慢均判定为可疑 |
| 3. 连续性检查 | 相邻点值差 | 不能超过 `max-jump-distance`（默认 80）。排除瞬移式非连续操作 |
| 4. 非匀速检查 | 速度方差 | 人类拖动具有"加速→匀速→减速"特征，速度方差应大于阈值。匀速直线运动判定为机器行为 |
| 5. 加速度方向多样性 | 加速度正负方向 | 人类拖动过程中加速度方向会变化（先正后负），全程同方向加速度判定为可疑（仅在总位移较大时强制要求） |

> 完整验证逻辑实现于 `com.weacsoft.jaravel.vendor.captcha.TrajectoryValidator`，详见 [AI-API.md](AI-API.md)。

### 8.4 前端轨迹采集示例

模块内置了一个完整的前端演示页面 `src/main/resources/static/captcha-demo.html`，其中包含滑动与旋转验证码的轨迹采集逻辑。以下为关键代码片段（滑动验证码）：

```javascript
// 滑动轨迹采集核心逻辑（摘自 captcha-demo.html）
(function initSliderDrag() {
    const handle = document.getElementById("slider-handle");
    const progress = document.getElementById("slider-progress");
    let dragging = false, startX = 0, startTime = 0;

    // 鼠标按下：记录起点与起始时间，初始化轨迹首点 {t:0, v:0}
    handle.addEventListener("mousedown", function (e) {
        dragging = true;
        startX = e.clientX;
        startTime = Date.now();
        handle._drag = { value: 0, points: [{ t: 0, v: 0 }] };
        e.preventDefault();
    });

    // 鼠标移动：计算位移并追加轨迹点 {t: 相对毫秒, v: 当前 x 坐标}
    document.addEventListener("mousemove", function (e) {
        if (!dragging) return;
        let dx = e.clientX - startX;
        dx = Math.max(0, Math.min(dx, handle._drag.max));  // 限制在有效范围
        handle._drag.value = dx;
        handle._drag.points.push({ t: Date.now() - startTime, v: Math.round(dx) });
        handle.style.left = dx + "px";
        progress.style.width = dx + "px";
    });

    document.addEventListener("mouseup", function () { dragging = false; });
})();

// 提交验证：将最终值与轨迹组装为 JSON 提交
async function verifySlider() {
    const d = document.getElementById("slider-handle")._drag;
    if (!d || d.points.length < 2) { setResult("slider", false, "请拖动滑块"); return; }
    const payload = { value: Math.round(d.value), trajectory: d.points };
    const input = JSON.stringify(payload);   // {"value":123,"trajectory":[...]}
    const res = await apiVerify(captchaKey, input);
    // ...
}
```

旋转验证码的轨迹采集逻辑类似，区别在于将横向位移映射为角度（如每像素 0.5 度），并将 `value` 与 `trajectory[].v` 记录为角度值。完整实现参见 `captcha-demo.html` 中的 `initRotateDrag()` 与 `verifyRotate()`。

### 8.5 关闭轨迹验证

如需关闭轨迹验证（例如在测试环境或对兼容性有要求时），设置：

```yaml
jaravel:
  captcha:
    trajectory-enabled: false
```

关闭后，滑动/旋转验证码仅需提交纯数字字符串即可通过位置/角度校验，与旧版本行为一致。生成结果中的 `extra.trajectoryEnabled` 也会相应返回 `false`，前端可据此决定提交格式。

---

## 9. 可配置性

captcha 模块的视觉呈现高度可配置，所有视觉相关参数均集中在 `CaptchaProperties`（核心层）与 `springboot.CaptchaProperties`（SpringBoot 适配层）中，可通过 `application.yml` 或 Java 代码调整。

### 9.1 自定义字符集（`char-set`）

默认字符集为 `ABCDEFGHJKMNPQRSTUVWXYZ23456789`（已排除易混淆字符 `0`/`O`/`1`/`I`/`L`）。如需自定义（例如仅使用数字、或加入小写字母），设置 `char-set`：

```yaml
jaravel:
  captcha:
    char-set: "0123456789"           # 仅数字
    # char-set: "abcdefghjkmnpqrstuvwxyz23456789"  # 小写字母 + 数字
```

设为 `null` 时回退到默认字符集。核心层通过 `CaptchaProperties.getEffectiveChars()` 获取最终生效的字符数组，`AbstractCaptcha.randomString()` 会使用该方法生成的字符集。

### 9.2 字体配置（`font-family` / `font-style` / `min-font-size` / `max-font-size`）

| 配置项 | 说明 |
| --- | --- |
| `font-family` | 字体名称，如 `Arial`、`Microsoft YaHei`、`Serif` 等。`null` 表示使用系统默认 |
| `font-style` | 字体样式：`Font.PLAIN=0`、`Font.BOLD=1`、`Font.ITALIC=2`、`Font.BOLD\|Font.ITALIC=3` |
| `min-font-size` | 最小字体大小（像素）。设为 `0` 时自动按图片高度计算（`height - 14`） |
| `max-font-size` | 最大字体大小（像素）。设为 `0` 时自动按图片高度计算（`height - 6`） |

```yaml
jaravel:
  captcha:
    font-family: "Microsoft YaHei"
    font-style: 1            # 粗体
    min-font-size: 28
    max-font-size: 36
```

### 9.3 字符旋转角度（`max-rotation-degree`）

每个字符会随机旋转一个角度以增加识别难度，旋转范围由 `max-rotation-degree` 控制（0~90）。设为 `0` 时字符不旋转。

```yaml
jaravel:
  captcha:
    max-rotation-degree: 30   # 每个字符在 -30° ~ +30° 之间随机旋转
```

### 9.4 弧线干扰（`arc-interfere` / `arc-interfere-count`）

除了传统的直线干扰线（`interfere-lines`），模块还提供弧线干扰（二次贝塞尔曲线），比直线更难被 OCR 引擎识别：

| 配置项 | 说明 |
| --- | --- |
| `arc-interfere` | 是否启用弧线干扰（默认 `true`） |
| `arc-interfere-count` | 弧线数量（默认 `5`） |

```yaml
jaravel:
  captcha:
    arc-interfere: true
    arc-interfere-count: 5
```

> 弧线干扰通过 `AbstractCaptcha.addArcInterference()` 方法绘制，使用 `java.awt.geom.QuadCurve2D` 实现二次贝塞尔曲线。

### 9.5 完整配置示例

```yaml
jaravel:
  captcha:
    # 基础配置
    enabled: true
    width: 200
    height: 60
    length: 5
    expire-seconds: 180
    case-sensitive: true
    tolerance: 6.0
    noise: 60
    interfere-lines: 25
    # 视觉配置
    font-family: "Microsoft YaHei"
    font-style: 1
    min-font-size: 28
    max-font-size: 36
    max-rotation-degree: 25
    char-set: "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    arc-interfere: true
    arc-interfere-count: 6
    # 轨迹验证配置
    trajectory-enabled: true
    min-trajectory-points: 6
    min-trajectory-duration-ms: 600
    max-trajectory-duration-ms: 20000
    max-jump-distance: 60
```

---

## 10. 自定义背景图

滑动（`slider`）与旋转（`rotate`）验证码支持自定义背景图，替换默认的随机渐变背景，使验证码视觉更贴合业务场景（如使用品牌图片、风景图等作为底图）。

### 10.1 支持的验证码类型

| 验证码 | 支持自定义背景图 | 说明 |
| --- | --- | --- |
| 数字（`number`） | 否 | 使用白色背景 + 噪点 / 干扰线 |
| 算术（`arithmetic`） | 否 | 使用白色背景 + 噪点 / 干扰线 |
| 滑动（`slider`） | 是 | 背景图上抠缺口，滑块拼回 |
| 旋转（`rotate`） | 是 | 背景图旋转，前端拖动转回正方向 |

### 10.2 两种配置方式

自定义背景图支持两种配置方式，`backgroundImageBase64` 优先级高于 `backgroundImagePath`：

| 配置项 | 说明 | 适用场景 |
| --- | --- | --- |
| `background-image-path` | 文件路径或 classpath 资源路径。先按文件路径查找，找不到则尝试 classpath 加载 | 静态图片资源，部署时随包发布 |
| `background-image-base64` | base64 编码的图片数据（支持带 `data:image/...;base64,` 前缀或不带前缀） | 动态生成 / 运行时指定的图片，如从数据库 / 对象存储读取 |

> **未配置时**：当 `backgroundImagePath` 与 `backgroundImageBase64` 均为 `null` 时，滑动 / 旋转验证码会使用 `AbstractCaptcha.drawRandomBackground()` 随机生成的渐变背景 + 色块纹理。

> **加载失败时**：若配置的图片加载失败（文件不存在、base64 解码失败等），会静默回退到随机生成的渐变背景，不会抛出异常。

> **尺寸缩放**：加载的背景图会按双线性插值缩放到验证码画布尺寸（滑动为 `width x height`，旋转为 `max(width, 200)` 的正方形）。

### 10.3 配置示例

**SpringBoot 配置（`application.yml`）**：

```yaml
jaravel:
  captcha:
    # 方式一：使用 classpath 资源路径
    background-image-path: "captcha/backgrounds/scene1.jpg"
    # 方式二：使用 base64 数据（优先级更高，会覆盖 path）
    # background-image-base64: "data:image/png;base64,iVBORw0KGgoAAAANSUhEUg..."
```

**核心层配置（独立使用）**：

```java
CaptchaProperties props = CaptchaProperties.createDefault();

// 方式一：文件路径 / classpath 资源
props.setBackgroundImagePath("captcha/backgrounds/scene1.jpg");

// 方式二：base64 数据（优先级更高）
// String base64 = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUg...";
// props.setBackgroundImageBase64(base64);

SliderCaptcha slider = new SliderCaptcha(new MemoryCaptchaStore(), props);
CaptchaResult result = slider.generate("my-key");  // 使用自定义背景图
```

### 10.4 base64 图片构造示例

可通过 `BufferedImage` 动态生成图片并转为 base64：

```java
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import javax.imageio.ImageIO;

private String toBase64Image(int width, int height) {
    BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    g.setColor(Color.BLUE);
    g.fillRect(0, 0, width, height);
    g.dispose();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try {
        ImageIO.write(img, "png", baos);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
}

// 使用
props.setBackgroundImageBase64(toBase64Image(160, 50));
```

---

## 11. 水印

五种验证码（数字、算术、滑动、旋转、文字点选）均支持叠加**文字水印**和**图片水印**，用于版权标识、品牌曝光或防伪。水印由 `AbstractCaptcha.applyWatermark()` 统一处理，在验证码内容（字符、缺口、朝向标记等）绘制完成后叠加，不影响验证逻辑。

### 11.1 文字水印

文字水印支持配置内容、字体、大小、颜色（ARGB 含透明度）、位置和旋转角度。

| 配置项 | 说明 |
| --- | --- |
| `watermark-text` | 文字水印内容（`null` 表示不添加文字水印） |
| `watermark-font-family` | 字体名称，如 `Arial`、`Microsoft YaHei` |
| `watermark-font-size` | 字体大小（像素） |
| `watermark-color` | 颜色（ARGB 格式，如 `0x80666666` 表示半透明灰色，`0x80` 为 alpha 通道） |
| `watermark-position` | 位置：`top-left`、`top-right`、`bottom-left`、`bottom-right`、`center` |
| `watermark-rotation` | 旋转角度（度，绕文字中心旋转） |

```yaml
jaravel:
  captcha:
    watermark-text: "© MyCorp"
    watermark-font-family: "Arial"
    watermark-font-size: 12
    watermark-color: 0x80666666      # 半透明灰色
    watermark-position: "bottom-right"
    watermark-rotation: 0
```

### 11.2 图片水印

图片水印支持配置 base64 数据、透明度和缩放比例，默认居中显示。

| 配置项 | 说明 |
| --- | --- |
| `watermark-image-base64` | 图片水印的 base64 数据（`null` 表示不添加图片水印，支持带 `data:image/...;base64,` 前缀） |
| `watermark-opacity` | 透明度（0.0~1.0，默认 `0.3`） |
| `watermark-scale` | 缩放比例（0.0~1.0，相对于画布宽度，默认 `0.2`） |

```yaml
jaravel:
  captcha:
    watermark-image-base64: "data:image/png;base64,iVBORw0KGgoAAAANSUhEUg..."
    watermark-opacity: 0.3
    watermark-scale: 0.2
```

### 11.3 水印叠加顺序

当同时配置了文字水印和图片水印时，`applyWatermark()` 会**先绘制文字水印，再绘制图片水印**，即图片水印叠加在文字水印之上。两种水印可独立配置，互不影响。

### 11.4 适用范围

| 验证码 | 文字水印 | 图片水印 |
| --- | --- | --- |
| 数字（`number`） | 支持 | 支持 |
| 算术（`arithmetic`） | 支持 | 支持 |
| 滑动（`slider`） | 支持 | 支持 |
| 旋转（`rotate`） | 支持（叠加在旋转后的展示图上） | 支持（叠加在旋转后的展示图上） |
| 文字点选（`click`） | 支持 | 支持 |

### 11.5 配置示例

```java
CaptchaProperties props = CaptchaProperties.createDefault();

// 文字水印
props.setWatermarkText("© MyCorp");
props.setWatermarkFontFamily("Microsoft YaHei");
props.setWatermarkFontSize(12);
props.setWatermarkColor(0x80666666);       // 半透明灰色
props.setWatermarkPosition("bottom-right");
props.setWatermarkRotation(0);

// 图片水印（与文字水印可共存）
props.setWatermarkImageBase64(toBase64Image(60, 30));
props.setWatermarkOpacity(0.3f);
props.setWatermarkScale(0.2f);

NumberCaptcha captcha = new NumberCaptcha(new MemoryCaptchaStore(), props);
CaptchaResult result = captcha.generate("my-key");  // 图片同时含文字水印与图片水印
```

---

## 12. 存储选择

验证码答案的存储通过 `CaptchaStore` 接口抽象，内置两种实现：

### 12.1 MemoryCaptchaStore（默认）

| 项 | 说明 |
| --- | --- |
| 实现类 | `com.weacsoft.jaravel.vendor.captcha.MemoryCaptchaStore` |
| 底层 | `ConcurrentHashMap` |
| 线程安全 | 是 |
| TTL | 自带过期机制，读取时惰性清理，另提供 `cleanup()` 主动清理 |
| 适用场景 | 单机、低并发场景 |
| 跨进程 | 不支持（进程重启即失） |

### 12.2 CacheStoreCaptchaStore（跨进程）

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

## 13. 自定义验证码类型

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
    manager.register(new ClickCaptcha(store, coreProps));
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

> `AbstractCaptcha` 还提供丰富的图像工具方法供子类复用：`createImage`、`createArgbImage`、`randomColor`、`drawRandomBackground`、`loadBackgroundImage`（加载自定义背景图）、`addNoise`、`addInterfereLines`、`addArcInterference`（弧线干扰）、`drawText`（支持配置化字体/大小/旋转）、`applyWatermark`（叠加文字 / 图片水印）、`toBase64`、`randomString`（支持自定义字符集）等。各方法签名详见 [AI-API.md](AI-API.md)。

---

## 14. 前端 JavaScript API

模块提供 `jaravel-captcha.js` 前端库（零依赖），封装验证码 UI 构建、事件绑定、轨迹采集与加密。前端采用**事件驱动模式**：用户完成验证操作后触发 `complete` 事件（不自动提交到后端），由业务方在事件回调中决定后续处理。

### 14.1 引入方式

```html
<!-- 直接引入（SpringBoot 项目中静态资源自动映射） -->
<script src="/static/jaravel-captcha.js"></script>
```

引入后全局可用两个对象：
- **`JaravelCaptcha`** — 加解密工具（`encrypt`/`decrypt`/`fetchCaptcha`/`submitCaptcha` 等）
- **`Captcha`** — OOP 验证码组件类

### 14.2 快速开始

```html
<div id="captcha-container"></div>

<script>
// 初始化滑动验证码
const captcha = Captcha.init('captcha-container', {
    type: 'slider',
    apiUrl: '/api/captcha/generate',
    encryptionType: 'aes',
    encryptionKey: 'my-secret-key'
});

// 注册事件监听器（链式调用）
captcha
    .on('beforeGet', (type) => {
        console.log('正在获取验证码，类型:', type);
    })
    .on('afterGet', (captchaKey, captchaData) => {
        console.log('验证码已加载, key:', captchaKey);
    })
    .on('complete', (captchaKey, captchaInput, rawInput) => {
        console.log('用户完成验证');
        console.log('  captchaKey:', captchaKey);
        console.log('  captchaInput (加密后):', captchaInput);
        console.log('  rawInput (原始明文):', rawInput);

        // 在此处提交到后端验证
        submitToBackend(captchaKey, captchaInput);
    });

async function submitToBackend(captchaKey, captchaInput) {
    const resp = await fetch('/api/captcha/verify', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            type: 'slider',
            captchaKey: captchaKey,
            input: captchaInput
        })
    });
    const result = await resp.json();
    if (result.code === 200) {
        alert('验证通过');
    } else {
        alert('验证失败');
        captcha.refresh();  // 刷新验证码
    }
}
</script>
```

### 14.3 配置选项

`Captcha.init(containerId, options)` 的 `options` 参数：

| 属性 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `type` | `String` | `'number'` | 验证码类型：`'number'`/`'arithmetic'`/`'slider'`/`'rotate'`/`'click'` |
| `apiUrl` | `String` | `'/api/captcha/generate'` | 生成验证码的 API 地址（GET 请求，附 `?type=` 查询参数） |
| `encryptionType` | `String` | `'none'` | 加密类型：`'none'`（纯 Base64）/`'aes'`（AES-CBC）/`'rsa'`（RSA-OAEP） |
| `encryptionKey` | `String` | `null` | 加密密钥（AES 为对称密钥字符串，RSA 为 Base64 公钥） |
| `config` | `Object` | `null` | per-instance 后端配置覆盖（如 `{clickTargetCount: 6, length: 6}`），作为查询参数传给 generate API |

### 14.4 事件系统

前端采用事件驱动模式，通过 `on`/`off` 方法注册/移除事件监听器：

| 方法 | 参数 | 返回 | 说明 |
| --- | --- | --- | --- |
| `on` | `event, callback` | `Captcha`（this） | 注册事件监听器，支持链式调用 |
| `off` | `event, callback` | `Captcha`（this） | 移除事件监听器。不传 `callback` 则移除该事件的所有监听器 |

支持的事件：

| 事件 | 参数 | 触发时机 |
| --- | --- | --- |
| `beforeGet` | `(type)` | 获取验证码前（含刷新场景） |
| `afterGet` | `(captchaKey, captchaData)` | 验证码加载并渲染完成后 |
| `complete` | `(captchaKey, captchaInput, rawInput)` | 用户完成前端验证操作后（不提交到后端） |

> **complete 事件触发时机**：
> - `number`/`arithmetic`：用户在输入框按 **Enter** 键时触发
> - `slider`：用户拖动滑块**松开**时触发
> - `rotate`：用户拖动旋转手柄**松开**时触发
> - `click`：用户依次**点击完所有目标文字**时触发

`complete` 事件参数说明：

| 参数 | 说明 |
| --- | --- |
| `captchaKey` | 验证码标识（字符串），用于提交到后端验证 |
| `captchaInput` | 加密后的用户输入（Base64 编码的密文），直接传给后端 verify 接口的 `input` 参数 |
| `rawInput` | 原始明文输入（仅供调试/日志使用，不要直接传给后端） |

### 14.5 实例方法

| 方法 | 参数 | 返回 | 说明 |
| --- | --- | --- | --- |
| `getCaptchaKey` | 无 | `String\|null` | 获取当前验证码标识 |
| `getCaptchaInput` | 无 | `Promise<string>` | 获取用户输入（加密后），可在 `complete` 回调中或外部调用 |
| `show` | 无 | `void` | 显示验证码组件 |
| `hide` | 无 | `void` | 隐藏验证码组件 |
| `refresh` | 无 | `void` | 刷新验证码（触发 `beforeGet` → 加载 → `afterGet`） |
| `destroy` | 无 | `void` | 销毁组件，清理 DOM 和事件监听器 |
| `on` | `event, callback` | `Captcha` | 注册事件监听器 |
| `off` | `event, callback` | `Captcha` | 移除事件监听器 |

### 14.6 per-instance 配置覆盖

通过 `config` 选项可为每个验证码实例单独覆盖后端配置，这些配置作为查询参数传给 generate API：

```javascript
// 文字点选验证码 — 自定义目标数量和画布尺寸
const clickCaptcha = Captcha.init('click-container', {
    type: 'click',
    apiUrl: '/api/captcha/generate',
    config: {
        clickTargetCount: 4,   // 点击 4 个目标文字
        clickDecoyCount: 6,    // 6 个干扰文字
        width: 300,            // 画布宽度
        height: 180            // 画布高度
    }
}).on('complete', async (captchaKey, captchaInput) => {
    // 提交到后端验证
    const resp = await fetch('/api/captcha/verify', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            type: 'click',
            captchaKey: captchaKey,
            input: captchaInput
        })
    });
    const result = await resp.json();
    if (result.code !== 200) {
        clickCaptcha.refresh();  // 验证失败，刷新
    }
});
```

> 后端 `CaptchaController` 需要读取这些查询参数并应用到 `CaptchaProperties`。示例：

```java
@GetMapping("/generate")
public CaptchaResult generate(@RequestParam String type,
                              @RequestParam(required = false) Integer clickTargetCount,
                              @RequestParam(required = false) Integer clickDecoyCount,
                              @RequestParam(required = false) Integer width,
                              @RequestParam(required = false) Integer height) {
    CaptchaProperties props = captchaProperties.toCoreProperties();
    // 应用 per-instance 覆盖
    if (clickTargetCount != null) props.setClickTargetCount(clickTargetCount);
    if (clickDecoyCount != null) props.setClickDecoyCount(clickDecoyCount);
    if (width != null) props.setWidth(width);
    if (height != null) props.setHeight(height);

    // 使用覆盖后的配置创建临时 manager
    CaptchaManager tempManager = new CaptchaManager(captchaManager.getStore(), props);
    // 注册对应类型的验证码（使用覆盖后的配置）
    // ... 或直接通过 Captcha 实例生成
    String key = UUID.randomUUID().toString();
    return tempManager.generate(type, key);
}
```

### 14.7 加密模式

前端支持三种加密模式，加密后的 `captchaInput` 直接传给后端 verify 接口：

| 模式 | 说明 | 密钥 |
| --- | --- | --- |
| `none` | 纯 Base64 编码（默认） | 不需要 |
| `aes` | AES-CBC 加密（key = SHA-256(userKey)[0:16]） | 对称密钥字符串 |
| `rsa` | RSA-OAEP 加密 | Base64 公钥 |

> 当 Web Crypto API 不可用时（如非 HTTPS 环境），`aes`/`rsa` 会自动降级为 `none`，仅在控制台输出警告。

```javascript
// AES 加密
const captcha = Captcha.init('container', {
    type: 'slider',
    encryptionType: 'aes',
    encryptionKey: 'my-secret-key'  // 后端需用相同密钥解密
});

// RSA 加密
const captcha2 = Captcha.init('container2', {
    type: 'number',
    encryptionType: 'rsa',
    encryptionKey: 'MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...'  // Base64 公钥
});
```

### 14.8 完整示例

```html
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>验证码示例</title>
    <script src="/static/jaravel-captcha.js"></script>
</head>
<body>
    <h3>滑动验证码</h3>
    <div id="slider-captcha"></div>

    <h3>文字点选验证码</h3>
    <div id="click-captcha"></div>

    <script>
    // 滑动验证码
    const slider = Captcha.init('slider-captcha', {
        type: 'slider',
        apiUrl: '/api/captcha/generate',
        encryptionType: 'aes',
        encryptionKey: 'my-secret-key'
    }).on('complete', async (key, input) => {
        const resp = await fetch('/api/captcha/verify', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ type: 'slider', captchaKey: key, input: input })
        });
        const result = await resp.json();
        if (result.code === 200) {
            console.log('滑动验证通过');
        } else {
            slider.refresh();
        }
    });

    // 文字点选验证码（per-instance 配置）
    const click = Captcha.init('click-captcha', {
        type: 'click',
        apiUrl: '/api/captcha/generate',
        config: { clickTargetCount: 3, clickDecoyCount: 5, width: 300, height: 180 }
    }).on('complete', async (key, input) => {
        const resp = await fetch('/api/captcha/verify', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ type: 'click', captchaKey: key, input: input })
        });
        const result = await resp.json();
        if (result.code === 200) {
            console.log('点选验证通过');
        } else {
            click.refresh();
        }
    });
    </script>
</body>
</html>
```

> 更多 API 细节（方法签名、参数格式、内部方法等）详见 [AI-API.md](AI-API.md) 的 "Frontend JavaScript API" 章节。
