package com.weacsoft.jaravel.vendor.captcha;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * 图片数字验证码：随机字母 + 数字字符串。
 * <p>
 * 类型名 {@code "number"}。在 {@link AbstractCaptcha} 提供的图像工具之上绘制
 * 带噪点、干扰线、随机旋转的字符图片。验证时按 {@link CaptchaProperties#isCaseSensitive()}
 * 决定是否区分大小写。
 */
public class NumberCaptcha extends AbstractCaptcha {

    public NumberCaptcha() {
        super();
    }

    public NumberCaptcha(CaptchaStore store, CaptchaProperties properties) {
        super(store, properties);
    }

    @Override
    public String getType() {
        return "number";
    }

    @Override
    protected CaptchaResult doGenerate(CaptchaContext context) {
        CaptchaProperties p = context.getProperties();
        Random random = new Random();

        String answer = randomString(p.getLength(), random);
        context.setAnswer(answer);

        int width = p.getWidth();
        int height = p.getHeight();
        BufferedImage image = createImage(width, height);
        Graphics2D g = image.createGraphics();
        try {
            addNoise(g, width, height, p.getNoiseCount(), random);
            addInterfereLines(g, width, height, p.getInterfereCount(), random);
            drawText(g, answer, width, height, random);
        } finally {
            g.dispose();
        }

        CaptchaResult result = new CaptchaResult();
        result.setImageBase64(toBase64(image));
        return result;
    }

    @Override
    protected boolean doVerify(String answer, String userInput) {
        if (answer == null || userInput == null) {
            return false;
        }
        String input = userInput.trim();
        if (properties.isCaseSensitive()) {
            return answer.equals(input);
        }
        return answer.equalsIgnoreCase(input);
    }
}
