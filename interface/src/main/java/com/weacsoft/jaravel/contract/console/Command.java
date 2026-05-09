package com.weacsoft.jaravel.contract.console;

import java.util.List;
import java.util.Map;

/**
 * 控制台命令接口，定义 CLI 命令的执行契约。
 *
 * <p>参考 Laravel {@code Illuminate\Console\Command} 的命令抽象，
 * 本接口定义命令的元数据读取与执行能力，解耦命令定义与命令调度。</p>
 *
 * <h3>设计约束</h3>
 * <ul>
 *   <li>实现类应为无状态或线程安全</li>
 *   <li>{@link #getName()} 返回的名称在命令注册表中必须唯一</li>
 *   <li>{@link #execute()} 由 {@link Kernel} 调度执行，不应由业务代码直接调用</li>
 *   <li>命令执行异常应由 {@link Kernel} 统一处理</li>
 * </ul>
 *
 * @see Kernel
 */
public interface Command {

    /**
     * 获取命令名称。
     *
     * @return 命令名称，在注册表中必须唯一
     */
    String getName();

    /**
     * 获取命令描述。
     *
     * @return 命令描述，无描述时返回 {@code null}
     */
    String getDescription();

    /**
     * 执行命令。
     */
    void execute();

    /**
     * 获取命令参数定义。
     *
     * @return 参数名到参数定义的映射，无参数时返回空 Map
     */
    Map<String, ?> getArguments();

    /**
     * 获取命令选项定义。
     *
     * @return 选项名到选项定义的映射，无选项时返回空 Map
     */
    Map<String, ?> getOptions();

    /**
     * 获取命令别名列表。
     *
     * @return 别名列表，无别名时返回空列表
     */
    List<String> getAliases();
}
