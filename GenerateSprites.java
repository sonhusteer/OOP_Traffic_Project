import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Script tạo sprite PNG đơn giản cho mỗi loại xe.
 * Chạy: javac GenerateSprites.java && java GenerateSprites
 */
public class GenerateSprites {

    public static void main(String[] args) throws Exception {
        new File("bin/images").mkdirs();

        drawCar("bin/images/car.png",        60, 28, new Color(30, 120, 255),  "CAR");
        drawCar("bin/images/motorcycle.png",  40, 20, new Color(255, 140, 0),   "MTR");
        drawCar("bin/images/bicycle.png",     36, 18, new Color(50, 200, 50),   "BCY");
        drawAmbulance("bin/images/ambulance.png");
        drawFiretruck("bin/images/firetruck.png");

        System.out.println("✅ Sprites đã tạo trong bin/images/");
    }

    static void drawCar(String path, int w, int h, Color body, String label) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Thân xe
        g.setColor(body);
        g.fill(new RoundRectangle2D.Float(0, 0, w, h, 6, 6));

        // Kính chắn gió (bên phải = đầu xe theo hướng đi)
        g.setColor(new Color(180, 220, 255, 200));
        g.fillRoundRect(w - 14, 3, 10, h - 6, 3, 3);

        // Đèn pha
        g.setColor(new Color(255, 255, 180));
        g.fillOval(w - 5, 4, 4, 4);
        g.fillOval(w - 5, h - 8, 4, 4);

        // Bánh xe
        g.setColor(new Color(25, 25, 25));
        int wr = 5, wh = 4;
        g.fillRoundRect(4,      -2,     wr, wh, 2, 2);
        g.fillRoundRect(4,      h - 2,  wr, wh, 2, 2);
        g.fillRoundRect(w - 10, -2,     wr, wh, 2, 2);
        g.fillRoundRect(w - 10, h - 2,  wr, wh, 2, 2);

        // Nhãn
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 8));
        g.drawString(label, 3, h / 2 + 3);

        g.dispose();
        ImageIO.write(img, "png", new File(path));
    }

    static void drawAmbulance(String path) throws Exception {
        int w = 70, h = 28;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Thân trắng
        g.setColor(Color.WHITE);
        g.fill(new RoundRectangle2D.Float(0, 0, w, h, 6, 6));

        // Sọc đỏ
        g.setColor(new Color(200, 30, 30));
        g.fillRect(0, h/2 - 3, w, 6);

        // Dấu thập đỏ
        g.setColor(new Color(200, 30, 30));
        g.fillRect(8, 6, 10, 3);
        g.fillRect(11, 3, 4, 9);

        // Đèn xanh trên nóc
        g.setColor(new Color(0, 180, 255, 220));
        g.fillOval(w - 16, 2, 10, 6);

        // Đèn đỏ trên nóc
        g.setColor(new Color(255, 50, 50, 220));
        g.fillOval(w - 16, h - 8, 10, 6);

        // Bánh xe
        g.setColor(new Color(25, 25, 25));
        g.fillRoundRect(4, -2, 6, 5, 2, 2);
        g.fillRoundRect(4, h - 3, 6, 5, 2, 2);
        g.fillRoundRect(w - 12, -2, 6, 5, 2, 2);
        g.fillRoundRect(w - 12, h - 3, 6, 5, 2, 2);

        g.dispose();
        ImageIO.write(img, "png", new File(path));
    }

    static void drawFiretruck(String path) throws Exception {
        int w = 80, h = 30;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Thân đỏ
        g.setColor(new Color(200, 30, 30));
        g.fill(new RoundRectangle2D.Float(0, 0, w, h, 6, 6));

        // Cabin trước (sáng hơn)
        g.setColor(new Color(220, 60, 60));
        g.fillRoundRect(w - 22, 0, 22, h, 6, 6);

        // Kính cabin
        g.setColor(new Color(180, 220, 255, 180));
        g.fillRoundRect(w - 18, 4, 12, h - 8, 3, 3);

        // Đèn vàng cảnh báo trên nóc
        g.setColor(new Color(255, 200, 0, 230));
        g.fillOval(w - 20, 1, 8, 5);

        // Bánh xe (xe tải to hơn)
        g.setColor(new Color(25, 25, 25));
        g.fillRoundRect(4,      -3, 8, 6, 2, 2);
        g.fillRoundRect(4,      h - 3, 8, 6, 2, 2);
        g.fillRoundRect(w - 16, -3, 8, 6, 2, 2);
        g.fillRoundRect(w - 16, h - 3, 8, 6, 2, 2);

        // Ống phun nước
        g.setColor(new Color(150, 150, 150));
        g.fillRect(0, h/2 - 2, 20, 4);
        g.setColor(new Color(100, 100, 100));
        g.fillOval(-4, h/2 - 4, 8, 8);

        g.dispose();
        ImageIO.write(img, "png", new File(path));
    }
}
