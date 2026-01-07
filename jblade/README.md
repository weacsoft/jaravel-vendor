# JBlade 模板引擎

JBlade 是一个受 Laravel Blade 启发的 Java 模板引擎，提供优雅的模板语法和强大的组件系统。

## 特性

- **模板继承** - 支持 `@extends`, `@section`, `@yield` 实现模板继承
- **条件判断** - 支持 `@if`, `@elseif`, `@else`, `@endif`
- **循环控制** - 支持 `@for`, `@foreach`, `@endfor`, `@endforeach`
- **组件系统** - 支持 `@component`, `@endcomponent`, `@slot`, `@endslot`
- **变量输出** - 使用 `{{ $var }}` 输出变量
- **注释** - 使用 `{{-- 注释 --}}` 添加注释
- **动态编译** - 运行时动态编译模板
- **内存缓存** - 编译后的模板缓存在内存中，提升性能

## 快速开始

### 1. 创建 BladeEngine

```java
import com.weacsoft.jaravel.jblade.BladeEngine;

// 创建引擎，指定模板目录
BladeEngine engine = new BladeEngine("templates");
```

### 2. 准备数据

```java
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;

Map<String, Object> data = new HashMap<>();
data.put("name", "World");
data.put("items", Arrays.asList("Apple", "Banana", "Orange"));
data.put("user", new HashMap<String, String>() {{
    put("name", "John");
    put("email", "john@example.com");
}});
```

### 3. 渲染模板

```java
String result = engine.render("welcome", data);
System.out.println(result);
```

## 模板语法

### 变量输出

```jblade
<h1>Hello, {{ $name }}!</h1>
<p>Email: {{ $user.email }}</p>
<p>Count: {{ $items.size() }}</p>
```

### 条件判断

```jblade
@if ($user)
    <p>Welcome, {{ $user.name }}!</p>
@elseif ($guest)
    <p>Welcome, Guest!</p>
@else
    <p>Please login.</p>
@endif
```

### 循环

```jblade
<ul>
    @foreach ($items as $item)
        <li>{{ $item }}</li>
    @endforeach
</ul>

@for ($i = 0; $i < 10; $i++)
    <p>Item {{ $i }}</p>
@endfor
```

### 模板继承

**父模板 (layouts/app.jblade):**
```jblade
<!DOCTYPE html>
<html>
<head>
    <title>@yield('title', 'Default Title')</title>
</head>
<body>
    <header>
        <h1>My Website</h1>
    </header>
    
    <main>
        @yield('content')
    </main>
    
    <footer>
        &copy; 2026 My Website
    </footer>
</body>
</html>
```

**子模板:**
```jblade
@extends('layouts.app')

@section('title', 'Home Page')

@section('content')
    <h2>Welcome to My Website</h2>
    <p>This is the home page content.</p>
@endsection
```

## 组件系统

JBlade 提供了强大的组件系统，类似于 Laravel Blade 的组件功能。

### 基本组件

**定义组件 (components/alert.jblade):**
```jblade
<div class="alert alert-{{ $type }}">
    @if ($title)
        <h4>{{ $title }}</h4>
    @endif
    
    @if ($slot)
        <p>{{ $slot }}</p>
    @endif
</div>
```

**使用组件:**
```jblade
@component('alert', ['type' => 'success'])
    操作成功！
@endcomponent

@component('alert', ['type' => 'warning', 'title' => '警告'])
    请注意此操作
@endcomponent
```

### 使用插槽

**定义带插槽的组件 (components/card.jblade):**
```jblade
<div class="card">
    @if ($title)
        <div class="card-header">
            <h3>{{ $title }}</h3>
        </div>
    @endif
    
    @if ($header)
        <div class="card-header-custom">
            {{ $header }}
        </div>
    @endif
    
    <div class="card-body">
        @if ($slot)
            {{ $slot }}
        @endif
    </div>
    
    @if ($footer)
        <div class="card-footer">
            {{ $footer }}
        </div>
    @endif
</div>
```

**使用带插槽的组件:**
```jblade
@component('card', ['title' => '我的卡片'])
    @slot('header')
        <span>卡片头部内容</span>
    @endslot
    
    <p>这是卡片的主要内容区域。</p>
    <p>可以放置任何HTML内容。</p>
    
    @slot('footer')
        <button>确定</button>
        <button>取消</button>
    @endslot
@endcomponent
```

### 组件变量说明

在组件模板中，可以访问以下变量：

- `$slot` - 默认插槽内容（组件标签内的内容）
- `$header`, `$footer` 等 - 命名插槽内容（通过 `@slot` 定义）
- `$type`, `$title` 等 - 组件参数（通过组件属性传递）

### 嵌套组件

组件可以嵌套使用：

