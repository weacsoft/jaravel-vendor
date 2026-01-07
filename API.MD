# Jaravel API 文档

## 1. request 模块

### 1.1 Request 类
**路径**: `com.weacsoft.jaravel.http.request.Request`

**方法**:
- `String get(String key)` - 获取请求参数，返回字符串值
- `String get(String key, String defaultValue)` - 获取请求参数，带默认值
- `Integer getInt(String key)` - 获取整型参数
- `Integer getInt(String key, Integer defaultValue)` - 获取整型参数，带默认值
- `Long getLong(String key)` - 获取长整型参数
- `Long getLong(String key, Long defaultValue)` - 获取长整型参数，带默认值
- `Boolean getBoolean(String key)` - 获取布尔型参数
- `Boolean getBoolean(String key, Boolean defaultValue)` - 获取布尔型参数，带默认值
- `Map<String, Object> all()` - 获取所有请求参数
- `Map<String, Object> only(String... keys)` - 获取指定的请求参数
- `Map<String, Object> except(String... keys)` - 获取除指定外的所有参数
- `boolean has(String key)` - 检查参数是否存在
- `String input(String key)` - 输入参数别名方法
- `String input(String key, String defaultValue)` - 输入参数别名方法，带默认值
- `jakarta.servlet.http.HttpServletRequest getRequest()` - 获取原始 HttpServletRequest
- `jakarta.servlet.http.Cookie[] getCookies()` - 获取所有 Cookie
- `String getCookie(String name)` - 获取指定名称的 Cookie 值
- `void addCookie(String name, String value)` - 添加 Cookie
- `void addCookie(jakarta.servlet.http.Cookie cookie)` - 添加 Cookie 对象
- `void removeCookie(String name)` - 移除 Cookie
- `Map<String, Object> getSession()` - 获取会话属性
- `Object getSession(String key)` - 获取会话属性值
- `void putSession(String key, Object value)` - 设置会话属性
- `void removeSession(String key)` - 移除会话属性
- `String getIp()` - 获取客户端 IP
- `String getUserAgent()` - 获取用户代理
- `String getMethod()` - 获取请求方法
- `String getPathInfo()` - 获取路径信息
- `String getRequestURI()` - 获取请求 URI
- `String getQueryString()` - 获取查询字符串
- `String getScheme()` - 获取请求协议
- `String getServerName()` - 获取服务器名称
- `int getServerPort()` - 获取服务器端口
- `String getHeader(String name)` - 获取请求头
- `java.util.Enumeration<String> getHeaders(String name)` - 获取多个同名请求头
- `java.util.Enumeration<String> getHeaderNames()` - 获取所有请求头名称
- `boolean isAjax()` - 检查是否为 AJAX 请求
- `boolean isSecure()` - 检查是否为安全连接
- `String url()` - 获取完整请求 URL
- `String fullUrl()` - 获取完整请求 URL（包含查询参数）
- `String path()` - 获取请求路径
- `String root()` - 获取应用根路径
- `String route()` - 获取路由路径
- `void flash(String key, Object value)` - 闪存数据到会话
- `Object old(String key)` - 获取旧闪存数据
- `Object old(String key, Object defaultValue)` - 获取旧闪存数据，带默认值
- `void flush()` - 刷新会话闪存数据
- `java.util.List<jakarta.servlet.http.Cookie> getNewCookies()` - 获取新添加的 Cookie

### 1.2 RequestFactory 类
**路径**: `com.weacsoft.jaravel.http.request.RequestFactory`

**方法**:
- `static Request create(jakarta.servlet.http.HttpServletRequest request)` - 创建 Request 实例

## 2. response 模块

### 2.1 Response 接口
**路径**: `com.weacsoft.jaravel.http.response.Response`

