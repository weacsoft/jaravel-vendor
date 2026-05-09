package com.weacsoft.jaravel.contract.console;

/**
 * 控制台内核接口，定义 CLI 应用的调度与生命周期契约。
 *
 * <p>参考 Laravel {@code Illuminate\Contracts\Console\Kernel}，
 * 本接口定义命令行应用的核心调度能力，包括命令注册与执行。</p>
 *
 * <h3>设计约束</h3>
 * <ul>
 *   <li>实现类必须保证线程安全</li>
 *   <li>{@link #run(String[])} 应处理所有命令调度逻辑，包括参数解析和异常处理</li>
 *   <li>命令注册应在 {@link #run(String[])} 调用之前完成</li>
 * </ul>
 *
 * @see Command
 */
public interface Kernel {

    /**
     * 注册命令。
     *
     * @param command 命令实例
     */
    void register(Command command);

    /**
     * 运行控制台应用。
     *
     * @param args 命令行参数
     */
    void run(String[] args);
}
