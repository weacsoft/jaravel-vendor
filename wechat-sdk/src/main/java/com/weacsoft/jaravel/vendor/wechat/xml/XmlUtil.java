package com.weacsoft.jaravel.vendor.wechat.xml;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 微信消息 XML 工具：Map → XML（被动回复/事件应答）与 XML → Map（推送消息解析）。
 * <p>
 * 规则与微信官方样例严格对齐：
 * <ul>
 *   <li>文本值用 CDATA 包裹（值内 {@code ]]>} 拆分为 {@code ]]]]]><![CDATA[>}，保持合法）；</li>
 *   <li>数值/布尔值<b>不</b>加 CDATA（如 {@code <CreateTime>1348831860</CreateTime>}、
 *       {@code <ArticleCount>2</ArticleCount>}）；</li>
 *   <li>嵌套 Map → 嵌套元素；Map 中的 List 值 → 以该键名重复的元素
 *       （如 {@code Articles → {item: [..]}} 渲染为多个 {@code <item>} 子节点）；</li>
 *   <li>解析时使用 JDK 自带 StAX（java.xml 模块，JRE 即可用，无第三方依赖），
 *       禁用 DTD/外部实体，防止 XXE；同名兄弟元素自动聚合为 List。</li>
 * </ul>
 *
 * @author weacsoft
 */
public final class XmlUtil {

    private XmlUtil() {
    }

    /**
     * 将字段表渲染为微信风格 XML 文档。
     * <p>
     * 值为 null 的字段跳过；嵌套 Map 递归渲染；List 元素支持 Map（子节点）与字符串/数值（CDATA 或原文）。
     *
     * @param rootTag 根元素名（如 {@code xml}）
     * @param fields  字段表（PascalCase 键，微信 wire 名）
     * @return XML 字符串
     */
    public static String toXml(String rootTag, Map<String, Object> fields) {
        StringBuilder sb = new StringBuilder(256);
        sb.append('<').append(rootTag).append('>');
        appendFields(sb, fields);
        sb.append("</").append(rootTag).append('>');
        return sb.toString();
    }

    private static void appendFields(StringBuilder sb, Map<String, Object> fields) {
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            if (value instanceof Map<?, ?> map) {
                sb.append('<').append(name).append('>');
                appendFields(sb, (Map<String, Object>) map);
                sb.append("</").append(name).append('>');
            } else if (value instanceof List<?> list) {
                for (Object item : list) {
                    sb.append('<').append(name).append('>');
                    appendScalar(sb, item);
                    sb.append("</").append(name).append('>');
                }
            } else {
                sb.append('<').append(name).append('>');
                appendScalar(sb, value);
                sb.append("</").append(name).append('>');
            }
        }
    }

    private static void appendScalar(StringBuilder sb, Object value) {
        if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Map<?, ?> map) {
            appendFields(sb, (Map<String, Object>) map);
        } else {
            sb.append(cdata(String.valueOf(value)));
        }
    }

    /**
     * CDATA 包裹，值内的 {@code ]]>} 按标准做法拆分处理。
     */
    private static String cdata(String text) {
        return "<![CDATA[" + text.replace("]]>", "]]]]><![CDATA[>") + "]]>";
    }

    /**
     * 解析微信推送 XML 为 Map 结构。
     * <p>
     * 叶子节点 → 字符串；容器节点 → LinkedHashMap（保持顺序）；同名兄弟节点 → List。
     * 禁用 DTD 与外部实体（防 XXE）。
     *
     * @param xml XML 文本
     * @return 以根元素为键的 Map
     * @throws IllegalArgumentException XML 结构非法时
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseXml(String xml) {
        try {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
            factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.FALSE);
            XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xml));
            try {
                Map<String, Object> root = new LinkedHashMap<>();
                Deque<Map<String, Object>> maps = new ArrayDeque<>();
                Deque<List<Object>> listSentinels = new ArrayDeque<>();
                Deque<StringBuilder> texts = new ArrayDeque<>();

                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        String name = reader.getLocalName();
                        Map<String, Object> parent = maps.peek() != null ? maps.peek() : root;

                        if (parent.containsKey(name)) {
                            // 同名兄弟节点：聚合为 List
                            Object existing = parent.get(name);
                            List<Object> list;
                            if (existing instanceof List<?> l) {
                                list = (List<Object>) l;
                            } else {
                                list = new ArrayList<>();
                                list.add(existing);
                                parent.put(name, list);
                            }
                            Map<String, Object> child = new LinkedHashMap<>();
                            list.add(child);
                            maps.push(child);
                            listSentinels.push(list);
                        } else {
                            Map<String, Object> child = new LinkedHashMap<>();
                            parent.put(name, child);
                            maps.push(child);
                            listSentinels.push(new ArrayList<>());
                        }
                        texts.push(new StringBuilder());
                    } else if (event == XMLStreamConstants.END_ELEMENT) {
                        StringBuilder text = texts.pop();
                        Map<String, Object> child = maps.pop();
                        List<Object> list = listSentinels.pop();
                        String trimmed = text.toString().trim();
                        if (trimmed.isEmpty() || !child.isEmpty()) {
                            // 容器节点（或空元素）：保留 Map 结构；微信推送 XML 无混排文本
                            continue;
                        }
                        // 叶子节点：用文本替换该 Map 占位
                        if (!list.isEmpty() && list.get(list.size() - 1) == child) {
                            list.set(list.size() - 1, trimmed);
                        } else {
                            Map<String, Object> parent = maps.peek() != null ? maps.peek() : root;
                            for (Map.Entry<String, Object> e : parent.entrySet()) {
                                if (e.getValue() == child) {
                                    e.setValue(trimmed);
                                    break;
                                }
                            }
                        }
                    } else if (event == XMLStreamConstants.CHARACTERS
                            || event == XMLStreamConstants.CDATA) {
                        if (!texts.isEmpty()) {
                            texts.peek().append(reader.getText());
                        }
                    }
                }
                return root;
            } finally {
                reader.close();
            }
        } catch (XMLStreamException e) {
            throw new IllegalArgumentException("微信 XML 解析失败: " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("微信 XML 解析失败: " + e.getMessage(), e);
        }
    }
}
