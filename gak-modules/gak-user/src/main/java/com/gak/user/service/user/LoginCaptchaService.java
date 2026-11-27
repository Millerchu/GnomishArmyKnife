package com.gak.user.service.user;

import com.gak.framework.exception.BusinessException;
import com.gak.framework.security.AttemptLimiter;
import jakarta.servlet.http.HttpSession;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.web.util.WebUtils;

/** 签发短期图形验证码，同一会话一次只可使用一张。 */
@Service
public class LoginCaptchaService {
    private static final String SESSION_KEY = LoginCaptchaService.class.getName();
    private static final String CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 4;
    private static final int WIDTH = 130;
    private static final int HEIGHT = 42;
    private final AttemptLimiter issuance = new AttemptLimiter(120, Duration.ofMinutes(1));
    private final SecureRandom random = new SecureRandom();

    public String issue(HttpSession session, String address) {
        issuance.acquire(address);
        StringBuilder answer = new StringBuilder(LENGTH);
        for (int index = 0; index < LENGTH; index++) {
            answer.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        String image = render(answer.toString());
        synchronized (WebUtils.getSessionMutex(session)) {
            session.setAttribute(SESSION_KEY, new LoginCaptcha(answer.toString(), Instant.now().plusSeconds(180)));
        }
        return image;
    }

    public void verify(HttpSession session, String answer) {
        LoginCaptcha challenge;
        synchronized (WebUtils.getSessionMutex(session)) {
            challenge = (LoginCaptcha) session.getAttribute(SESSION_KEY);
            session.removeAttribute(SESSION_KEY);
        }
        if (challenge == null || !Instant.now().isBefore(challenge.expiresAt())
                || !challenge.answer().equalsIgnoreCase(answer)) {
            throw new BusinessException("CAPTCHA_INVALID", "验证码错误或已过期");
        }
    }

    private String render(String answer) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(244, 248, 252));
            graphics.fillRect(0, 0, WIDTH, HEIGHT);
            graphics.setColor(new Color(169, 187, 204));
            for (int line = 0; line < 10; line++) {
                graphics.drawLine(random.nextInt(WIDTH), random.nextInt(HEIGHT),
                        random.nextInt(WIDTH), random.nextInt(HEIGHT));
            }
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 27));
            graphics.setColor(new Color(24, 47, 73));
            for (int index = 0; index < answer.length(); index++) {
                graphics.drawString(answer.substring(index, index + 1), 13 + 28 * index, 27 + random.nextInt(7));
            }
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", output);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
    }
}
