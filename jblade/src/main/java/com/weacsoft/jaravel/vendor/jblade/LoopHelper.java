package com.weacsoft.jaravel.vendor.jblade;

/**
 * Blade $loop 变量的等价实现。
 * <p>
 * 在 @foreach / @forelse 循环体内，模板可访问 {@code $loop} 变量：
 * index / iteration / first / last / count / remaining / odd / even / depth / parent。
 * 属性语义与 Laravel Blade 完全一致。
 */
public class LoopHelper {

    /** 当前迭代索引（从 0 开始） */
    private int index = 0;
    /** 当前迭代次数（从 1 开始） */
    private int iteration = 0;
    /** 集合元素总数（未知时为 -1） */
    private final int count;
    /** 嵌套深度（从 1 开始） */
    private final int depth;
    /** 父级循环（最外层为 null） */
    private final LoopHelper parent;

    public LoopHelper(int count, int depth, LoopHelper parent) {
        this.count = count;
        this.depth = depth;
        this.parent = parent;
    }

    /** 每次迭代前调用：i 为 0 起的索引 */
    public void advance(int i) {
        this.index = i;
        this.iteration = i + 1;
    }

    public int getIndex() {
        return index;
    }

    public int getIteration() {
        return iteration;
    }

    public int getCount() {
        return count;
    }

    public boolean isFirst() {
        return index == 0;
    }

    public boolean isLast() {
        return count >= 0 && iteration == count;
    }

    public int getRemaining() {
        return count >= 0 ? count - iteration : -1;
    }

    public boolean isOdd() {
        return iteration % 2 == 1;
    }

    public boolean isEven() {
        return iteration % 2 == 0;
    }

    public int getDepth() {
        return depth;
    }

    public LoopHelper getParent() {
        return parent;
    }

    /**
     * 以 Blade 属性名读取（供表达式 $loop->xxx 反射兜底使用）。
     */
    public Object prop(String name) {
        switch (name) {
            case "index":
                return index;
            case "iteration":
                return iteration;
            case "count":
                return count;
            case "first":
                return isFirst();
            case "last":
                return isLast();
            case "remaining":
                return getRemaining();
            case "odd":
                return isOdd();
            case "even":
                return isEven();
            case "depth":
                return depth;
            case "parent":
                return parent;
            default:
                return null;
        }
    }
}
