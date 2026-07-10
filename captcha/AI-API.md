# captcha AI-API Reference

> Module: `captcha` | Package: `com.weacsoft.jaravel.vendor.captcha` | Version: 0.1.1

## Overview
captcha 模块提供四种验证码（图片数字 `number`、算术 `arithmetic`、滑动 `slider`、旋转 `rotate`），核心层为纯 Java 实现（基于 `java.awt` 与 `java.util.Base64`），无 SpringBoot 依赖，可独立使用。采用模板方法模式：`AbstractCaptcha` 封装生成/验证流程，子类只需实现 `doGenerate` 与 `doVerify`。存储通过 `CaptchaStore` SPI 解耦，内置 `MemoryCaptchaStore`（内存）与 `CacheStoreCaptchaStore`（适配 jaravel cache 模块）。`CaptchaManager` 统一管理多类型注册与调用，并提供无状态 token 验证能力。SpringBoot 3 适配层提供自动装配与 `@ConfigurationProperties` 绑定。

滑动与旋转验证码支持**轨迹行为分析**：通过 `TrajectoryValidator` 校验用户拖动轨迹的人类行为特征（点数、时长、连续性、非匀速、加速度多样性），防范自动化脚本直接提交最终值。视觉呈现（字体、字符集、旋转角度、弧线干扰等）高度可配置，配置项集中在 `CaptchaProperties`。滑动与旋转验证码支持**自定义背景图**（通过文件路径或 base64 数据指定），四种验证码均支持叠加**文字水印与图片水印**（可配置内容、字体、颜色、位置、旋转角度、透明度、缩放比例），水印在验证码内容绘制完成后由 `AbstractCaptcha.applyWatermark()` 统一叠加。

## Package Structure

模块按职责拆分为以下子包：

| 子包 | 类 |
|------|-----|
| `com.weacsoft.jaravel.vendor.captcha`（根包） | `CaptchaManager`, `CaptchaContext`, `CaptchaResult`, `VerifyResult`, `CaptchaProperties`, `TrajectoryValidator` |
| `com.weacsoft.jaravel.vendor.captcha.generator` | `Captcha`(接口), `AbstractCaptcha`, `ArithmeticCaptcha`, `NumberCaptcha`, `SliderCaptcha`, `RotateCaptcha`, `ClickCaptcha` |
| `com.weacsoft.jaravel.vendor.captcha.store` | `CaptchaStore`(接口), `MemoryCaptchaStore`, `CacheStoreCaptchaStore` |
| `com.weacsoft.jaravel.vendor.captcha.crypto` | `CaptchaCrypto` |
| `com.weacsoft.jaravel.vendor.captcha.springboot` | `CaptchaAutoConfiguration`, `CaptchaProperties`（SpringBoot 适配层） |

## Classes & Interfaces

