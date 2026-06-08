package com.traffic.config;

public class Constants {
    // Kích thước cửa sổ
    public static final double WINDOW_WIDTH = 1280;
    public static final double WINDOW_HEIGHT = 800;
    
    // Cấu hình đường sá
    public static final double LANE_WIDTH = 30;
    public static final double ROAD_WIDTH = LANE_WIDTH * 4; // 120px tổng
    public static final double ROUNDABOUT_RADIUS = 100;     // bán kính nền nhựa bùng binh

    // THÊM CÔNG TẮC THỜI TIẾT
    public static boolean IS_RAINING = false;
    
    // Công tắc bật tắt đèn giao thông tự động
    public static boolean AUTO_LIGHTS = true;
    
    // CẤU HÌNH THỜI GIAN VÀ ĐÈN
    public static int TIME_MODE = 0; // 0: Chu kỳ tự động, 1: Luôn Ban Ngày, 2: Luôn Ban Đêm
    
    // Bật/tắt đồ họa (Nhà cửa, cây cối, đèn đường...)
    public static boolean BASIC_MODE = false;
}