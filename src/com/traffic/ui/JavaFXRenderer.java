package com.traffic.ui;

import com.traffic.core.IRenderer;
import com.traffic.core.Vehicle;
import com.traffic.core.Vector2D;
import com.traffic.map.Lane;
import com.traffic.map.TrafficLight;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Chế độ Graphic: vẽ xe bằng ảnh thật, xoay theo hướng,
 * hiệu ứng đèn nháy cho xe ưu tiên.
 *
 * Cùng implements IRenderer với BasicRenderer — đa hình:
 *   engine.setRenderer(new BasicRenderer(lanes));    // đổi sang Basic
 *   engine.setRenderer(new JavaFXRenderer(lanes));   // đổi sang Graphic
 * Không cần sửa bất kỳ dòng nào ở TrafficEngine.
 */
public class JavaFXRenderer extends JPanel implements IRenderer {

    private final List<Lane>                   lanes;
    private final List<Vehicle>                vehicles = new ArrayList<>();
    private final List<TrafficLight>           lights   = new ArrayList<>();
    private final Map<String, BufferedImage>   sprites  = new HashMap<>();

    // Hiệu ứng blink cho xe ưu tiên
    private boolean blinkOn   = true;
    private long    lastBlink = 0;

    // Màu fallback khi chưa có ảnh
    private static final Map<String, Color> FALLBACK_COLORS = Map.of(
        "car",        new Color(30,  120, 255),
        "motorcycle", new Color(255, 140, 0),
        "bicycle",    new Color(50,  200, 50),
        "ambulance",  Color.WHITE,
        "firetruck",  new Color(220, 30,  30)
    );

    public JavaFXRenderer(List<Lane> lanes) {
        this.lanes = lanes;
        setBackground(new Color(20, 20, 20)); // nền tối cho chế độ graphic
        setPreferredSize(new Dimension(800, 600));
        loadSprites();
    }

    // ── Load ảnh từ resources/images/ ────────────────────────────────────

    private void loadSprites() {
        String[] types = {"car", "motorcycle", "bicycle", "ambulance", "firetruck"};
        for (String type : types) {
            try {
                InputStream is = getClass().getResourceAsStream("/images/" + type + ".png");
                if (is != null) {
                    sprites.put(type, ImageIO.read(is));
                } else {
                    System.out.println("[Renderer] Chưa có ảnh: " + type + ".png — dùng màu fallback");
                }
            } catch (Exception e) {
                System.out.println("[Renderer] Lỗi load ảnh: " + type + " — " + e.getMessage());
            }
        }
    }

    // ── IRenderer ────────────────────────────────────────────────────────

    @Override
    public void clear() {
        vehicles.clear();
        lights.clear();
    }

    @Override
    public void renderVehicles(List<Vehicle> list) {
        vehicles.addAll(list);
    }

    @Override
    public void renderLights(List<TrafficLight> list) {
        lights.addAll(list);
    }

