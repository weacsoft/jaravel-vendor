package com.weacsoft.jaravel.vendor.jblade.pagination;

import com.weacsoft.jaravel.vendor.jblade.Htmlable;
import com.weacsoft.jaravel.vendor.jblade.HtmlString;
import com.weacsoft.jaravel.vendor.jblade.view.View;
import com.weacsoft.jaravel.vendor.jblade.view.ViewFacade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分页器，对齐 Laravel 的 {@code Illuminate\Pagination\LengthAwarePaginator}。
 * <p>
 * 核心能力是让模板中可以像 PHP Blade 那样一行生成分页器：
 * <pre>
 * {{ $list->links('layouts.mdui.pageinator') }}
 * </pre>
 * {@link #links()} 返回实现了 {@link Htmlable} 的对象，因此 <code>{{ }}</code>
 * 不会对其做 HTML 转义（见 {@code BladeTemplate#e(Object)}）。
 * <p>
 * <b>优雅降级</b>：以下任一情况，{@link #links()} 均返回空串而不抛异常，
 * 相当于「没有分页就等于没执行」：
 * <ul>
 *   <li>总页数 &lt;= 1（{@link #hasPages()} 为 false）；</li>
 *   <li>指定的分页模板不存在；</li>
 *   <li>视图层尚未初始化或渲染过程抛错。</li>
 * </ul>
 *
 * @param <T> 列表元素类型
 */
public class Paginator<T> implements Iterable<T> {

    /** 默认分页模板名，找不到时 links() 返回空串。 */
    public static final String DEFAULT_VIEW = "layouts.mdui.pageinator";

    private final List<T> items;
    private final long total;
    private final int perPage;
    private final int currentPage;

    /** 生成链接使用的基础路径，如 "/users"。 */
    private String path = "";
    /** 页码参数名，对齐 Laravel 的 "page"。 */
    private String pageName = "page";
    /** 附加到每个链接上的查询参数。 */
    private final Map<String, String> query = new LinkedHashMap<>();
    /** 链接两侧保留的页码数量，对齐 Laravel 默认值 3。 */
    private int onEachSide = 3;

    public Paginator(List<T> items, long total, int perPage, int currentPage) {
        this.items = items == null ? Collections.<T>emptyList() : items;
        this.total = Math.max(total, 0);
        this.perPage = perPage <= 0 ? 15 : perPage;
        this.currentPage = currentPage <= 0 ? 1 : currentPage;
    }

    // ==================== 数据访问 ====================

    /** 当前页数据。 */
    public List<T> getItems() {
        return items;
    }

    /** 当前页数据（Laravel 别名 {@code items()}）。 */
    public List<T> items() {
        return items;
    }

    /** 记录总数。 */
    public long getTotal() {
        return total;
    }

    public long total() {
        return total;
    }

    /** 每页条数。 */
    public int getPerPage() {
        return perPage;
    }

    public int perPage() {
        return perPage;
    }

    /** 当前页码（从 1 开始）。 */
    public int getCurrentPage() {
        return currentPage;
    }

    public int currentPage() {
        return currentPage;
    }

    /** 最后一页页码。 */
    public int getLastPage() {
        if (total == 0) {
            return 1;
        }
        return (int) Math.ceil((double) total / (double) perPage);
    }

    public int lastPage() {
        return getLastPage();
    }

    /** 当前页条数。 */
    public int count() {
        return items.size();
    }

    /** 当前页第一条记录的序号（从 1 开始），无数据时返回 0。 */
    public long firstItem() {
        return items.isEmpty() ? 0 : (long) (currentPage - 1) * perPage + 1;
    }

    /** 当前页最后一条记录的序号，无数据时返回 0。 */
    public long lastItem() {
        return items.isEmpty() ? 0 : firstItem() + items.size() - 1;
    }

    /** 是否为空。 */
    public boolean isEmpty() {
        return items.isEmpty();
    }

    public boolean isNotEmpty() {
        return !items.isEmpty();
    }

    @Override
    public java.util.Iterator<T> iterator() {
        return items.iterator();
    }

    // ==================== 分页状态 ====================

    /** 是否需要渲染分页器（总页数大于 1）。 */
    public boolean hasPages() {
        return getLastPage() > 1;
    }

    /** 是否存在下一页。 */
    public boolean hasMorePages() {
        return currentPage < getLastPage();
    }

    /** 是否在第一页。 */
    public boolean onFirstPage() {
        return currentPage <= 1;
    }

    /** 是否在最后一页。 */
    public boolean onLastPage() {
        return currentPage >= getLastPage();
    }

    // ==================== 链接生成 ====================

    /**
     * 设置生成链接的基础路径。
     *
     * @param path 如 "/users"
     * @return this
     */
    public Paginator<T> setPath(String path) {
        this.path = path == null ? "" : path;
        return this;
    }

    public String getPath() {
        return path;
    }

    /** 设置页码参数名。 */
    public Paginator<T> setPageName(String pageName) {
        if (pageName != null && !pageName.isEmpty()) {
            this.pageName = pageName;
        }
        return this;
    }

    public String getPageName() {
        return pageName;
    }

    /** 追加查询参数，对齐 Laravel 的 {@code appends()}。 */
    public Paginator<T> appends(String key, String value) {
        if (key != null && !key.isEmpty() && !key.equals(pageName)) {
            query.put(key, value == null ? "" : value);
        }
        return this;
    }

    /** 批量追加查询参数。 */
    public Paginator<T> appends(Map<String, ?> params) {
        if (params != null) {
            for (Map.Entry<String, ?> e : params.entrySet()) {
                Object v = e.getValue();
                appends(e.getKey(), v == null ? "" : String.valueOf(v));
            }
        }
        return this;
    }

    /** 设置当前页两侧显示的页码数量。 */
    public Paginator<T> onEachSide(int count) {
        this.onEachSide = Math.max(count, 0);
        return this;
    }

    public int getOnEachSide() {
        return onEachSide;
    }

    /**
     * 生成指定页码的 URL。
     *
     * @param page 页码（小于 1 时按 1 处理）
     * @return 该页的 URL
     */
    public String url(int page) {
        if (page < 1) {
            page = 1;
        }
        StringBuilder sb = new StringBuilder(path);
        sb.append(path.contains("?") ? '&' : '?');
        sb.append(encode(pageName)).append('=').append(page);
        for (Map.Entry<String, String> e : query.entrySet()) {
            sb.append('&').append(encode(e.getKey())).append('=').append(encode(e.getValue()));
        }
        return sb.toString();
    }

    /** 上一页 URL，已在第一页时返回 null。 */
    public String previousPageUrl() {
        return onFirstPage() ? null : url(currentPage - 1);
    }

    /** 下一页 URL，已在最后一页时返回 null。 */
    public String nextPageUrl() {
        return onLastPage() ? null : url(currentPage + 1);
    }

    /** 第一页 URL。 */
    public String firstPageUrl() {
        return url(1);
    }

    /** 最后一页 URL。 */
    public String lastPageUrl() {
        return url(getLastPage());
    }

    /**
     * 生成页码元素列表，供模板遍历。
     * <p>
     * 每个元素为一个 Map，字段：
     * <ul>
     *   <li>{@code type}：{@code "page"} 或 {@code "separator"}</li>
     *   <li>{@code page}：页码（separator 时为 0）</li>
     *   <li>{@code url}：链接（separator 时为空串）</li>
     *   <li>{@code active}：是否为当前页</li>
     * </ul>
     *
     * @return 页码元素列表
     */
    public List<Map<String, Object>> elements() {
        List<Map<String, Object>> result = new ArrayList<>();
        int last = getLastPage();
        if (last <= 1) {
            return result;
        }
        // 窗口：首尾各保留 1 页，当前页两侧保留 onEachSide 页
        int windowStart = Math.max(1, currentPage - onEachSide);
        int windowEnd = Math.min(last, currentPage + onEachSide);

        int previous = 0;
        for (int page = 1; page <= last; page++) {
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
            item.put("page", page);
            item.put("url", url(page));
            item.put("active", page == currentPage);
            result.add(item);
            previous = page;
        }
        return result;
    }

    // ==================== 渲染 ====================

    /**
     * 使用默认模板渲染分页器 HTML。
     *
     * @return 可直接用 <code>{{ }}</code> 输出的 HTML；无分页或模板缺失时为空串
     */
    public Htmlable links() {
        return links(DEFAULT_VIEW);
    }

    /**
     * 使用指定模板渲染分页器 HTML，对齐 Laravel 的
     * <code>{{ $list-&gt;links('layouts.mdui.pageinator') }}</code>。
     * <p>
     * 模板中可用变量：{@code paginator}（本对象）、{@code elements}（页码列表）。
     * 任何异常或缺失都会静默降级为空串，保证页面不因分页器报错。
     *
     * @param viewName 模板名（点号分隔，如 "layouts.mdui.pageinator"）
     * @return 分页器 HTML
     */
    public Htmlable links(String viewName) {
        if (!hasPages()) {
            return HtmlString.of("");
        }
        View view;
        try {
            view = ViewFacade.getView();
        } catch (RuntimeException e) {
            // 视图层未初始化：当作没有分页器
            return HtmlString.of("");
        }
        String name = (viewName == null || viewName.isEmpty()) ? DEFAULT_VIEW : viewName;
        if (!view.exists(name)) {
            return HtmlString.of("");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("paginator", this);
        data.put("elements", elements());
        try {
            return HtmlString.of(view.render(name, data));
        } catch (Exception e) {
            return HtmlString.of("");
        }
    }

    /** {@code render()} 为 Laravel 中 {@code links()} 的别名。 */
    public Htmlable render() {
        return links();
    }

    public Htmlable render(String viewName) {
        return links(viewName);
    }

    private static String encode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return s;
        }
    }

    @Override
    public String toString() {
        return "Paginator{currentPage=" + currentPage + ", lastPage=" + getLastPage()
                + ", perPage=" + perPage + ", total=" + total + ", items=" + items.size() + "}";
    }
}
