package com.traffic.ui;
import com.traffic.core.Vector2D;
import com.traffic.map.Lane;
import com.traffic.map.TrafficLight;
import javax.swing.JPanel;
import java.awt.*;
import java.util.List;

public class MapRenderer extends JPanel {
    private List<Lane> lanes; 
    private TrafficLight smartLight;
    
    public MapRenderer(List<Lane> lanes, TrafficLight smartLight) {
        this.lanes = lanes;
        this.smartLight = smartLight;
        this.setBackground(new Color(34, 139, 34)); 
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 1. Vẽ tất cả các làn đường
        for (Lane lane : lanes) {
            drawLane(g2d, lane);
        }

        // 2. Vẽ đèn giao thông tại vị trí cố định (khúc cua)
        if (smartLight != null) {
            drawTrafficLight(g2d, 310, 210, smartLight);
        }
    }

    private void drawLane(Graphics2D g2d, Lane lane) {
        List<Vector2D> points = lane.getwaypoints();
        if (points.size() < 2) return;

        // Vẽ mặt đường
        g2d.setColor(new Color(60, 60, 60));
        g2d.setStroke(new BasicStroke(40, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < points.size() - 1; i++) {
            Vector2D p1 = points.get(i);
            Vector2D p2 = points.get(i + 1);
            g2d.drawLine((int)p1.getX(), (int)p1.getY(), (int)p2.getX(), (int)p2.getY());
        }

        // Vẽ vạch kẻ đường
        g2d.setColor(Color.WHITE);
        float[] dash = {10f, 10f};
        g2d.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, dash, 0));
        for (int i = 0; i < points.size() - 1; i++) {
            Vector2D p1 = points.get(i);
            Vector2D p2 = points.get(i + 1);
            g2d.drawLine((int)p1.getX(), (int)p1.getY(), (int)p2.getX(), (int)p2.getY());
        }
    }

    private void drawTrafficLight(Graphics2D g2d, int x, int y, TrafficLight light) {
        g2d.setColor(Color.BLACK);
        g2d.fillRect(x, y, 30, 80);

        // Đèn Đỏ
        g2d.setColor(light.getColor().equalsIgnoreCase("Red") ? Color.RED : Color.DARK_GRAY);
        g2d.fillOval(x + 5, y + 5, 20, 20);
        // Đèn Vàng
        g2d.setColor(light.getColor().equalsIgnoreCase("Yellow") ? Color.YELLOW : Color.DARK_GRAY);
        g2d.fillOval(x + 5, y + 30, 20, 20);
        // Đèn Xanh
        g2d.setColor(light.getColor().equalsIgnoreCase("Green") ? Color.GREEN : Color.DARK_GRAY);
        g2d.fillOval(x + 5, y + 55, 20, 20);

        // Hiển thị số giây
        String displayValue = light.getDisplay(); 
        if (!displayValue.isEmpty() && !displayValue.equalsIgnoreCase(light.getColor())) {
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 14));
            g2d.drawString(displayValue, x + 35, y + 45);
        }
    }
}