**方法**:
- `int getStatus()` - 获取响应状态码
- `void setStatus(int status)` - 设置响应状态码
- `String getContent()` - 获取响应内容
- `void setContent(String content)` - 设置响应内容
- `String getContentType()` - 获取内容类型
- `void setContentType(String contentType)` - 设置内容类型
- `com.weacsoft.jaravel.http.response.Response status(int status)` - 设置响应状态码，链式调用
- `com.weacsoft.jaravel.http.response.Response header(String name, String value)` - 设置响应头，链式调用
- `com.weacsoft.jaravel.http.response.Response content(String content)` - 设置响应内容，链式调用
- `com.weacsoft.jaravel.http.response.Response type(String contentType)` - 设置内容类型，链式调用
- `void addHeader(String name, String value)` - 添加响应头
- `java.util.Map<String, String> getHeaders()` - 获取所有响应头
- `void addCookie(jakarta.servlet.http.Cookie cookie)` - 添加 Cookie
- `void addCookie(String name, String value)` - 添加 Cookie
- `jakarta.servlet.http.Cookie[] getCookies()` - 获取所有 Cookie

### 2.2 ResponseBuilder 类
**路径**: `com.weacsoft.jaravel.http.response.ResponseBuilder`

**方法**:
- `static com.weacsoft.jaravel.http.response.Response ok(String content)` - 创建 200 响应
- `static com.weacsoft.jaravel.http.response.Response created(String content)` - 创建 201 响应
- `static com.weacsoft.jaravel.http.response.Response accepted(String content)` - 创建 202 响应
- `static com.weacsoft.jaravel.http.response.Response noContent()` - 创建 204 响应
- `static com.weacsoft.jaravel.http.response.Response movedPermanently(String location)` - 创建 301 重定向
- `static com.weacsoft.jaravel.http.response.Response found(String location)` - 创建 302 重定向
- `static com.weacsoft.jaravel.http.response.Response seeOther(String location)` - 创建 303 重定向
- `static com.weacsoft.jaravel.http.response.Response temporaryRedirect(String location)` - 创建 307 重定向
- `static com.weacsoft.jaravel.http.response.Response permanentRedirect(String location)` - 创建 308 重定向
- `static com.weacsoft.jaravel.http.response.Response badRequest(String content)` - 创建 400 响应
- `static com.weacsoft.jaravel.http.response.Response unauthorized(String content)` - 创建 401 响应
- `static com.weacsoft.jaravel.http.response.Response forbidden(String content)` - 创建 403 响应
- `static com.weacsoft.jaravel.http.response.Response notFound(String content)` - 创建 404 响应
- `static com.weacsoft.jaravel.http.response.Response methodNotAllowed(String content)` - 创建 405 响应
- `static com.weacsoft.jaravel.http.response.Response internalServerError(String content)` - 创建 500 响应
- `static com.weacsoft.jaravel.http.response.Response json(Object data)` - 创建 JSON 响应
- `static com.weacsoft.jaravel.http.response.Response json(int status, Object data)` - 创建指定状态的 JSON 响应
- `static com.weacsoft.jaravel.http.response.Response view(String view, java.util.Map<String, Object> data)` - 创建视图响应
- `static com.weacsoft.jaravel.http.response.Response content(String content, String contentType)` - 创建指定内容类型的响应
- `static com.weacsoft.jaravel.http.response.Response file(String filePath)` - 创建文件下载响应
- `static com.weacsoft.jaravel.http.response.Response file(String filePath, String fileName)` - 创建带文件名的文件下载响应

### 2.3 JSONResponseResolver 类
**路径**: `com.weacsoft.jaravel.http.response.JSONResponseResolver`

**方法**:
- `static String resolve(Object data)` - 解析对象为 JSON 字符串

## 3. route 模块

### 3.1 Route 类
**路径**: `com.weacsoft.jaravel.route.Route`

**方法**:
- `String getUri()` - 获取路由 URI
- `String getMethod()` - 获取路由方法
- `com.weacsoft.jaravel.route.RouteHandler getHandler()` - 获取路由处理器
- `java.util.List<com.weacsoft.jaravel.middleware.Middleware> getMiddleware()` - 获取路由中间件
- `com.weacsoft.jaravel.route.Route middleware(com.weacsoft.jaravel.middleware.Middleware... middleware)` - 添加中间件

