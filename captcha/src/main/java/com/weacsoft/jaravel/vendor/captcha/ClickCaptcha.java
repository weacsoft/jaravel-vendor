package com.weacsoft.jaravel.vendor.captcha;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文字点选验证码：在背景图上随机生成若干汉字，要求用户按提示顺序点击指定文字。
 * <p>
 * 类型名 {@code "click"}。生成结果中：
 * <ul>
 *     <li>{@code imageBase64} —— 包含所有展示文字的背景图；</li>
 *     <li>{@code extra.prompt} —— 点选提示文字，如 "请依次点击：天、地、人"；</li>
 *     <li>{@code extra.clickCount} —— 需要点击的文字数量。</li>
 * </ul>
 * <p>
 * 出于安全考虑，<b>不再</b>向前端返回每个文字的坐标位置（{@code extra.positions}），
 * 前端无法获知文字的具体落点，需完全依赖用户视觉识别后点击。
 * <p>
 * 答案为需要点击的<b>目标文字坐标</b>，以分号分隔的坐标对，如 "x1,y1;x2,y2;x3,y3"，
 * 坐标顺序与提示文字顺序一致。验证时比对用户点击坐标与目标坐标的距离是否在容差范围内。
 * <p>
 * <h3>用户输入格式</h3>
 * 前端需提交 JSON：
 * <pre>
 * {"clicks":[{"x":120,"y":80},{"x":200,"y":150},{"x":300,"y":100}]}
 * </pre>
 * 或直接提交分号分隔的坐标字符串 "120,80;200,150;300,100"（向后兼容）。
 * 验证时依次比对每一对点击坐标与目标坐标，距离需小于等于容差半径
 * （{@code properties.getTolerance()}，点击验证码最小 20 像素）。
 */
public class ClickCaptcha extends AbstractCaptcha {

    /** 候选汉字池 */
    private static final String CHAR_POOL = "天地玄黄宇宙洪荒日月盈昃辰宿列张寒来暑往秋收冬藏" +
            "闰余成岁律吕调阳云腾致雨露结为霜金生丽水玉出昆冈" +
            "剑号巨阙珠称夜光果珍李柰菜重芥姜海咸河淡鳞潜羽翔" +
            "龙师火帝鸟官人皇始制文字乃服衣裳推位让国有虞陶唐";

    public ClickCaptcha() {
        super();
    }

    public ClickCaptcha(CaptchaProperties properties) {
        super(properties);
    }

    public ClickCaptcha(CaptchaStore store, CaptchaProperties properties) {
        super(store, properties);
    }

    @Override
    public String getType() {
        return "click";
    }

