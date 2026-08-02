package com.weacsoft.jaravel.vendor.core.pagination;

import com.weacsoft.jaravel.vendor.core.view.Htmlable;
import com.weacsoft.jaravel.vendor.core.view.HtmlString;
import com.weacsoft.jaravel.vendor.core.view.View;
import com.weacsoft.jaravel.vendor.core.view.ViewProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Laravel 风格分页器（core 标准层）。
 * <p>
 * 提供与 PHP Blade 对齐的分页 API：{@code hasPages()}/{@code onFirstPage()}/
 * {@code hasMorePages()}/{@code previousPageUrl()}/{@code nextPageUrl()}/
 * {@code url(n)}/{@code appends()}/{@code elements()}/{@code links()}。
 * </p>
 * <p>
 * 本类位于 core，<b>不依赖具体模板引擎</b>：{@code links(viewName)} 通过
 * {@link ViewProvider} 注入的默认 {@link View} 渲染分页模板。模板引擎（jblade）在启动时
 * 注入 provider 即可；未注入或模板缺失时 {@code links()} 安全降级为空串，等价于「无分页视图时未执行」。
 * </p>
 * <p>
 * 实现 {@link Iterable}，模板中可直接 {@code @foreach($list as $item)}；实现 {@link Htmlable}
 * 使得 {@code {{ $list }}} 输出分页 HTML 时免转义。
 * </p>
 *
 * @param <T> 元素类型
 */
public class Paginator<T> implements Iterable<T>, Htmlable {

    /** 默认视图提供者（由模板引擎注入）；为 null 时 links 降级空串。 */
    private static volatile ViewProvider defaultViewProvider = null;

    /** 默认分页模板名（可被子类/上游覆盖）。 */
    private static String defaultViewName = "layouts.mdui.pageinator";

    private final List<T> items;
    private final long total;
    private final int perPage;
    private final int currentPage;

    private String path = "";
    private final Map<String, Object> appendQuery = new LinkedHashMap<>();

    public Paginator(List<T> items, long total, int perPage, int currentPage) {
        this.items = items == null ? new ArrayList<>() : items;
        this.total = total;
        this.perPage = perPage <= 0 ? 1 : perPage;
        this.currentPage = currentPage < 1 ? 1 : currentPage;
    }

    public static void setDefaultViewProvider(ViewProvider provider) {
        defaultViewProvider = provider;
    }

    public static void setDefaultViewName(String viewName) {
        if (viewName != null && !viewName.isEmpty()) {
            defaultViewName = viewName;
        }
    }

    public List<T> items() {
        return items;
    }

    public long total() {
        return total;
    }

    public int perPage() {
        return perPage;
    }

    public int currentPage() {
        return currentPage;
    }

    public long lastPage() {
        if (perPage <= 0) {
            return 1;
        }
        return Math.max(1, (total + perPage - 1) / perPage);
    }

    /** 最后一页页码（int 便捷方法，对齐 Laravel/PHP 习惯）。 */
    public int getLastPage() {
        return (int) lastPage();
    }

    public long count() {
        return items.size();
    }

    /** 是否在最后一页。 */
    public boolean onLastPage() {
        return currentPage >= getLastPage();
    }

    /** 当前页第一条记录的序号（从 1 开始，空列表为 0）。 */
    public long firstItem() {
        return items.isEmpty() ? 0 : (long) (currentPage - 1) * perPage + 1;
    }

    /** 当前页最后一条记录的序号（空列表为 0）。 */
    public long lastItem() {
        return items.isEmpty() ? 0 : firstItem() + items.size() - 1;
    }

    /** 首页 URL。 */
    public String firstPageUrl() {
        return url(1);
    }

    /** 末页 URL。 */
    public String lastPageUrl() {
        return url(getLastPage());
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public boolean hasPages() {
        return lastPage() > 1;
    }

    public boolean onFirstPage() {
        return currentPage <= 1;
    }

    public boolean hasMorePages() {
        return currentPage < lastPage();
    }

    public String path() {
        return path;
    }

    public Paginator<T> setPath(String path) {
        this.path = path == null ? "" : path;
        return this;
    }

    public Paginator<T> appends(String key, Object value) {
        if (key != null) {
            appendQuery.put(key, value);
        }
        return this;
    }

    public Paginator<T> appends(Map<String, Object> map) {
        if (map != null) {
            appendQuery.putAll(map);
        }
        return this;
    }

    public Map<String, Object> appends() {
        return appendQuery;
    }

    private String buildQueryString(int page) {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("page", page);
        q.putAll(appendQuery);
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Object> e : q.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            sb.append(first ? "?" : "&")
              .append(encode(String.valueOf(e.getKey())))
              .append("=").append(encode(String.valueOf(e.getValue())));
            first = false;
        }
        return sb.toString();
    }