### 3.2 Router 类
**路径**: `com.weacsoft.jaravel.route.Router`

**方法**:
- `void get(String uri, com.weacsoft.jaravel.route.RouteHandler handler)` - 注册 GET 路由
- `void post(String uri, com.weacsoft.jaravel.route.RouteHandler handler)` - 注册 POST 路由
- `void put(String uri, com.weacsoft.jaravel.route.RouteHandler handler)` - 注册 PUT 路由
- `void patch(String uri, com.weacsoft.jaravel.route.RouteHandler handler)` - 注册 PATCH 路由
- `void delete(String uri, com.weacsoft.jaravel.route.RouteHandler handler)` - 注册 DELETE 路由
- `void options(String uri, com.weacsoft.jaravel.route.RouteHandler handler)` - 注册 OPTIONS 路由
- `void any(String uri, com.weacsoft.jaravel.route.RouteHandler handler)` - 注册任意方法路由
- `void match(java.util.List<String> methods, String uri, com.weacsoft.jaravel.route.RouteHandler handler)` - 注册指定方法路由
- `com.weacsoft.jaravel.route.Route find(String uri, String method)` - 查找匹配的路由
- `java.util.List<com.weacsoft.jaravel.route.Route> getRoutes()` - 获取所有路由

### 3.3 RouteService 类
**路径**: `com.weacsoft.jaravel.route.RouteService`

**方法**:
- `com.weacsoft.jaravel.route.Route get(String uri, com.weacsoft.jaravel.route.RouteHandler handler)` - 注册 GET 路由
- `com.weacsoft.jaravel.route.Route post(String uri, com.weacsoft.jaravel.route.RouteHandler handler)` - 注册 POST 路由
- `com.weacsoft.jaravel.route.Route put(String uri, com.weacsoft.jaravel.route.RouteHandler handler)` - 注册 PUT 路由
- `com.weacsoft.jaravel.route.Route patch(String uri, com.weacsoft.jaravel.route.RouteHandler handler)` - 注册 PATCH 路由
- `com.weacsoft.jaravel.route.Route delete(String uri, com.weacsoft.jaravel.route.RouteHandler handler)` - 注册 DELETE 路由
- `com.weacsoft.jaravel.route.Route options(String uri, com.weacsoft.jaravel.route.RouteHandler handler)` - 注册 OPTIONS 路由
- `com.weacsoft.jaravel.route.Route any(String uri, com.weacsoft.jaravel.route.RouteHandler handler)` - 注册任意方法路由
- `com.weacsoft.jaravel.route.Route match(java.util.List<String> methods, String uri, com.weacsoft.jaravel.route.RouteHandler handler)` - 注册指定方法路由
- `void middleware(com.weacsoft.jaravel.middleware.Middleware... middleware)` - 全局中间件
- `com.weacsoft.jaravel.http.response.Response dispatch(com.weacsoft.jaravel.http.request.Request request, com.weacsoft.jaravel.http.response.Response response)` - 分发请求

### 3.4 Controller 类
**路径**: `com.weacsoft.jaravel.controller.Controller`

**方法**:
- `com.weacsoft.jaravel.http.response.Response ok(String content)` - 创建 200 响应
- `com.weacsoft.jaravel.http.response.Response created(String content)` - 创建 201 响应
- `com.weacsoft.jaravel.http.response.Response accepted(String content)` - 创建 202 响应
- `com.weacsoft.jaravel.http.response.Response noContent()` - 创建 204 响应
- `com.weacsoft.jaravel.http.response.Response badRequest(String content)` - 创建 400 响应
- `com.weacsoft.jaravel.http.response.Response unauthorized(String content)` - 创建 401 响应
- `com.weacsoft.jaravel.http.response.Response forbidden(String content)` - 创建 403 响应
- `com.weacsoft.jaravel.http.response.Response notFound(String content)` - 创建 404 响应
- `com.weacsoft.jaravel.http.response.Response internalServerError(String content)` - 创建 500 响应
- `com.weacsoft.jaravel.http.response.Response json(Object data)` - 创建 JSON 响应
- `com.weacsoft.jaravel.http.response.Response view(String view, java.util.Map<String, Object> data)` - 创建视图响应