    // ── Swing paint ──────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        drawLanes(g2);
        drawLights(g2);
        drawVehicles(g2);
    }

    // ── Vẽ đường ─────────────────────────────────────────────────────────

    private void drawLanes(Graphics2D g2) {
        if (lanes == null) return;
        for (Lane lane : lanes) {
            List<Vector2D> pts = lane.getwaypoints();
            if (pts == null || pts.size() < 2) continue;

            // Mặt đường
            g2.setColor(new Color(50, 50, 50));
            g2.setStroke(new BasicStroke(42, BasicStroke.CAP_ROUND,
                                         BasicStroke.JOIN_ROUND));
            for (int i = 0; i < pts.size() - 1; i++) {
                g2.drawLine((int) pts.get(i).getX(), (int) pts.get(i).getY(),
                            (int) pts.get(i+1).getX(), (int) pts.get(i+1).getY());
            }

            // Vạch kẻ trắng mờ hơn
            float[] dash = {12f, 12f};
            g2.setColor(new Color(200, 200, 200, 160));
            g2.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT,
                    BasicStroke.JOIN_BEVEL, 0, dash, 0));
            for (int i = 0; i < pts.size() - 1; i++) {
                g2.drawLine((int) pts.get(i).getX(), (int) pts.get(i).getY(),
                            (int) pts.get(i+1).getX(), (int) pts.get(i+1).getY());
            }
        }
    }

    // ── Vẽ đèn ───────────────────────────────────────────────────────────

    private void drawLights(Graphics2D g2) {
        for (TrafficLight light : lights) {
            if (light == null) continue;
            int lx = (int) light.getPosition().getX();
            int ly = (int) light.getPosition().getY();

            int dx, dy;
            if (lx == 370 && ly == 280) { // Đèn làn ngang đi phải (Góc trên trái)
                dx = lx - 55;  dy = ly - 85;
            } else if (lx == 430 && ly == 320) { // Đèn làn ngang đi trái (Góc dưới phải)
                dx = lx + 5;   dy = ly + 18;
            } else if (lx == 380 && ly == 270) { // Đèn làn dọc đi xuống (Góc dưới trái)
                dx = lx - 80;  dy = ly - 15;
            } else if (lx == 420 && ly == 330) { // Đèn làn dọc đi lên (Góc trên phải)
                dx = lx + 48;  dy = ly - 70;
            } else {
                dx = lx;       dy = ly;
            }
            drawTrafficLight(g2, dx, dy, light);
        }
    }


    private void drawTrafficLight(Graphics2D g2, int x, int y, TrafficLight light) {
        // Cột đèn
        g2.setColor(new Color(40, 40, 40));
        g2.fillRoundRect(x, y, 30, 80, 8, 8);

        // Viền phát sáng theo trạng thái
        String color = light.getColor();
        Color glow = switch (color) {
            case "GREEN"  -> new Color(0,   255, 0,   60);
            case "YELLOW" -> new Color(255, 255, 0,   60);
            default       -> new Color(255, 0,   0,   60);
        };
        g2.setColor(glow);
        g2.fillRoundRect(x - 3, y - 3, 36, 86, 10, 10);

        // 3 bóng đèn
        drawBulb(g2, x + 5, y + 5,  color.equals("RED"),    Color.RED,    new Color(80,0,0));
        drawBulb(g2, x + 5, y + 30, color.equals("YELLOW"), Color.YELLOW, new Color(80,80,0));
        drawBulb(g2, x + 5, y + 55, color.equals("GREEN"),  Color.GREEN,  new Color(0,80,0));

        // Số giây — đa hình
        String display = light.getDisplay();
        if (!display.isEmpty()) {
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Arial", Font.BOLD, 13));
            g2.drawString(display, x + 35, y + 45);
        }
    }

    private void drawBulb(Graphics2D g2, int x, int y,
                           boolean on, Color onColor, Color offColor) {
        g2.setColor(on ? onColor : offColor);
        g2.fillOval(x, y, 20, 20);
        if (on) {
            // Hào quang phát sáng
            g2.setColor(new Color(onColor.getRed(), onColor.getGreen(),
                                  onColor.getBlue(), 60));
            g2.fillOval(x - 3, y - 3, 26, 26);
        }
    }

    // ── Vẽ xe ────────────────────────────────────────────────────────────

    private void drawVehicles(Graphics2D g2) {
        for (Vehicle v : vehicles) {
            if (v == null || v.getPosition() == null) continue;

            // Hiệu ứng blink cho xe ưu tiên — vẽ TRƯỚC thân xe
            if (v.isPriority()) drawBlinkEffect(g2, v);

            drawVehicle(g2, v);
        }
    }

    private void drawVehicle(Graphics2D g2, Vehicle v) {
        double w = v.getWidth(), h = v.getHeight();
        AffineTransform old = g2.getTransform();

        g2.translate(v.getPosition().getX(), v.getPosition().getY());
        g2.rotate(Math.toRadians(v.getAngle()));

        BufferedImage img = sprites.get(v.getTypeName());

        if (img != null) {
            // Vẽ ảnh thật — xoay theo góc đã translate ở trên
            g2.drawImage(img, (int)(-w/2), (int)(-h/2), (int)w, (int)h, null);
        } else {
            // Fallback: hình chữ nhật màu
            Color c = FALLBACK_COLORS.getOrDefault(v.getTypeName(), Color.GRAY);
            g2.setColor(c);
            g2.fillRect((int)(-w/2), (int)(-h/2), (int)w, (int)h);

            // Bánh xe
            g2.setColor(new Color(20, 20, 20));
            g2.fillRect((int)( w/2-5), (int)(-h/2-3), 4, 3);
            g2.fillRect((int)( w/2-5), (int)( h/2),   4, 3);
            g2.fillRect((int)(-w/2+1), (int)(-h/2-3), 4, 3);
            g2.fillRect((int)(-w/2+1), (int)( h/2),   4, 3);
        }

        // Tên xe nhỏ
        g2.setColor(new Color(255, 255, 255, 200));
        g2.setFont(new Font("SansSerif", Font.BOLD, 8));
        g2.drawString(v.getName().substring(0, Math.min(3, v.getName().length())),
                (int)(-w/2 + 2), 3);

        g2.setTransform(old);
    }

    /** Đèn nháy đỏ ↔ xanh cho xe cứu thương / cứu hỏa */
    private void drawBlinkEffect(Graphics2D g2, Vehicle v) {
        long now = System.currentTimeMillis();
        if (now - lastBlink > 300) {
            blinkOn   = !blinkOn;
            lastBlink = now;
        }
        Color blinkColor = blinkOn
                ? new Color(255, 0,   0,   100)   // đỏ
                : new Color(0,   100, 255, 100);   // xanh dương
        double w = v.getWidth(), h = v.getHeight();
        AffineTransform old = g2.getTransform();

        g2.translate(v.getPosition().getX(), v.getPosition().getY());
        g2.rotate(Math.toRadians(v.getAngle()));
        g2.setColor(blinkColor);
        g2.fillOval((int)(-w/2 - 5), (int)(-h/2 - 5),
                    (int)(w + 10),    (int)(h + 10));

        g2.setTransform(old);
    }
}