### Captcha
- **Type**: interface
- **Package**: `com.weacsoft.jaravel.vendor.captcha.generator`
- **Description**: 验证码接口。所有验证码类型（图片数字、算术、滑动、旋转等）实现此接口。核心层不依赖 SpringBoot，可独立使用。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `generate` | `String captchaKey` | `CaptchaResult` | 生成验证码。`captchaKey` 为验证码唯一标识（用于后续验证时查找），返回验证码结果（含 base64 图片、token 等） |
| `verify` | `String captchaKey, String userInput` | `boolean` | 验证用户提交的答案。`captchaKey` 为验证码标识，`userInput` 为用户输入（数字验证码填字符，滑动填 x 坐标或 JSON，旋转填角度或 JSON；启用轨迹验证时滑动/旋转需提交 JSON，详见 [SliderCaptcha](#slidercaptcha) / [RotateCaptcha](#rotatecaptcha)） |
| `getType` | 无 | `String` | 返回验证码类型名称（如 `"number"`、`"arithmetic"`、`"slider"`、`"rotate"`） |

#### Usage Example
```java
Captcha captcha = new NumberCaptcha();
CaptchaResult result = captcha.generate("my-key");
boolean ok = captcha.verify("my-key", "AB23");
```

---

### CaptchaContext
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.captcha`
- **Description**: 验证码生成上下文。由 `AbstractCaptcha.generate()` 在调用 `doGenerate` 前构造，携带本次生成所需的 `captchaKey` 与 `CaptchaProperties`，并提供一个 `answer` 写回通道：子类在 `doGenerate` 中计算答案后通过 `setAnswer()` 写入，由模板方法统一存入 `CaptchaStore`。这样可以避免把答案放进会下发到前端的 `CaptchaResult`，降低答案泄露风险。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `CaptchaContext` | `String captchaKey, CaptchaProperties properties` | 构造方法 | 创建生成上下文 |
| `getCaptchaKey` | 无 | `String` | 获取验证码标识 |
| `getProperties` | 无 | `CaptchaProperties` | 获取配置属性 |
| `getAnswer` | 无 | `String` | 获取答案（由子类在 doGenerate 中写入） |
| `setAnswer` | `String answer` | `void` | 写入答案（子类在 doGenerate 中调用） |

#### Usage Example
```java
// 在 AbstractCaptcha 子类的 doGenerate 中使用
@Override
protected CaptchaResult doGenerate(CaptchaContext context) {
    CaptchaProperties p = context.getProperties();
    String answer = randomString(p.getLength(), new Random());
    context.setAnswer(answer);  // 回写答案，由模板方法存入 store
    // ... 绘制图片
    CaptchaResult result = new CaptchaResult();
    result.setImageBase64(toBase64(image));
    return result;
}
```

---

### AbstractCaptcha
- **Type**: abstract class
- **Package**: `com.weacsoft.jaravel.vendor.captcha.generator`
- **Description**: 验证码抽象基类，实现 `Captcha` 接口，提供模板方法与公共图像处理逻辑。采用模板方法模式：`generate()` 构造上下文、调用 `doGenerate()` 生成结果、将答案写入 `CaptchaStore` 并生成无状态 token；`verify()` 从 `CaptchaStore` 一次性取出答案交给 `doVerify()` 比对。另提供 `generateToken` / `verifyToken` 用于无状态场景。核心层不依赖任何第三方库，图像生成基于 `java.awt`，编码基于 `java.util.Base64`。
- **Implements**: `Captcha`

#### Fields

| Field | Type | Modifiers | Description |
|-------|------|-----------|-------------|
| `store` | `CaptchaStore` | `protected` | 验证码存储 |
| `properties` | `CaptchaProperties` | `protected` | 配置属性 |

> 默认字符集常量 `DEFAULT_CHARS`（`ABCDEFGHJKMNPQRSTUVWXYZ23456789`，排除易混淆字符 0/O/1/I/L）已移至 `CaptchaProperties`（包级可见），通过 `CaptchaProperties.getEffectiveChars()` 获取最终生效的字符数组。

#### Constructors

| Constructor | Parameters | Description |
|-------------|-----------|-------------|
| `AbstractCaptcha` | 无 | 默认构造：使用 `MemoryCaptchaStore` 与默认配置 |
| `AbstractCaptcha` | `CaptchaStore store, CaptchaProperties properties` | 指定存储与配置构造 |

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `doGenerate` | `CaptchaContext context` | `CaptchaResult`（abstract, protected） | 子类实现：生成验证码图片并通过 `context.setAnswer()` 回写答案 |
| `doVerify` | `String answer, String userInput` | `boolean`（abstract, protected） | 子类实现：比对真实答案与用户输入 |
| `generate` | `String captchaKey` | `CaptchaResult` | 模板方法：构造上下文 -> doGenerate -> 填充元信息 -> 存答案到 store -> 生成 token |
| `verify` | `String captchaKey, String userInput` | `boolean` | 模板方法：从 store 一次性取出答案 -> doVerify 比对 |
| `generateToken` | `String captchaKey, String answer, long expireTime` | `String`（protected） | 生成无状态 token：三段 Base64 以 `.` 拼接 |
| `verifyToken` | `String token, String userInput` | `boolean`（protected） | 校验无状态 token：解码取答案与过期时间，过期或解码失败返回 false，否则交由 doVerify 比对 |
| `createImage` | `int width, int height` | `BufferedImage`（protected） | 创建白色背景的 RGB 图片 |
| `createArgbImage` | `int width, int height` | `BufferedImage`（protected） | 创建透明背景的 ARGB 图片（用于滑块等需要透明通道的场景） |
| `randomColor` | `Random random` | `Color`（protected） | 生成随机颜色（限制亮度避免过白） |
| `drawRandomBackground` | `Graphics2D g, int width, int height, Random random` | `void`（protected） | 绘制随机渐变背景 + 色块（滑动/旋转验证码底图纹理） |
| `loadBackgroundImage` | `int width, int height` | `BufferedImage`（protected） | 加载自定义背景图。优先从 `backgroundImageBase64` 加载，其次从 `backgroundImagePath` 加载（支持文件路径和 classpath 资源）。图片按双线性插值缩放到 `width x height`。加载失败返回 `null`（静默回退，不抛异常） |
| `applyWatermark` | `BufferedImage image` | `void`（protected） | 在图片上叠加文字水印和/或图片水印。根据 `CaptchaProperties` 的水印配置，先绘制文字水印（`watermarkText`），再绘制图片水印（`watermarkImageBase64`）。应在验证码内容绘制完成后调用 |
| `addNoise` | `Graphics2D g, int width, int height, int count, Random random` | `void`（protected） | 添加噪点 |
| `addInterfereLines` | `Graphics2D g, int width, int height, int count, Random random` | `void`（protected） | 添加干扰线 |
| `addArcInterference` | `Graphics2D g, int width, int height, int count, Random random` | `void`（protected） | 添加弧线干扰（二次贝塞尔曲线，比直线更难被 OCR 识别）。使用 `java.awt.geom.QuadCurve2D` 绘制 |
| `drawText` | `Graphics2D g, String text, int width, int height, Random random` | `void`（protected） | 绘制验证码文本（每字符独立颜色、字体大小与旋转角度）。使用当前 `properties` 中的字体配置（`fontFamily`、`fontStyle`、`minFontSize`、`maxFontSize`、`maxRotationDegree`） |
| `drawText` | `Graphics2D g, String text, int width, int height, Random random, CaptchaProperties props` | `void`（protected） | 绘制验证码文本（带自定义配置）。允许子类传入独立的 `CaptchaProperties` 控制字体名称、样式、大小范围和旋转角度 |
| `toBase64` | `BufferedImage image` | `String`（protected） | 将图片编码为带前缀的 base64 字符串：`data:image/png;base64,...` |
| `randomString` | `int length, Random random` | `String`（protected） | 从配置的字符集随机生成指定长度字符串。通过 `properties.getEffectiveChars()` 获取字符集（`charSet` 为空时使用默认字符集 `DEFAULT_CHARS`） |
| `getStore` | 无 | `CaptchaStore` | 获取存储 |
| `setStore` | `CaptchaStore store` | `void` | 设置存储 |
| `getProperties` | 无 | `CaptchaProperties` | 获取配置 |
| `setProperties` | `CaptchaProperties properties` | `void` | 设置配置 |

#### Token 结构
token 由 `generateToken` 生成，格式为：
```
base64(captchaKey) . base64(answer) . base64(expireTime)
```
- 三段以 `.` 拼接
- 每段为 UTF-8 字符串的 Base64 编码
- `expireTime` 为毫秒时间戳
- 注意：仅 Base64 编码，非加密安全

#### Usage Example
```java
// 子类只需实现 doGenerate 与 doVerify
public class MyCaptcha extends AbstractCaptcha {
    public MyCaptcha(CaptchaStore store, CaptchaProperties properties) {
        super(store, properties);
    }

    @Override
    public String getType() { return "my"; }

    @Override
    protected CaptchaResult doGenerate(CaptchaContext context) {
        String answer = randomString(context.getProperties().getLength(), new Random());
        context.setAnswer(answer);
        BufferedImage image = createImage(160, 50);
        Graphics2D g = image.createGraphics();
        try {
            drawText(g, answer, 160, 50, new Random());
        } finally {
            g.dispose();
        }
        CaptchaResult result = new CaptchaResult();
        result.setImageBase64(toBase64(image));
        return result;
    }

    @Override
    protected boolean doVerify(String answer, String userInput) {
        return answer.equalsIgnoreCase(userInput.trim());
    }
}
```

---

### CaptchaResult
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.captcha`
- **Description**: 验证码生成结果。封装一次验证码生成产生的全部信息：base64 图片、加密 token、过期时间以及额外数据。该对象会被返回给调用方用于 JSON 序列化下发到前端，因此答案本身不包含在此对象中（答案仅存于 `CaptchaStore`，避免泄露）。

#### Fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `captchaKey` | `String` | - | 验证码标识 |
| `type` | `String` | - | 验证码类型 |
| `imageBase64` | `String` | - | base64 编码的图片（带 `data:image/png;base64,` 前缀） |
| `token` | `String` | - | 加密 token（包含答案的加密信息，可用于无状态验证） |
| `expireTime` | `long` | - | 过期时间戳（毫秒） |
| `extra` | `Map<String, Object>` | `new HashMap<>()` | 额外数据（如滑动验证码的滑块图、缺口位置等） |

#### Constructors

| Constructor | Parameters | Description |
|-------------|-----------|-------------|
| `CaptchaResult` | 无 | 创建空结果（extra 初始化为空 HashMap） |
| `CaptchaResult` | `String captchaKey, String type, String imageBase64, String token, long expireTime, Map<String, Object> extra` | 全参构造（extra 为 null 时初始化为空 HashMap） |

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `getCaptchaKey` / `setCaptchaKey` | `String` | `String` / `void` | 验证码标识 getter/setter |
| `getType` / `setType` | `String` | `String` / `void` | 验证码类型 getter/setter |
| `getImageBase64` / `setImageBase64` | `String` | `String` / `void` | base64 图片 getter/setter |
| `getToken` / `setToken` | `String` | `String` / `void` | token getter/setter |
| `getExpireTime` / `setExpireTime` | `long` | `long` / `void` | 过期时间 getter/setter |
| `getExtra` / `setExtra` | `Map<String, Object>` | `Map` / `void` | 额外数据 getter/setter |
| `toMap` | 无 | `Map<String, Object>` | 将结果转为 Map，便于 JSON 序列化 |
| `toString` | 无 | `String` | 返回简要字符串描述（不含图片/token 完整内容） |

#### Usage Example
```java
CaptchaResult result = manager.generate("slider", "my-key");

// 获取各字段
String image = result.getImageBase64();    // "data:image/png;base64,..."
String token = result.getToken();           // "YXNk.Zmdo.cWVy"
String key = result.getCaptchaKey();        // "my-key"
String type = result.getType();             // "slider"
long expire = result.getExpireTime();       // 毫秒时间戳

// 获取额外数据（滑动验证码特有）
String sliderImg = (String) result.getExtra().get("sliderImage");
int gapY = (int) result.getExtra().get("gapY");
int blockSize = (int) result.getExtra().get("blockSize");

// 转 Map 便于 JSON 序列化
Map<String, Object> map = result.toMap();
```

---

### CaptchaStore
- **Type**: interface
- **Package**: `com.weacsoft.jaravel.vendor.captcha.store`
- **Description**: 验证码存储接口（SPI）。核心层通过此接口存取验证码答案，与具体存储介质解耦。默认实现 `MemoryCaptchaStore`（`ConcurrentHashMap` + TTL）。可适配为 CacheStore（jaravel cache 模块）、Redis 等。

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `put` | `String captchaKey, String answer, long ttlSeconds` | `void` | 存储验证码答案。`ttlSeconds` 为过期秒数 |
| `get` | `String captchaKey` | `String` | 读取验证码答案，不存在或已过期返回 `null` |
| `pull` | `String captchaKey` | `String` | 读取并删除（验证成功后一次性消费），不存在或已过期返回 `null` |
| `remove` | `String captchaKey` | `void` | 移除验证码 |

#### Usage Example
```java
CaptchaStore store = new MemoryCaptchaStore();
store.put("my-key", "AB23", 300);    // 存入答案，300 秒过期
String answer = store.get("my-key"); // "AB23"
String pulled = store.pull("my-key");// "AB23"（取出后即删除）
store.get("my-key");                  // null（已被 pull 删除）
```

---

### MemoryCaptchaStore
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.captcha.store`
- **Description**: 基于 `ConcurrentHashMap` 的内存验证码存储默认实现。线程安全，自带 TTL 过期机制：`get()` 与 `pull()` 读取时会检查过期，过期则自动删除并返回 `null`。另提供 `cleanup()` 主动清理全部过期项。适用于单机、低并发场景；分布式环境请改用基于 Redis / jaravel cache 模块的实现。
- **Implements**: `CaptchaStore`

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `MemoryCaptchaStore` | 无 | 构造方法 | 创建内存验证码存储 |
| `put` | `String captchaKey, String answer, long ttlSeconds` | `void` | 存储验证码答案，计算过期时间 |
| `get` | `String captchaKey` | `String` | 读取答案，若已过期则惰性删除并返回 `null` |
| `pull` | `String captchaKey` | `String` | 读取并删除答案，若已过期返回 `null` |
| `remove` | `String captchaKey` | `void` | 移除验证码 |
| `cleanup` | 无 | `int` | 主动清理所有已过期条目，返回实际清理的条目数 |
| `size` | 无 | `int` | 返回当前存储条目数（含可能已过期但未触发清理的） |

#### Nested Types
- **MemoryCaptchaStore.Entry** (class): 存储条目，记录 `answer` 与 `expireTime`（毫秒时间戳），提供 `isExpired()` 判断是否过期

#### Usage Example
```java
MemoryCaptchaStore store = new MemoryCaptchaStore();
store.put("key1", "AB23", 60);    // 60 秒过期
store.put("key2", "XY99", 300);   // 300 秒过期

String answer = store.get("key1"); // "AB23"
String pulled = store.pull("key2");// "XY99"（取出即删除）

// 主动清理过期条目
int removed = store.cleanup();
int total = store.size();
```

---

### CacheStoreCaptchaStore
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.captcha.store`
- **Description**: 基于 jaravel `CacheStore` 的验证码存储适配器。当项目引入了 jaravel cache 模块时，可通过此类将验证码答案存入 CacheStore（底层可以是 Redis、数据库等），实现跨进程验证码共享。未引入 cache 模块时使用 `MemoryCaptchaStore`。key 前缀默认为 `captcha:`，避免与其他业务缓存冲突。
- **Implements**: `CaptchaStore`

#### Constructors

| Constructor | Parameters | Description |
|-------------|-----------|-------------|
| `CacheStoreCaptchaStore` | `CacheStore cacheStore` | 创建适配器，传入 jaravel cache 模块的 `CacheStore` 实例 |

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `put` | `String captchaKey, String answer, long ttlSeconds` | `void` | 委托 `cacheStore.put("captcha:" + captchaKey, answer, ttlSeconds)` |
| `get` | `String captchaKey` | `String` | 委托 `cacheStore.get("captcha:" + captchaKey)`，值为 null 返回 null，否则 `toString()` |
| `pull` | `String captchaKey` | `String` | 委托 `cacheStore.pull("captcha:" + captchaKey)`，值为 null 返回 null，否则 `toString()` |
| `remove` | `String captchaKey` | `void` | 委托 `cacheStore.forget("captcha:" + captchaKey)` |

#### Usage Example
```java
import com.weacsoft.jaravel.vendor.cache.CacheStore;
import com.weacsoft.jaravel.vendor.cache.DefaultCacheStore;
import com.weacsoft.jaravel.vendor.cache.ArrayCacheDriver;

// 使用 jaravel cache 模块的 CacheStore 作为后端
CacheStore cacheStore = new DefaultCacheStore(new ArrayCacheDriver(), "myapp");
CaptchaStore captchaStore = new CacheStoreCaptchaStore(cacheStore);

captchaStore.put("my-key", "AB23", 300);  // 实际存入 "captcha:my-key"
String answer = captchaStore.get("my-key"); // "AB23"
captchaStore.remove("my-key");
```

---

### CaptchaProperties（核心层）
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.captcha`
- **Description**: 验证码配置属性（核心层）。核心层不依赖 Spring，因此使用普通 Java 类而非 `@ConfigurationProperties`。SpringBoot 兼容层可在外部继承或包装本类，通过 `jaravel.captcha` 前缀绑定配置后注入。

#### Properties

##### 基础配置

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `width` | `int` | `160` | 图片宽度（像素） |
| `height` | `int` | `50` | 图片高度（像素） |
| `length` | `int` | `4` | 验证码字符长度（适用于数字/算术验证码） |
| `expireSeconds` | `long` | `300` | 过期秒数，默认 5 分钟 |
| `caseSensitive` | `boolean` | `false` | 是否区分大小写（适用于数字验证码） |
| `tolerance` | `double` | `5.0` | 滑动/旋转验证的容差范围（滑动为像素，旋转为角度） |
| `interfereCount` | `int` | `30` | 干扰线数量 |
| `noiseCount` | `int` | `50` | 噪点数量 |

##### 视觉配置

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `fontFamily` | `String` | `"Arial"` | 字体名称（`null` 表示使用系统默认） |
| `fontStyle` | `int` | `1` | 字体样式：`Font.PLAIN=0`、`Font.BOLD=1`、`Font.ITALIC=2`、`Font.BOLD\|Font.ITALIC=3` |
| `minFontSize` | `int` | `0` | 最小字体大小（`0` 表示自动按图片高度计算，`height - 14`） |
| `maxFontSize` | `int` | `0` | 最大字体大小（`0` 表示自动按图片高度计算，`height - 6`） |
| `maxRotationDegree` | `int` | `30` | 每个字符最大旋转角度（0~90） |
| `charSet` | `String` | `null` | 自定义字符集（`null` 表示使用默认字符集 `ABCDEFGHJKMNPQRSTUVWXYZ23456789`） |
| `arcInterfere` | `boolean` | `true` | 是否添加弧线干扰（比直线更难被 OCR 识别） |
| `arcInterfereCount` | `int` | `5` | 弧线干扰数量 |

##### 轨迹验证配置

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `trajectoryEnabled` | `boolean` | `true` | 是否启用轨迹验证（滑动/旋转验证码） |
| `minTrajectoryPoints` | `int` | `5` | 轨迹最少点数（低于此值判定为机器行为） |
| `minTrajectoryDurationMs` | `long` | `500` | 轨迹最短持续时间（毫秒，低于此值判定为机器行为） |
| `maxTrajectoryDurationMs` | `long` | `30000` | 轨迹最长持续时间（毫秒，超过此值判定为可疑行为） |
| `maxJumpDistance` | `double` | `80` | 相邻轨迹点最大跳变距离（超过此值判定为非连续操作） |

##### 自定义背景图配置

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `backgroundImagePath` | `String` | `null` | 滑动/旋转验证码的自定义背景图路径（文件路径或 classpath 资源路径），`null` 表示不使用 |
| `backgroundImageBase64` | `String` | `null` | 自定义背景图的 base64 数据（支持带 `data:image/...;base64,` 前缀），优先级高于 `backgroundImagePath` |

##### 水印配置

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `watermarkText` | `String` | `null` | 文字水印内容（`null` 表示不添加文字水印） |
| `watermarkFontFamily` | `String` | `"Arial"` | 文字水印字体名称 |
| `watermarkFontSize` | `int` | `12` | 文字水印字体大小 |
| `watermarkColor` | `int` | `0x80666666` | 文字水印颜色（ARGB 格式，如 `0x80666666` 表示半透明灰色，`0x80` 为 alpha 通道） |
| `watermarkPosition` | `String` | `"bottom-right"` | 文字水印位置：`top-left`、`top-right`、`bottom-left`、`bottom-right`、`center` |
| `watermarkRotation` | `int` | `0` | 文字水印旋转角度（度，绕文字中心旋转） |
| `watermarkImageBase64` | `String` | `null` | 图片水印的 base64 数据（`null` 表示不添加图片水印，支持带 `data:image/...;base64,` 前缀） |
| `watermarkOpacity` | `float` | `0.3` | 图片水印透明度（0.0~1.0） |
| `watermarkScale` | `float` | `0.2` | 图片水印缩放比例（0.0~1.0，相对于画布宽度） |

#### Static Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `createDefault` | 无 | `CaptchaProperties` | 创建一份使用默认值的配置实例 |

#### Instance Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `getEffectiveChars` | 无 | `char[]` | 获取有效字符集。`charSet` 为空时返回默认字符集 `DEFAULT_CHARS`，否则返回 `charSet.toCharArray()` |
| 各属性 getter/setter | - | - | 标准 getter/setter（含上述全部基础、视觉、轨迹字段） |

#### Constants

| Constant | Type | Modifiers | Description |
|----------|------|-----------|-------------|
| `DEFAULT_CHARS` | `char[]` | `static final`（包级可见） | 默认字符集（排除易混淆字符 0/O/1/I/L）：`ABCDEFGHJKMNPQRSTUVWXYZ23456789` |

#### Usage Example
```java
// 使用默认配置
CaptchaProperties properties = CaptchaProperties.createDefault();

// 自定义基础配置
properties.setWidth(200);
properties.setHeight(60);
properties.setLength(5);
properties.setExpireSeconds(120);
properties.setCaseSensitive(true);
properties.setTolerance(8.0);
properties.setNoiseCount(80);
properties.setInterfereCount(40);

// 自定义视觉配置
properties.setFontFamily("Microsoft YaHei");
properties.setFontStyle(1);          // Font.BOLD
properties.setMinFontSize(28);
properties.setMaxFontSize(36);
properties.setMaxRotationDegree(25);
properties.setCharSet("0123456789"); // 仅数字
properties.setArcInterfere(true);
properties.setArcInterfereCount(6);

// 自定义轨迹验证配置
properties.setTrajectoryEnabled(true);
properties.setMinTrajectoryPoints(6);
properties.setMinTrajectoryDurationMs(600);
properties.setMaxTrajectoryDurationMs(20000);
properties.setMaxJumpDistance(60);

// 自定义背景图配置（滑动/旋转验证码）
properties.setBackgroundImagePath("captcha/backgrounds/scene1.jpg");
// properties.setBackgroundImageBase64("data:image/png;base64,...");  // 优先级更高

// 水印配置
properties.setWatermarkText("© MyCorp");          // 文字水印
properties.setWatermarkColor(0x80666666);          // ARGB 半透明灰色
properties.setWatermarkPosition("bottom-right");
properties.setWatermarkRotation(0);
// properties.setWatermarkImageBase64("data:image/png;base64,...");  // 图片水印
properties.setWatermarkOpacity(0.3f);
properties.setWatermarkScale(0.2f);

// 获取有效字符集（charSet 为空时返回 DEFAULT_CHARS）
char[] chars = properties.getEffectiveChars();

CaptchaManager manager = new CaptchaManager(new MemoryCaptchaStore(), properties);
```

---

### CaptchaManager
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.captcha`
- **Description**: 验证码管理器，统一管理多种验证码类型的注册与调用。内部维护 `type -> Captcha` 映射（`LinkedHashMap` 保持注册顺序），提供按类型生成/验证的入口，并通过 `createDefault()` 提供开箱即用的默认管理器（注册数字、算术、滑动、旋转四种）。核心层不依赖 Spring，可独立使用；SpringBoot 兼容层可将其包装为 Bean。

#### Constructors

| Constructor | Parameters | Description |
|-------------|-----------|-------------|
| `CaptchaManager` | 无 | 创建管理器（默认使用 `MemoryCaptchaStore` 与默认配置） |
| `CaptchaManager` | `CaptchaStore store, CaptchaProperties properties` | 指定存储与配置创建管理器 |

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `register` | `Captcha captcha` | `void` | 注册验证码实现（captcha 及其 type 不能为 null，否则抛 `IllegalArgumentException`） |
| `generate` | `String type, String captchaKey` | `CaptchaResult` | 生成指定类型的验证码，类型未注册时抛 `IllegalArgumentException` |
| `verify` | `String type, String captchaKey, String userInput` | `boolean` | 验证指定类型的验证码，类型未注册返回 `false` |
| `getTypes` | 无 | `Set<String>` | 返回所有已注册类型（不可修改） |
| `getCaptcha` | `String type` | `Captcha` | 按类型获取验证码实现，不存在返回 `null` |
| `getCaptchas` | 无 | `Map<String, Captcha>` | 返回全部已注册验证码的不可修改视图 |
| `getStore` / `setStore` | `CaptchaStore` | `CaptchaStore` / `void` | 存储 getter/setter |
| `getProperties` / `setProperties` | `CaptchaProperties` | `CaptchaProperties` / `void` | 配置 getter/setter |

#### Static Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `createDefault` | 无 | `CaptchaManager` | 创建默认管理器：使用内存存储与默认配置，注册数字、算术、滑动、旋转四种验证码（共享同一 store 与 properties） |

#### Usage Example
```java
// 开箱即用
CaptchaManager manager = CaptchaManager.createDefault();

// 查看已注册类型
Set<String> types = manager.getTypes();  // [number, arithmetic, slider, rotate]

// 生成与验证
CaptchaResult result = manager.generate("number", uuid);
boolean ok = manager.verify("number", uuid, "AB23");

// 注册自定义类型
manager.register(new MyCaptcha(manager.getStore(), manager.getProperties()));

// 自定义构造
CaptchaProperties props = CaptchaProperties.createDefault();
props.setWidth(200);
CaptchaManager custom = new CaptchaManager(new MemoryCaptchaStore(), props);
custom.register(new NumberCaptcha(custom.getStore(), custom.getProperties()));
```

---

### NumberCaptcha
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.captcha.generator`
- **Description**: 图片数字验证码：随机字母 + 数字字符串。类型名 `"number"`。在 `AbstractCaptcha` 提供的图像工具之上绘制带噪点、干扰线、随机旋转的字符图片。验证时按 `CaptchaProperties.isCaseSensitive()` 决定是否区分大小写。字符集排除易混淆字符 0/O/1/I/L。支持水印（`applyWatermark()` 在字符绘制完成后叠加，配置详见 `CaptchaProperties` 水印配置）。
- **Extends**: `AbstractCaptcha`
- **Type Name**: `"number"`

#### Constructors

| Constructor | Parameters | Description |
|-------------|-----------|-------------|
| `NumberCaptcha` | 无 | 默认构造：使用 `MemoryCaptchaStore` 与默认配置 |
| `NumberCaptcha` | `CaptchaStore store, CaptchaProperties properties` | 指定存储与配置构造 |

#### Behavior

| 项 | 说明 |
| --- | --- |
| 答案 | 从 `CaptchaProperties.getEffectiveChars()` 随机生成 `length` 位字符串（`charSet` 为空时使用默认字符集 `DEFAULT_CHARS`） |
| 用户输入 | 用户识别的字符 |
| 验证规则 | `caseSensitive=true` 时 `equals`，`false` 时 `equalsIgnoreCase` |
| 图片 | 带噪点 + 干扰线 + 随机旋转字符的 base64 PNG |

#### Usage Example
```java
NumberCaptcha captcha = new NumberCaptcha(store, properties);
CaptchaResult result = captcha.generate("my-key");
// result.getImageBase64() -> base64 图片
// result.getToken() -> 无状态 token

boolean ok = captcha.verify("my-key", "AB23");
```

---

### ArithmeticCaptcha
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.captcha.generator`
- **Description**: 算术验证码：随机生成 `a op b = ?` 形式的算式，答案为运算结果。类型名 `"arithmetic"`。支持加、减、乘三种运算，减法保证结果非负，乘法限制操作数在 1~9 之间避免结果过大。验证时按字符串相等比对（去除空格）。支持水印（`applyWatermark()` 在算式文本绘制完成后叠加，配置详见 `CaptchaProperties` 水印配置）。
- **Extends**: `AbstractCaptcha`
- **Type Name**: `"arithmetic"`

#### Constructors

| Constructor | Parameters | Description |
|-------------|-----------|-------------|
| `ArithmeticCaptcha` | 无 | 默认构造：使用 `MemoryCaptchaStore` 与默认配置 |
| `ArithmeticCaptcha` | `CaptchaStore store, CaptchaProperties properties` | 指定存储与配置构造 |

#### Behavior

| 项 | 说明 |
| --- | --- |
| 答案 | 运算结果（数字字符串） |
| 用户输入 | 计算结果 |
| 验证规则 | 去除空格后 `equals` |
| 图片 | 带噪点 + 干扰线 + 算式文本的 base64 PNG，内容如 `12 + 5 = ?` |
| 运算规则 | 加法：a+b（a,b 为 1~20）；减法：保证非负；乘法：a*b（a,b 为 1~9） |

#### Usage Example
```java
ArithmeticCaptcha captcha = new ArithmeticCaptcha(store, properties);
CaptchaResult result = captcha.generate("my-key");
// 图片显示如 "12 + 5 = ?"

boolean ok = captcha.verify("my-key", "17");
```

---

### TrajectoryValidator
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.captcha`
- **Description**: 轨迹验证器：检测用户拖动轨迹是否为人类行为。滑动和旋转验证码不仅校验最终位置/角度，还校验拖动过程的轨迹特征，以防范自动化脚本直接提交最终值。所有方法均为 `static`，不依赖第三方 JSON 库（手动解析）。被 `SliderCaptcha` 与 `RotateCaptcha` 的 `doVerify` 调用。

#### 验证维度

`validate()` 按以下顺序依次校验，任一维度不通过即返回 `false`：

| 维度 | 检查内容 | 判定规则 |
|------|----------|----------|
| 1. 点数检查 | 轨迹点数 | 必须 ≥ `minTrajectoryPoints`。机器行为往往点数过少 |
| 2. 时长检查 | 总拖动时长（`last.t - first.t`） | 必须在 `[minTrajectoryDurationMs, maxTrajectoryDurationMs]` 区间内。过快（<500ms）或过慢（>30s）均判定为可疑 |
| 3. 连续性检查 | 相邻两点的值差 | 不能超过 `maxJumpDistance`，排除瞬移式非连续操作 |
| 4. 非匀速检查 | 速度方差 | 人类拖动具有"加速→匀速→减速"特征，速度方差应大于阈值。匀速直线运动（方差 < 0.0001 且平均速度 > 0.01）判定为机器行为 |
| 5. 加速度方向多样性 | 加速度正负方向 | 人类拖动过程中加速度方向会变化（先正后负）。当总位移 > 20 时，要求同时存在正、负加速度，否则判定为可疑 |

#### Nested Types

| Type | Description |
|------|-------------|
| `TrajectoryValidator.Point` | 轨迹点：时间戳（毫秒）+ 值（滑动为 x 坐标，旋转为角度）。包含两个 `public final` 字段：`long t`（时间戳）、`double v`（值），以及构造方法 `Point(long t, double v)` |

#### Static Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `validate` | `List<Point> points, CaptchaProperties props` | `boolean` | 验证轨迹。`points` 为 `null` 或空时：若未启用轨迹验证返回 `true`，否则返回 `false`。启用轨迹验证时依次执行上述五个维度的检查 |
| `parsePoints` | `String json` | `List<Point>` | 从简单 JSON 字符串解析轨迹点。输入格式：`[{"t":0,"v":0},{"t":50,"v":5},...]`。不依赖第三方 JSON 库，手动解析 `{"t":xxx,"v":yyy}` 模式。解析失败返回空列表 |
| `extractValue` | `String userInput` | `double` | 从用户输入中提取最终值（`value` 字段）。若输入包含 `"value"` 字段则从 JSON 中提取该字段数值；否则尝试直接解析为数字（兼容旧格式纯数字输入）。解析失败返回 `Double.NaN` |
| `extractTrajectory` | `String userInput` | `List<Point>` | 从用户输入 JSON 中提取轨迹点。查找 `"trajectory"` 字段对应的数组部分并调用 `parsePoints()` 解析。无 `"trajectory"` 字段时返回空列表（兼容旧格式） |

#### 用户输入格式

启用轨迹验证时，前端需提交 JSON：

```json
{"value": 123, "trajectory": [{"t": 0, "v": 0}, {"t": 50, "v": 5}, ...]}
```

- `value`：最终值（滑动为 x 坐标，旋转为角度）
- `trajectory`：拖动过程中采集的 `{时间戳ms, 值}` 点序列
- 未启用轨迹验证时，用户输入可直接为数字字符串 `"123"`（向后兼容），`extractValue()` 会自动识别

#### Usage Example
```java
import com.weacsoft.jaravel.vendor.captcha.TrajectoryValidator;
import com.weacsoft.jaravel.vendor.captcha.CaptchaProperties;
import java.util.List;

CaptchaProperties props = CaptchaProperties.createDefault();
props.setTrajectoryEnabled(true);
props.setMinTrajectoryPoints(5);

// 1. 从用户输入 JSON 提取最终值
String userInput = "{\"value\":123,\"trajectory\":[{\"t\":0,\"v\":0},{\"t\":50,\"v\":5}]}";
double value = TrajectoryValidator.extractValue(userInput);  // 123.0

// 2. 从用户输入 JSON 提取轨迹点
List<TrajectoryValidator.Point> points = TrajectoryValidator.extractTrajectory(userInput);

// 3. 验证轨迹是否为人类行为
boolean ok = TrajectoryValidator.validate(points, props);

// 4. 独立解析轨迹 JSON 数组
List<TrajectoryValidator.Point> parsed = TrajectoryValidator.parsePoints(
        "[{\"t\":0,\"v\":0},{\"t\":50,\"v\":5}]");

// 5. 兼容旧格式：纯数字输入
double legacyValue = TrajectoryValidator.extractValue("123");  // 123.0
List<TrajectoryValidator.Point> legacyTraj = TrajectoryValidator.extractTrajectory("123");  // 空列表
```

---

### SliderCaptcha
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.captcha.generator`
- **Description**: 滑动验证码：在背景图上随机位置抠出缺口，前端拖动滑块拼回缺口。类型名 `"slider"`。答案为缺口横坐标 `gapX`。`doVerify` 通过 `TrajectoryValidator.extractValue()` 提取用户提交的最终值进行位置校验（`CaptchaProperties.getTolerance()` 像素容差）；若 `CaptchaProperties.isTrajectoryEnabled()` 为 `true`，还会通过 `TrajectoryValidator.validate()` 校验拖动轨迹的人类行为特征（点数、时长、连续性、非匀速、加速度多样性）。支持 JSON 格式输入（含 `value` 与 `trajectory`），未启用轨迹验证时也兼容纯数字字符串输入。支持自定义背景图（通过 `CaptchaProperties.backgroundImagePath` / `backgroundImageBase64` 配置，优先使用自定义图，未配置或加载失败时回退到 `drawRandomBackground()` 随机渐变背景）与水印（`applyWatermark()` 在缺口绘制后叠加）。
- **Extends**: `AbstractCaptcha`
- **Type Name**: `"slider"`

#### Constructors

| Constructor | Parameters | Description |
|-------------|-----------|-------------|
| `SliderCaptcha` | 无 | 默认构造：使用 `MemoryCaptchaStore` 与默认配置 |
| `SliderCaptcha` | `CaptchaStore store, CaptchaProperties properties` | 指定存储与配置构造 |

#### doVerify 输入格式

`doVerify(String answer, String userInput)` 的 `userInput` 支持两种格式：

| 格式 | 适用场景 | 示例 |
|------|----------|------|
| JSON 字符串 | 启用轨迹验证时（`trajectoryEnabled=true`） | `{"value": 123, "trajectory": [{"t":0,"v":0},{"t":50,"v":5},...]}` |
| 纯数字字符串 | 未启用轨迹验证时（向后兼容） | `"123"` |

`TrajectoryValidator.extractValue()` 会自动识别输入格式：包含 `"value"` 字段则从 JSON 提取，否则直接解析为数字。

#### Behavior

| 项 | 说明 |
| --- | --- |
| 答案 | 缺口横坐标 `gapX`（像素，字符串） |
| 用户输入 | 滑块拖动的 x 坐标。启用轨迹验证时需提交 JSON（含 `value` 与 `trajectory`） |
| 验证规则 | 1. 位置校验：`Math.abs(gapX - value) <= tolerance`；2. 轨迹校验（`trajectoryEnabled=true` 时）：`TrajectoryValidator.validate(points, properties)`。任一校验失败返回 `false`，解析失败返回 `false` |
| `imageBase64` | 带缺口的背景图（base64 PNG）。背景图来源：优先使用 `loadBackgroundImage()` 加载自定义背景图（`backgroundImageBase64` > `backgroundImagePath`），未配置或加载失败时使用 `drawRandomBackground()` 随机渐变背景 |
| `extra.sliderImage` | 滑块拼图小块（带透明通道的 PNG base64） |
| `extra.gapY` | 缺口纵坐标（前端固定滑块纵坐标用） |
| `extra.blockSize` | 滑块边长（`Math.max(20, height / 2)`） |
| `extra.trajectoryEnabled` | 是否启用了轨迹验证（`boolean`，前端可据此决定提交格式） |

#### Usage Example
```java
SliderCaptcha captcha = new SliderCaptcha(store, properties);
CaptchaResult result = captcha.generate("my-key");

String bgImage = result.getImageBase64();                  // 带缺口的背景图
String sliderImg = (String) result.getExtra().get("sliderImage"); // 滑块小块
int gapY = (int) result.getExtra().get("gapY");            // 缺口纵坐标
int blockSize = (int) result.getExtra().get("blockSize");  // 滑块边长
boolean trajEnabled = (boolean) result.getExtra().get("trajectoryEnabled"); // 是否启用轨迹验证

// 启用轨迹验证时：前端拖动后提交 JSON
boolean ok = captcha.verify("my-key",
        "{\"value\":123,\"trajectory\":[{\"t\":0,\"v\":0},{\"t\":50,\"v\":5}]}");

// 未启用轨迹验证时：可直接提交数字字符串（向后兼容）
boolean ok2 = captcha.verify("my-key", "123");
```

---

### RotateCaptcha
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.captcha.generator`
- **Description**: 旋转验证码：将一张带明显朝向（向上箭头）的图片随机旋转，前端拖动将其转回正方向。类型名 `"rotate"`。答案为旋转角度（0~359）。`doVerify` 通过 `TrajectoryValidator.extractValue()` 提取用户提交的最终角度进行角度校验（`CaptchaProperties.getTolerance()` 角度容差，双向最短角差）；若 `CaptchaProperties.isTrajectoryEnabled()` 为 `true`，还会通过 `TrajectoryValidator.validate()` 校验旋转轨迹的人类行为特征（点数、时长、连续性、非匀速、加速度多样性）。支持 JSON 格式输入（含 `value` 与 `trajectory`），未启用轨迹验证时也兼容纯数字字符串输入。使用正方形画布（`Math.max(width, 200)`）。支持自定义背景图（通过 `CaptchaProperties.backgroundImagePath` / `backgroundImageBase64` 配置，优先使用自定义图作为底图并叠加朝向标记，未配置或加载失败时使用 `drawRandomBackground()` 随机渐变背景）与水印（`applyWatermark()` 在旋转后的展示图上叠加）。
- **Extends**: `AbstractCaptcha`
- **Type Name**: `"rotate"`

#### Constructors

| Constructor | Parameters | Description |
|-------------|-----------|-------------|
| `RotateCaptcha` | 无 | 默认构造：使用 `MemoryCaptchaStore` 与默认配置 |
| `RotateCaptcha` | `CaptchaStore store, CaptchaProperties properties` | 指定存储与配置构造 |

#### doVerify 输入格式

`doVerify(String answer, String userInput)` 的 `userInput` 支持两种格式：

| 格式 | 适用场景 | 示例 |
|------|----------|------|
| JSON 字符串 | 启用轨迹验证时（`trajectoryEnabled=true`） | `{"value": 90, "trajectory": [{"t":0,"v":0},{"t":50,"v":3},...]}` |
| 纯数字字符串 | 未启用轨迹验证时（向后兼容） | `"90"` |

`TrajectoryValidator.extractValue()` 会自动识别输入格式：包含 `"value"` 字段则从 JSON 提取，否则直接解析为数字。

#### Behavior

| 项 | 说明 |
| --- | --- |
| 答案 | 旋转角度（0~359，字符串） |
| 用户输入 | 用户旋转的角度。启用轨迹验证时需提交 JSON（含 `value` 与 `trajectory`） |
| 验证规则 | 1. 角度校验：双向最短角差 `diff <= tolerance`；2. 轨迹校验（`trajectoryEnabled=true` 时）：`TrajectoryValidator.validate(points, properties)`。任一校验失败返回 `false`，解析失败返回 `false` |
| `imageBase64` | 旋转后的图片（正方形 base64 PNG）。底图来源：优先使用 `loadBackgroundImage()` 加载自定义背景图（`backgroundImageBase64` > `backgroundImagePath`）并叠加朝向标记，未配置或加载失败时使用 `drawRandomBackground()` 随机渐变背景 + 朝向标记 |
| `extra.size` | 图片边长（`Math.max(width, 200)`） |
| `extra.trajectoryEnabled` | 是否启用了轨迹验证（`boolean`，前端可据此决定提交格式） |
| 朝向标记 | 顶部居中向上箭头（白色杆 + 蓝色三角头部），便于用户判断正方向 |

#### Usage Example
```java
RotateCaptcha captcha = new RotateCaptcha(store, properties);
CaptchaResult result = captcha.generate("my-key");

String image = result.getImageBase64();          // 旋转后的图片
int size = (int) result.getExtra().get("size");  // 图片边长
boolean trajEnabled = (boolean) result.getExtra().get("trajectoryEnabled"); // 是否启用轨迹验证

// 启用轨迹验证时：前端拖动转回正方向后提交 JSON
boolean ok = captcha.verify("my-key",
        "{\"value\":90,\"trajectory\":[{\"t\":0,\"v\":0},{\"t\":50,\"v\":3}]}");

// 未启用轨迹验证时：可直接提交数字字符串（向后兼容）
boolean ok2 = captcha.verify("my-key", "90");
```

---

### CaptchaAutoConfiguration
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.captcha.springboot`
- **Description**: 验证码 SpringBoot 自动装配。当 `jaravel.captcha.enabled=true`（默认）时自动创建 `CaptchaManager` Bean，注册四种验证码类型（数字、算术、滑动、旋转）。根据是否引入 jaravel cache 模块智能选择存储：有 cache 模块使用 `CacheStoreCaptchaStore`（跨进程），无则使用 `MemoryCaptchaStore`（内存）。
- **Annotations**: `@AutoConfiguration`, `@ConditionalOnClass(CaptchaManager.class)`, `@ConditionalOnProperty(prefix = "jaravel.captcha", name = "enabled", havingValue = "true", matchIfMissing = true)`, `@EnableConfigurationProperties(CaptchaProperties.class)`

#### Inner Configurations

| 内部配置类 | 条件 | 说明 |
| --- | --- | --- |
| `MemoryStoreConfig` | `@ConditionalOnMissingClass("com.weacsoft.jaravel.vendor.cache.CacheStore")` | 无 cache 模块时注册 `MemoryCaptchaStore` Bean |
| `CacheStoreConfig` | `@ConditionalOnClass(name = "com.weacsoft.jaravel.vendor.cache.CacheStore")` | 有 cache 模块时注册 `CacheStoreCaptchaStore` Bean（容器中无 `CacheStore` Bean 时回退到 `MemoryCaptchaStore`） |

#### Bean Methods

| Bean | Parameters | Return | Description |
|------|-----------|--------|-------------|
| `captchaStore`（MemoryStoreConfig） | 无 | `CaptchaStore` | 内存存储（`@Bean`, `@ConditionalOnMissingBean`） |
| `captchaStore`（CacheStoreConfig） | `ObjectProvider<CacheStore> cacheStoreProvider` | `CaptchaStore` | CacheStore 适配存储或回退内存（`@Bean`, `@ConditionalOnMissingBean`） |
| `captchaManager` | `CaptchaProperties properties, ObjectProvider<CaptchaStore> captchaStoreProvider` | `CaptchaManager` | 验证码管理器，注册四种验证码类型（`@Bean`, `@ConditionalOnMissingBean`）；存储为 null 时回退 `MemoryCaptchaStore`；通过 `properties.toCoreProperties()` 转换为核心层配置 |

#### Usage Example
```java
// 引入依赖后自动装配，直接注入使用
@Autowired
private CaptchaManager captchaManager;

@GetMapping("/captcha")
public CaptchaResult captcha(@RequestParam(defaultValue = "number") String type) {
    String key = UUID.randomUUID().toString();
    return captchaManager.generate(type, key);
}
```

```yaml
# application.yml
jaravel:
  captcha:
    enabled: true
    width: 160
    height: 50
    expire-seconds: 300
```

---

### CaptchaProperties（SpringBoot 适配层）
- **Type**: class
- **Package**: `com.weacsoft.jaravel.vendor.captcha.springboot`
- **Description**: SpringBoot 配置属性，前缀 `jaravel.captcha`。在 `application.yml` 中通过 `jaravel.captcha.*` 配置。通过 `toCoreProperties()` 方法转为核心层 `CaptchaProperties` 对象供 `CaptchaManager` 使用。
- **Annotations**: `@ConfigurationProperties(prefix = "jaravel.captcha")`

#### Properties

##### 基础配置

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | `boolean` | `true` | 是否启用自动装配 |
| `width` | `int` | `160` | 图片宽度（像素） |
| `height` | `int` | `50` | 图片高度（像素） |
| `length` | `int` | `4` | 验证码字符长度（数字/字母验证码） |
| `expireSeconds` | `long` | `300` | 过期时间（秒） |
| `caseSensitive` | `boolean` | `false` | 是否区分大小写 |
| `tolerance` | `double` | `5.0` | 滑动/旋转验证的容差范围 |
| `noise` | `int` | `50` | 噪点数量（对应核心层 `noiseCount`） |
| `interfereLines` | `int` | `30` | 干扰线数量（对应核心层 `interfereCount`） |

##### 视觉配置

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `fontFamily` | `String` | `"Arial"` | 字体名称（`null` 表示使用系统默认） |
| `fontStyle` | `int` | `1` | 字体样式：`Font.PLAIN=0`、`Font.BOLD=1`、`Font.ITALIC=2`、`Font.BOLD\|Font.ITALIC=3` |
| `minFontSize` | `int` | `0` | 最小字体大小（`0` 表示自动按图片高度计算） |
| `maxFontSize` | `int` | `0` | 最大字体大小（`0` 表示自动按图片高度计算） |
| `maxRotationDegree` | `int` | `30` | 每个字符最大旋转角度（0~90） |
| `charSet` | `String` | `null` | 自定义字符集（`null` 表示使用默认字符集） |
| `arcInterfere` | `boolean` | `true` | 是否添加弧线干扰（比直线更难被 OCR 识别） |
| `arcInterfereCount` | `int` | `5` | 弧线干扰数量 |

##### 轨迹验证配置

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `trajectoryEnabled` | `boolean` | `true` | 是否启用轨迹验证（滑动/旋转验证码） |
| `minTrajectoryPoints` | `int` | `5` | 轨迹最少点数（低于此值判定为机器行为） |
| `minTrajectoryDurationMs` | `long` | `500` | 轨迹最短持续时间（毫秒） |
| `maxTrajectoryDurationMs` | `long` | `30000` | 轨迹最长持续时间（毫秒） |
| `maxJumpDistance` | `double` | `80` | 相邻轨迹点最大跳变距离 |

##### 自定义背景图配置

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `backgroundImagePath` | `String` | `null` | 滑动/旋转验证码的自定义背景图路径（文件路径或 classpath 资源路径），`null` 表示不使用 |
| `backgroundImageBase64` | `String` | `null` | 自定义背景图的 base64 数据（优先级高于 `backgroundImagePath`） |

##### 水印配置

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `watermarkText` | `String` | `null` | 文字水印内容（`null` 表示不添加文字水印） |
| `watermarkFontFamily` | `String` | `"Arial"` | 文字水印字体名称 |
| `watermarkFontSize` | `int` | `12` | 文字水印字体大小 |
| `watermarkColor` | `int` | `0x80666666` | 文字水印颜色（ARGB 格式） |
| `watermarkPosition` | `String` | `"bottom-right"` | 文字水印位置（`top-left`/`top-right`/`bottom-left`/`bottom-right`/`center`） |
| `watermarkRotation` | `int` | `0` | 文字水印旋转角度（度） |
| `watermarkImageBase64` | `String` | `null` | 图片水印的 base64 数据（`null` 表示不添加图片水印） |
| `watermarkOpacity` | `float` | `0.3` | 图片水印透明度（0.0~1.0） |
| `watermarkScale` | `float` | `0.2` | 图片水印缩放比例（0.0~1.0，相对于画布宽度） |

#### Methods

| Method | Parameters | Return | Description |
|--------|-----------|--------|-------------|
| `toCoreProperties` | 无 | `com.weacsoft.jaravel.vendor.captcha.CaptchaProperties` | 转为核心层配置对象（映射：`noise` -> `noiseCount`，`interfereLines` -> `interfereCount`；其余视觉与轨迹字段同名直传） |
| 各属性 getter/setter | - | - | 标准 getter/setter（含上述全部基础、视觉、轨迹字段） |

#### Field映射（SpringBoot -> 核心层）

| SpringBoot 字段 | 核心层字段 | 说明 |
| --- | --- | --- |
| `width` | `width` | 同名直传 |
| `height` | `height` | 同名直传 |
| `length` | `length` | 同名直传 |
| `expireSeconds` | `expireSeconds` | 同名直传 |
| `caseSensitive` | `caseSensitive` | 同名直传 |
| `tolerance` | `tolerance` | 同名直传 |
| `noise` | `noiseCount` | 名称映射 |
| `interfereLines` | `interfereCount` | 名称映射 |
| `fontFamily` | `fontFamily` | 同名直传 |
| `fontStyle` | `fontStyle` | 同名直传 |
| `minFontSize` | `minFontSize` | 同名直传 |
| `maxFontSize` | `maxFontSize` | 同名直传 |
| `maxRotationDegree` | `maxRotationDegree` | 同名直传 |
| `charSet` | `charSet` | 同名直传 |
| `arcInterfere` | `arcInterfere` | 同名直传 |
| `arcInterfereCount` | `arcInterfereCount` | 同名直传 |
| `trajectoryEnabled` | `trajectoryEnabled` | 同名直传 |
| `minTrajectoryPoints` | `minTrajectoryPoints` | 同名直传 |
| `minTrajectoryDurationMs` | `minTrajectoryDurationMs` | 同名直传 |
| `maxTrajectoryDurationMs` | `maxTrajectoryDurationMs` | 同名直传 |
| `maxJumpDistance` | `maxJumpDistance` | 同名直传 |
| `backgroundImagePath` | `backgroundImagePath` | 同名直传 |
| `backgroundImageBase64` | `backgroundImageBase64` | 同名直传 |
| `watermarkText` | `watermarkText` | 同名直传 |
| `watermarkFontFamily` | `watermarkFontFamily` | 同名直传 |
| `watermarkFontSize` | `watermarkFontSize` | 同名直传 |
| `watermarkColor` | `watermarkColor` | 同名直传 |
| `watermarkPosition` | `watermarkPosition` | 同名直传 |
| `watermarkRotation` | `watermarkRotation` | 同名直传 |
| `watermarkImageBase64` | `watermarkImageBase64` | 同名直传 |
| `watermarkOpacity` | `watermarkOpacity` | 同名直传 |
| `watermarkScale` | `watermarkScale` | 同名直传 |

#### Usage Example
```yaml
# application.yml
jaravel:
  captcha:
    enabled: true
    width: 200
    height: 60
    length: 5
    expire-seconds: 120
    case-sensitive: true
    tolerance: 8.0
    noise: 80
    interfere-lines: 40
    # 视觉配置
    font-family: "Microsoft YaHei"
    font-style: 1
    min-font-size: 28
    max-font-size: 36
    max-rotation-degree: 25
    char-set: "0123456789"
    arc-interfere: true
    arc-interfere-count: 6
    # 轨迹验证配置
    trajectory-enabled: true
    min-trajectory-points: 6
    min-trajectory-duration-ms: 600
    max-trajectory-duration-ms: 20000
    max-jump-distance: 60
    # 自定义背景图配置（滑动/旋转验证码）
    background-image-path: "captcha/backgrounds/scene1.jpg"
    # background-image-base64: "data:image/png;base64,..."
    # 水印配置
    watermark-text: "© MyCorp"
    watermark-font-family: Arial
    watermark-font-size: 12
    watermark-color: 0x80666666
    watermark-position: bottom-right
    watermark-rotation: 0
    # watermark-image-base64: "data:image/png;base64,..."
    watermark-opacity: 0.3
    watermark-scale: 0.2
```

```java
// 在自定义配置中获取转换后的核心配置
@Autowired
private CaptchaProperties springBootProps;

CaptchaProperties coreProps = springBootProps.toCoreProperties();
// coreProps.getNoiseCount() == 80
// coreProps.getInterfereCount() == 40
// coreProps.getFontFamily() == "Microsoft YaHei"
// coreProps.getTrajectoryEnabled() == true
// coreProps.getBackgroundImagePath() == "captcha/backgrounds/scene1.jpg"
// coreProps.getWatermarkText() == "© MyCorp"
// coreProps.getWatermarkPosition() == "bottom-right"
```