### 3.5 Middleware 接口
**路径**: `com.weacsoft.jaravel.middleware.Middleware`

**方法**:
- `com.weacsoft.jaravel.http.response.Response handle(com.weacsoft.jaravel.http.request.Request request, com.weacsoft.jaravel.http.response.Response response, com.weacsoft.jaravel.middleware.Middleware.Next next)` - 中间件处理方法

### 3.6 EncryptCookies 类
**路径**: `com.weacsoft.jaravel.middleware.EncryptCookies`

**方法**:
- `com.weacsoft.jaravel.http.response.Response handle(com.weacsoft.jaravel.http.request.Request request, com.weacsoft.jaravel.http.response.Response response, com.weacsoft.jaravel.middleware.Middleware.Next next)` - 处理 Cookie 加密
- `void encrypt(com.weacsoft.jaravel.http.request.Request request)` - 加密 Cookie
- `void decrypt(com.weacsoft.jaravel.http.request.Request request)` - 解密 Cookie

### 3.7 TrimStrings 类
**路径**: `com.weacsoft.jaravel.middleware.TrimStrings`

**方法**:
- `com.weacsoft.jaravel.http.response.Response handle(com.weacsoft.jaravel.http.request.Request request, com.weacsoft.jaravel.http.response.Response response, com.weacsoft.jaravel.middleware.Middleware.Next next)` - 处理字符串修剪
- `java.util.Map<String, Object> trim(java.util.Map<String, Object> data)` - 修剪字符串数据

### 3.8 TrustProxies 类
**路径**: `com.weacsoft.jaravel.middleware.TrustProxies`

**方法**:
- `com.weacsoft.jaravel.http.response.Response handle(com.weacsoft.jaravel.http.request.Request request, com.weacsoft.jaravel.http.response.Response response, com.weacsoft.jaravel.middleware.Middleware.Next next)` - 处理代理信任

### 3.9 VerifyCsrfToken 类
**路径**: `com.weacsoft.jaravel.middleware.VerifyCsrfToken`

**方法**:
- `com.weacsoft.jaravel.http.response.Response handle(com.weacsoft.jaravel.http.request.Request request, com.weacsoft.jaravel.http.response.Response response, com.weacsoft.jaravel.middleware.Middleware.Next next)` - 处理 CSRF 令牌验证
- `boolean tokensMatch(com.weacsoft.jaravel.http.request.Request request)` - 检查令牌是否匹配
- `String getTokenFromRequest(com.weacsoft.jaravel.http.request.Request request)` - 从请求获取令牌
- `String getTokenFromSession(com.weacsoft.jaravel.http.request.Request request)` - 从会话获取令牌

## 4. listener 模块

### 4.1 Event 接口
**路径**: `com.weacsoft.jaravel.event.Event`

**说明**: 事件标记接口，无方法

### 4.2 Listener 接口
**路径**: `com.weacsoft.jaravel.event.Listener<T extends Event>`

**方法**:
- `void handle(T event)` - 处理事件，泛型参数为事件类型

### 4.3 ListenerService 类
**路径**: `com.weacsoft.jaravel.event.ListenerService`

**方法**:
- `static ListenerService getInstance()` - 获取单例实例
- `<T extends Event> void listen(Class<T> eventClass, Listener<T> listener)` - 注册监听器
- `<T extends Event> void listen(Class<T> eventClass, Listener<T> listener, int priority)` - 注册带优先级的监听器
- `void dispatch(Event event)` - 触发事件
- `void enableQueue()` - 启用队列模式
- `void disableQueue()` - 禁用队列模式
- `boolean isQueueEnabled()` - 检查队列是否启用
- `void shutdown()` - 关闭线程池
- `void clearListeners(Class<? extends Event> eventClass)` - 清除指定事件的所有监听器
- `void clearAllListeners()` - 清除所有监听器