    @Override
    protected CaptchaResult doGenerate(CaptchaContext context) {
        CaptchaProperties p = context.getProperties();
        Random random = new Random();

        // 点击验证码画布尺寸由用户配置决定
        int width = p.getWidth();
        int height = p.getHeight();

        // 1. 创建背景
        BufferedImage bg = loadBackgroundImage(width, height);
        if (bg == null) {
            bg = createImage(width, height);
            Graphics2D g = bg.createGraphics();
            try {
                drawRandomBackground(g, width, height, random);
            } finally {
                g.dispose();
            }
        }

        // 2. 随机选择文字
        int targetCount = p.getClickTargetCount();
        int decoyCount = p.getClickDecoyCount();
        int totalCount = targetCount + decoyCount;
        List<Character> allChars = new ArrayList<>();
        List<Character> poolChars = new ArrayList<>();
        for (char c : CHAR_POOL.toCharArray()) {
            poolChars.add(c);
        }
        // 随机抽取不重复的文字
        java.util.Collections.shuffle(poolChars, random);
        for (int i = 0; i < totalCount && i < poolChars.size(); i++) {
            allChars.add(poolChars.get(i));
        }

        // 前 targetCount 个为需要点选的文字（答案）
        List<Character> targetChars = new ArrayList<>(allChars.subList(0, targetCount));

        // 3. 在画布上随机分布文字位置
        List<int[]> positions = generatePositions(width, height, totalCount, random);
        // 打乱文字与位置的对应关系
        java.util.Collections.shuffle(allChars, random);

        // 4. 绘制文字
        Graphics2D g = bg.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int fontSize = Math.max(18, height / 6);

            for (int i = 0; i < allChars.size() && i < positions.size(); i++) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    int[] pos = positions.get(i);
                    g2.setColor(randomColor(random));
                    int style = random.nextBoolean() ? Font.BOLD : Font.PLAIN;
                    g2.setFont(resolveFont(style, fontSize + random.nextInt(6) - 3, true));
                    double angle = random.nextInt(30) - 15;
                    g2.rotate(Math.toRadians(angle), pos[0], pos[1]);
                    g2.drawString(String.valueOf(allChars.get(i)), pos[0] - fontSize / 3, pos[1] + fontSize / 3);
                } finally {
                    g2.dispose();
                }
            }

            // 添加干扰线和噪点
            addInterfereLines(g, width, height, p.getEffectiveInterfereCount() / 2, random);
            addNoise(g, width, height, p.getEffectiveNoiseCount() / 2, random);
        } finally {
            g.dispose();
        }

        // 5. 应用水印
        applyWatermark(bg);

        // 6. 构建答案：存储目标文字的坐标位置 "x1,y1;x2,y2;x3,y3"（按提示顺序）
        // 建立文字到坐标的映射以便查找（allChars 已打乱，与 positions 一一对应）
        Map<Character, int[]> charToPos = new LinkedHashMap<>();
        for (int i = 0; i < allChars.size() && i < positions.size(); i++) {
            charToPos.put(allChars.get(i), positions.get(i));
        }
        StringBuilder answer = new StringBuilder();
        for (int i = 0; i < targetChars.size(); i++) {
            if (i > 0) answer.append(";");
            int[] pos = charToPos.get(targetChars.get(i));
            answer.append(pos[0]).append(",").append(pos[1]);
        }
        context.setAnswer(answer.toString());

        // 7. 构建提示
        StringBuilder prompt = new StringBuilder("请依次点击：");
        for (int i = 0; i < targetChars.size(); i++) {
            if (i > 0) prompt.append("、");
            prompt.append(targetChars.get(i));
        }

        CaptchaResult result = new CaptchaResult();
        result.setImageBase64(toBase64(bg));
        result.getExtra().put("prompt", prompt.toString());
        result.getExtra().put("clickCount", targetCount);
        result.getExtra().put("width", width);
        result.getExtra().put("height", height);

        // 安全考虑：不再返回文字位置列表（positions），前端无法获知文字坐标
        return result;
    }

    @Override
    protected boolean doVerify(String answer, String userInput) {
        if (answer == null || userInput == null) {
            return false;
        }

        // 解析存储的目标坐标："x1,y1;x2,y2;x3,y3"
        List<int[]> targets = parsePositions(answer.trim());
        if (targets.isEmpty()) {
            return false;
        }

        // 解析用户点击坐标
        List<int[]> clicks = parseUserClicks(userInput);
        if (clicks.size() != targets.size()) {
            return false;
        }

        // 逐一比对点击坐标与目标坐标的距离
        double tolerance = properties.getTolerance();
        // 点击验证码容差过小时，使用最小 20 像素
        if (tolerance < 20) {
            tolerance = 20;
        }

        for (int i = 0; i < targets.size(); i++) {
            int[] target = targets.get(i);
            int[] click = clicks.get(i);
            double dist = Math.sqrt(Math.pow(target[0] - click[0], 2) + Math.pow(target[1] - click[1], 2));
            if (dist > tolerance) {
                return false;
            }
        }
        return true;
    }

    /**
     * 将 "x1,y1;x2,y2;..." 解析为 [x, y] 坐标列表。
     *
     * @param s 坐标字符串
     * @return 坐标列表，每个元素为 [x, y]
     */
    private List<int[]> parsePositions(String s) {
        List<int[]> result = new ArrayList<>();
        for (String pair : s.split(";")) {
            String[] parts = pair.trim().split(",");
            if (parts.length == 2) {
                try {
                    result.add(new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())});
                } catch (NumberFormatException e) {
                    // 跳过无法解析的坐标
                }
            }
        }
        return result;
    }

    /**
     * 解析用户点击坐标，支持两种格式：
     * <ul>
     *     <li>JSON：{"clicks":[{"x":120,"y":80},...]}</li>
     *     <li>分号分隔字符串："x1,y1;x2,y2;..."</li>
     * </ul>
     *
     * @param userInput 用户输入
     * @return 点击坐标列表，每个元素为 [x, y]
     */
    private List<int[]> parseUserClicks(String userInput) {
        List<int[]> clicks = new ArrayList<>();
        String input = userInput.trim();

        // 尝试 JSON 格式
        if (input.contains("\"clicks\"") || input.contains("\"x\"")) {
            // 提取所有 {"x":NNN,"y":NNN} 对象
            Pattern p = Pattern.compile("\\{[^}]*\"x\"\\s*:\\s*(\\d+)[^}]*\"y\"\\s*:\\s*(\\d+)[^}]*\\}");
            Matcher m = p.matcher(input);
            while (m.find()) {
                clicks.add(new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))});
            }
            if (!clicks.isEmpty()) {
                return clicks;
            }
        }

        // 尝试 "x1,y1;x2,y2;..." 格式
        return parsePositions(input);
    }

    /**
     * 在画布上生成不重叠的随机位置。
     *
     * @param width     画布宽
     * @param height    画布高
     * @param count     位置数量
     * @param random    随机源
     * @return 位置列表，每个位置为 [x, y]
     */
    private List<int[]> generatePositions(int width, int height, int count, Random random) {
        List<int[]> positions = new ArrayList<>();
        int margin = 30;
        int minDistance = 50;
        int maxAttempts = 200;

        for (int i = 0; i < count; i++) {
            int x = 0, y = 0;
            boolean found = false;
            for (int attempt = 0; attempt < maxAttempts && !found; attempt++) {
                x = margin + random.nextInt(width - 2 * margin);
                y = margin + random.nextInt(height - 2 * margin);
                found = true;
                for (int[] pos : positions) {
                    if (Math.abs(pos[0] - x) < minDistance && Math.abs(pos[1] - y) < minDistance) {
                        found = false;
                        break;
                    }
                }
            }
            positions.add(new int[]{x, y});
        }

        return positions;
    }
}