    private static String encode(String s) {
        if (s == null) {
            return "";
        }
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return s;
        }
    }

    public String url(int page) {
        if (page < 1) {
            page = 1;
        }
        return path + buildQueryString(page);
    }

    public String previousPageUrl() {
        if (onFirstPage()) {
            return null;
        }
        return url(currentPage - 1);
    }

    public String nextPageUrl() {
        if (!hasMorePages()) {
            return null;
        }
        return url(currentPage + 1);
    }

    /**
     * 生成分页元素序列（用于渲染页码），返回与模板契约一致的 Map 列表。
     * 每个元素为 {@code Map}，含字段：
     * <ul>
     *   <li>{@code type}：{@code "page"} 或 {@code "separator"}</li>
     *   <li>{@code page}：页码（separator 时忽略）</li>
     *   <li>{@code url}：该页 URL（separator 时为空串）</li>
     *   <li>{@code active}：是否当前页</li>
     * </ul>
     * 形如 [{"type":"page","page":1,...}, {"type":"separator",...}, {"type":"page","page":5,...}]。
     *
     * @param onEachSide 当前页两侧保留的页数
     * @return 元素 Map 列表
     */
    public List<Map<String, Object>> elements(int onEachSide) {
        long last = lastPage();
        if (last <= 1) {
            return new ArrayList<>();
        }
        if (onEachSide < 0) {
            onEachSide = 3;
        }
        List<Map<String, Object>> result = new ArrayList<>();
        long windowStart = Math.max(1, (long) currentPage - onEachSide);
        long windowEnd = Math.min(last, (long) currentPage + onEachSide);

        long previous = 0;
        for (long page = 1; page <= last; page++) {
            boolean inWindow = page >= windowStart && page <= windowEnd;
            boolean isEdge = page == 1 || page == last;
            if (!inWindow && !isEdge) {
                continue;
            }
            if (previous != 0 && page - previous > 1) {
                Map<String, Object> sep = new LinkedHashMap<>();
                sep.put("type", "separator");
                sep.put("page", 0);
                sep.put("url", "");
                sep.put("active", false);
                result.add(sep);
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "page");
            item.put("page", (int) page);
            item.put("url", url((int) page));
            item.put("active", page == currentPage);
            result.add(item);
            previous = page;
        }
        return result;
    }

    public List<Map<String, Object>> elements() {
        return elements(3);
    }

    /**
     * 默认模板名渲染分页 HTML（模板引擎已注入默认视图时可用）。
     *
     * @return 渲染字符串；无视图/无模板/降级时返回空串
     */
    public Htmlable links() {
        return links(defaultViewName);
    }

    /**
     * 以指定模板名渲染分页 HTML。
     * <p>
     * 三级降级：① 仅一页（或未配置分页）返回空串；② 视图未就绪或模板不存在返回空串；
     * ③ 正常渲染返回 HTML。保证「无分页视图时等同于未执行」。
     * </p>
     *
     * @param viewName 模板名（点号命名空间）
     * @return 渲染后的 HTML（{@link Htmlable}），未渲染时为空 {@link HtmlString}
     */
    public Htmlable links(String viewName) {
        if (!hasPages()) {
            return HtmlString.raw("");
        }
        View view = defaultViewProvider == null ? null : defaultViewProvider.getDefaultView();
        if (view == null || viewName == null || viewName.isEmpty()) {
            return HtmlString.raw("");
        }
        // 已注册具体视图：通过 exists 判存在（视图不支持时视为不存在）
        try {
            if (!view.exists(viewName)) {
                return HtmlString.raw("");
            }
        } catch (Throwable ignore) {
            return HtmlString.raw("");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("paginator", this);
        data.put("elements", elements());
        try {
            return HtmlString.raw(view.render(viewName, data));
        } catch (Exception e) {
            return HtmlString.raw("");
        }
    }

    @Override
    public String toHtml() {
        return links().toHtml();
    }

    @Override
    public java.util.Iterator<T> iterator() {
        return items.iterator();
    }
}