## 5. jblade 模块

### 5.1 BladeEngine 类
**路径**: `com.weacsoft.jaravel.jblade.BladeEngine`

**方法**:
- `BladeEngine setPrefix(String prefix)` - 设置模板前缀
- `BladeEngine setSuffix(String suffix)` - 设置模板后缀
- `BladeEngine setCachePath(String cachePath)` - 设置缓存路径
- `BladeEngine setCacheEnabled(boolean cacheEnabled)` - 设置是否启用缓存
- `BladeTemplate make(String name, java.util.Map<String, Object> data)` - 创建模板实例
- `String render(String name, java.util.Map<String, Object> data)` - 渲染模板

### 5.2 BladeCompiler 类
**路径**: `com.weacsoft.jaravel.jblade.BladeCompiler`

**方法**:
- `String compile(String template)` - 编译模板内容
- `String compileFile(String filePath)` - 编译模板文件

### 5.3 BladeTemplate 类
**路径**: `com.weacsoft.jaravel.jblade.BladeTemplate`

**方法**:
- `String render()` - 渲染模板
- `BladeTemplate with(String key, Object value)` - 添加模板变量
- `BladeTemplate with(java.util.Map<String, Object> data)` - 添加模板变量映射

### 5.4 BladeContext 类
**路径**: `com.weacsoft.jaravel.jblade.BladeContext`

**方法**:
- `Object get(String key)` - 获取上下文变量
- `void put(String key, Object value)` - 设置上下文变量
- `boolean has(String key)` - 检查上下文变量是否存在

## 6. util 模块

### 6.1 StringUtils 类
**路径**: `com.weacsoft.jaravel.utils.StringUtils`

**方法**:
- `static boolean isEmpty(String str)` - 检查字符串是否为空
- `static boolean isNotEmpty(String str)` - 检查字符串是否非空
- `static boolean isBlank(String str)` - 检查字符串是否为空白
- `static boolean isNotBlank(String str)` - 检查字符串是否非空白
- `static String trim(String str)` - 修剪字符串
- `static String trimToNull(String str)` - 修剪字符串，空字符串返回 null
- `static String trimToEmpty(String str)` - 修剪字符串，null 返回空字符串
- `static String substring(String str, int start)` - 字符串截取
- `static String substring(String str, int start, int end)` - 字符串截取
- `static boolean equals(String str1, String str2)` - 字符串比较
- `static boolean equalsIgnoreCase(String str1, String str2)` - 忽略大小写的字符串比较
- `static String camelCase(String str)` - 转换为驼峰命名
- `static String snakeCase(String str)` - 转换为蛇形命名
- `static String kebabCase(String str)` - 转换为短横线命名
- `static String titleCase(String str)` - 转换为标题命名

### 6.2 ExpiryMap 类
**路径**: `com.weacsoft.jaravel.utils.ExpiryMap<K, V>`

**方法**:
- `V get(Object key)` - 获取值，检查过期时间
- `V put(K key, V value, long expiryTimeMs)` - 放入值，带过期时间
- `V put(K key, V value)` - 放入值，使用默认过期时间
- `V remove(Object key)` - 移除值
- `void clear()` - 清除所有值
- `int size()` - 获取有效元素数量
- `boolean isEmpty()` - 检查是否为空
- `boolean containsKey(Object key)` - 检查键是否存在且未过期
- `boolean containsValue(Object value)` - 检查值是否存在
- `void setDefaultExpiryTime(long defaultExpiryTimeMs)` - 设置默认过期时间
- `long getDefaultExpiryTime()` - 获取默认过期时间

### 6.3 内存工具类

#### 6.3.1 ClassFileJavaFileObject 类
**路径**: `com.weacsoft.jaravel.utils.memory.ClassFileJavaFileObject`

