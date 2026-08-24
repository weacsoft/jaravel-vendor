package com.weacsoft.jaravel.vendor.captcha.generator;

import com.weacsoft.jaravel.vendor.captcha.CaptchaContext;
import com.weacsoft.jaravel.vendor.captcha.CaptchaProperties;
import com.weacsoft.jaravel.vendor.captcha.CaptchaResult;
import com.weacsoft.jaravel.vendor.captcha.store.CaptchaStore;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * 算术验证码：随机生成 {@code a op b = ?} 形式的算式，答案为运算结果。
 * <p>
 * 类型名 {@code "arithmetic"}。支持加、减、乘三种运算，减法保证结果非负，
 * 乘法限制操作数在 1~9 之间避免结果过大。验证时按字符串相等比对（去除空格）。
 */
public class ArithmeticCaptcha extends AbstractCaptcha {

    public ArithmeticCaptcha() {
        super();
    }

    public ArithmeticCaptcha(CaptchaProperties properties) {
        super(properties);
    }

    public ArithmeticCaptcha(CaptchaStore store, CaptchaProperties properties) {
        super(store, properties);
    }

    @Override
    public String getType() {
        return "arithmetic";
    }

    @Override
    protected CaptchaResult doGenerate(CaptchaContext context) {
        CaptchaProperties p = context.getProperties();
        Random random = new Random();

        int a = random.nextInt(20) + 1;
        int b = random.nextInt(20) + 1;
        char op;
        int result;
        int opType = random.nextInt(3);
        switch (opType) {
            case 0:
                op = '+';
                result = a + b;
                break;
            case 1:
                op = '-';
                if (a < b) {
                    int tmp = a;
                    a = b;
                    b = tmp;
                }
                result = a - b;
                break;
            default:
                op = 'x';
                a = random.nextInt(9) + 1;
                b = random.nextInt(9) + 1;
                result = a * b;
                break;
        }

        String expression = a + " " + op + " " + b + " = ?";
        context.setAnswer(String.valueOf(result));

        int width = p.getWidth();
        int height = p.getHeight();
        BufferedImage image = createImage(width, height);
        Graphics2D g = image.createGraphics();
        try {
            addNoise(g, width, height, p.getEffectiveNoiseCount(), random);
            addInterfereLines(g, width, height, p.getEffectiveInterfereCount(), random);
            if (p.isArcInterfere()) {
                addArcInterference(g, width, height, p.getEffectiveArcInterfereCount(), random);
            }
            drawText(g, expression, width, height, random);
        } finally {
            g.dispose();
        }

        // 应用水印
        applyWatermark(image);

        CaptchaResult resultObj = new CaptchaResult();
        resultObj.setImageBase64(toBase64(image));
        return resultObj;
    }

    @Override
    protected boolean doVerify(String answer, String userInput) {
        if (answer == null || userInput == null) {
            return false;
        }
        return answer.equals(userInput.trim());
    }
}