```jblade
@component('card', ['title' => '包含警告的卡片'])
    @slot('header')
        <h3>重要通知</h3>
    @endslot
    
    <p>以下是警告信息：</p>
    
    @component('alert', ['type' => 'warning', 'title' => '注意'])
        这是嵌套在卡片中的警告组件
    @endcomponent
    
    @slot('footer')
        <small>最后更新: 2026-01-07</small>
    @endslot
@endcomponent
```

### 列表组件

**定义列表组件 (components/list.jblade):**
```jblade
<ul class="list">
    @if ($items && count($items) > 0)
        @foreach ($items as $item)
            <li>{{ $item }}</li>
        @endforeach
    @else
        @if ($empty)
            <li class="empty">{{ $empty }}</li>
        @else
            <li class="empty">没有数据</li>
        @endif
    @endif
</ul>
```

**使用列表组件:**
```jblade
@component('list', ['items' => ['苹果', '香蕉', '橙子']])
    @slot('empty')
        没有数据
    @endslot
@endcomponent
```

## 注释

```jblade
{{-- 这是一个注释，不会输出到HTML --}}

<p>这是可见的内容</p>
```

## 完整示例

### 模板文件 (templates/welcome.jblade)

```jblade
<!DOCTYPE html>
<html>
<head>
    <title>JBlade Demo</title>
    <style>
        .alert {
            padding: 15px;
            margin-bottom: 20px;
            border: 1px solid transparent;
            border-radius: 4px;
        }
        .alert-success {
            color: #3c763d;
            background-color: #dff0d8;
            border-color: #d6e9c6;
        }
        .alert-info {
            color: #31708f;
            background-color: #d9edf7;
            border-color: #bce8f1;
        }
    </style>
</head>
<body>
    <h1>Hello, {{ $name }}!</h1>
    
    @if ($items)
        <h2>Items:</h2>
        <ul>
            @foreach ($items as $item)
                <li>{{ $item }}</li>
            @endforeach
        </ul>
    @endif
    
    <h2>Components:</h2>
    
    @component('alert', ['type' => 'success'])
        Welcome to JBlade!
    @endcomponent
    
    @component('alert', ['type' => 'info', 'title' => 'Information'])
        This is a component with title
    @endcomponent
</body>
</html>
```

### Java 代码

```java
import com.weacsoft.jaravel.jblade.BladeEngine;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;

public class Main {
    public static void main(String[] args) throws Exception {
        BladeEngine engine = new BladeEngine("templates");
        
        Map<String, Object> data = new HashMap<>();
        data.put("name", "Jaravel");
        data.put("items", Arrays.asList("Feature 1", "Feature 2", "Feature 3"));
        
        String html = engine.render("welcome", data);
        System.out.println(html);
    }
}
```

## 测试

JBlade 提供了完整的组件功能测试示例。

### 运行测试

```bash
# 编译项目
mvn clean compile

# 运行组件测试
java -cp "target/classes;..." ComponentTest
```

### 测试模板

测试模板位于 `jblade/templates/` 目录：

- `component_test.jblade` - 组件功能测试主模板
- `alert.jblade` - 警告组件
- `card.jblade` - 卡片组件
- `list.jblade` - 列表组件

### 测试内容

1. **基本组件使用** - 测试最简单的组件调用
2. **带标题的组件** - 测试带参数的组件
3. **使用插槽** - 测试默认插槽功能
4. **自定义卡片组件** - 测试多插槽组件
5. **嵌套组件** - 测试组件嵌套功能
6. **列表组件** - 测试带数据的组件

## API 文档

### BladeEngine

```java
// 创建引擎
BladeEngine(String templateDir)

// 渲染模板（带数据）
String render(String templateName, Map<String, Object> variables)

// 渲染模板（无数据）
String render(String templateName)

// 清除缓存
void clearCache()
```

### BladeContext

```java
// 设置变量
void setVariable(String name, Object value)

// 获取变量
Object getVariable(String name)

// 设置父模板
void setParentTemplate(String parentTemplate)

// 获取父模板
String getParentTemplate()

// 设置区块
void setSection(String name, String content)

// 获取区块
String getSection(String name)

// 设置区块渲染器
void setSectionRenderer(String name, Consumer<Writer> renderer)

// 获取区块渲染器
Consumer<Writer> getSectionRenderer(String name)
```

## 注意事项

1. **模板目录** - 模板文件必须放在指定的模板目录中
2. **文件扩展名** - 模板文件必须使用 `.jblade` 扩展名
3. **JDK要求** - 需要使用 JDK 而非 JRE，因为需要动态编译
4. **性能优化** - 模板编译后会缓存在内存中，避免重复编译
5. **变量作用域** - 组件内的变量是独立的，不会影响外部变量

## 许可证

本项目采用开源许可证，具体请参考项目文件。

## 贡献

欢迎提交 Issue 和 Pull Request！