**方法**:
- `String getName()` - 获取名称
- `Kind getKind()` - 获取文件类型
- `NashornScriptEngineFactory.CompiledScript getCompiledScript()` - 获取编译后的脚本
- `byte[] getBytes()` - 获取字节码
- `Writer openWriter()` - 打开写入器

#### 6.3.2 MemoryClassLoader 类
**路径**: `com.weacsoft.jaravel.utils.memory.MemoryClassLoader`

**方法**:
- `Class<?> findClass(String name)` - 查找类
- `void addClass(String name, byte[] bytes)` - 添加类字节码

#### 6.3.3 MemoryFileManager 类
**路径**: `com.weacsoft.jaravel.utils.memory.MemoryFileManager`

**方法**:
- `JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling)` - 获取 Java 文件输出对象
- `Iterable<JavaFileObject> getJavaFileObjectsFromStrings(Iterable<String> names)` - 从字符串获取 Java 文件对象
- `void close()` - 关闭文件管理器

#### 6.3.4 SourceCodeJavaFileObject 类
**路径**: `com.weacsoft.jaravel.utils.memory.SourceCodeJavaFileObject`

**方法**:
- `CharSequence getCharContent(boolean ignoreEncodingErrors)` - 获取字符内容
- `Writer openWriter()` - 打开写入器
- `URI toUri()` - 转换为 URI

## 7. springboot-starter 模块

### 7.1 SpringBootRouteAutoConfiguration 类
**路径**: `com.weacsoft.jaravel.springboot.SpringBootRouteAutoConfiguration`

**方法**:
- `RouteService routeService()` - 创建路由服务实例
- `Router router()` - 创建路由器实例
- `com.weacsoft.jaravel.route.RouteHandlerAdapter routeHandlerAdapter()` - 创建路由处理器适配器

### 7.2 SpringBootRequestMVCResolver 类
**路径**: `com.weacsoft.jaravel.springboot.SpringBootRequestMVCResolver`

**方法**:
- `static com.weacsoft.jaravel.http.request.Request resolve(javax.servlet.http.HttpServletRequest request)` - 解析 HttpServletRequest 为 Jaravel Request

### 7.3 SpringBootResponseMVCResolver 类
**路径**: `com.weacsoft.jaravel.springboot.SpringBootResponseMVCResolver`

**方法**:
- `static org.springframework.http.ResponseEntity<?> resolve(com.weacsoft.jaravel.http.response.Response response)` - 解析 Jaravel Response 为 Spring ResponseEntity
- `static org.springframework.web.servlet.ModelAndView resolveView(com.weacsoft.jaravel.http.response.Response response)` - 解析视图响应

### 7.4 ListenerAutoConfiguration 类
**路径**: `com.weacsoft.jaravel.springboot.ListenerAutoConfiguration`

**方法**:
- `ListenerService listenerService(ListenerProperties listenerProperties)` - 创建监听器服务实例

### 7.5 ListenerProperties 类
**路径**: `com.weacsoft.jaravel.springboot.ListenerProperties`

**方法**:
- `boolean isQueueEnabled()` - 检查是否启用队列
- `void setQueueEnabled(boolean queueEnabled)` - 设置是否启用队列

## 8. 类型定义

### 8.1 RouteHandler 函数式接口
**路径**: `com.weacsoft.jaravel.route.RouteHandler`

**方法**:
- `com.weacsoft.jaravel.http.response.Response handle(com.weacsoft.jaravel.http.request.Request request, com.weacsoft.jaravel.http.response.Response response)` - 路由处理方法

### 8.2 Middleware.Next 接口
**路径**: `com.weacsoft.jaravel.middleware.Middleware.Next`

**方法**:
- `com.weacsoft.jaravel.http.response.Response handle(com.weacsoft.jaravel.http.request.Request request, com.weacsoft.jaravel.http.response.Response response)` - 中间件下一步处理方法