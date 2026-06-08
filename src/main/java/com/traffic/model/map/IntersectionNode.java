package com.traffic.model.map;

import com.traffic.model.traffic.TrafficLight;
import com.traffic.model.traffic.TrafficLight.Phase;

public class IntersectionNode {
    public enum NodeType  { THREE_WAY, FOUR_WAY, FIVE_WAY }
    public enum LightMode { NORMAL, COUNTDOWN, SMART_COUNTDOWN }

    private String id;
    private double x, y;
    private NodeType  type;
    private LightMode lightMode  = LightMode.SMART_COUNTDOWN;
    private boolean   isSpawnNode = false;

    // 4 đèn cardinal + 1 đèn NW (chỉ dùng cho FIVE_WAY)
    private TrafficLight lightNorth = new TrafficLight(Phase.GREEN);
    private TrafficLight lightSouth = new TrafficLight(Phase.GREEN);
    private TrafficLight lightEast  = new TrafficLight(Phase.RED);
    private TrafficLight lightWest  = new TrafficLight(Phase.RED);
    private TrafficLight lightNW    = new TrafficLight(Phase.RED);

    // Các hướng có đường thực sự kết nối (được set bởi CityMap sau khi build)
    private boolean hasNorth, hasSouth, hasEast, hasWest, hasNW;

    private double phaseTimer  = 0;
    private int    currentPhase = 0;

    private static final double GREEN_DURATION  = 15.0;
    private static final double YELLOW_DURATION = 3.0;

    // -------- Constructors --------

    public IntersectionNode(String id, double x, double y, NodeType type) {
        this.id = id; this.x = x; this.y = y; this.type = type;
        applyPhaseStates();
    }

    public IntersectionNode(String id, double x, double y, NodeType type, boolean isSpawnNode) {
        this(id, x, y, type);
        this.isSpawnNode = isSpawnNode;
    }

    // -------- Direction setup (gọi bởi CityMap) --------

    public void setConnectedDirections(boolean n, boolean s, boolean e, boolean w, boolean nw) {
        hasNorth = n; hasSouth = s; hasEast = e; hasWest = w; hasNW = nw;
        phaseTimer   = 0;
        currentPhase = 0;
        applyPhaseStates();
    }

    // -------- Light update --------

    public void updateLights() {
        if (!com.traffic.config.Constants.AUTO_LIGHTS) return;

        double dt = 1.0 / 60.0;
        phaseTimer += dt;

        if (type == NodeType.FIVE_WAY) {
            // FIVE_WAY: 5 pha xoay vòng, mỗi pha = GREEN_DURATION
            if (phaseTimer >= GREEN_DURATION) {
                phaseTimer   = 0;
                currentPhase = (currentPhase + 1) % 5;
                applyPhaseStates();
            }
        } else {
            // THREE_WAY / FOUR_WAY: 4 pha (green/yellow x2)
            double duration = (currentPhase % 2 == 0) ? GREEN_DURATION : YELLOW_DURATION;
            if (phaseTimer >= duration) {
                phaseTimer   = 0;
                currentPhase = (currentPhase + 1) % 4;
                applyPhaseStates();
            }
        }
    }

    public void manualToggle() {
        int maxPhases = (type == NodeType.FIVE_WAY) ? 5 : 4;
        currentPhase = (currentPhase + 1) % maxPhases;
        phaseTimer   = 0;
        applyPhaseStates();
    }

    public double getRemainingTime() {
        if (type == NodeType.FIVE_WAY) return GREEN_DURATION - phaseTimer;
        double duration = (currentPhase % 2 == 0) ? GREEN_DURATION : YELLOW_DURATION;
        return duration - phaseTimer;
    }

    // -------- Phase state machines --------

    private void applyPhaseStates() {
        switch (type) {
            case FIVE_WAY  -> applyFiveWayPhase();
            case THREE_WAY -> applyThreeWayPhase();
            default        -> applyFourWayPhase();
        }
    }

    private void applyFiveWayPhase() {
        // Mỗi pha: 1 cánh được GREEN, 4 cánh còn lại RED
        // Thứ tự: 0=West, 1=East, 2=North, 3=South, 4=NW
        lightWest .setPhase(currentPhase == 0 ? Phase.GREEN : Phase.RED);
        lightEast .setPhase(currentPhase == 1 ? Phase.GREEN : Phase.RED);
        lightNorth.setPhase(currentPhase == 2 ? Phase.GREEN : Phase.RED);
        lightSouth.setPhase(currentPhase == 3 ? Phase.GREEN : Phase.RED);
        lightNW   .setPhase(currentPhase == 4 ? Phase.GREEN : Phase.RED);
    }

    private void applyThreeWayPhase() {
        // Pha 0/1: E+W green/yellow (luồng ngang)
        // Pha 2/3: N or S green/yellow (cánh dọc còn lại)
        boolean vertArm = hasNorth || hasSouth;
        switch (currentPhase) {
            case 0 -> {
                lightWest .setPhase(hasWest  ? Phase.GREEN  : Phase.RED);
                lightEast .setPhase(hasEast  ? Phase.GREEN  : Phase.RED);
                lightNorth.setPhase(Phase.RED);
                lightSouth.setPhase(Phase.RED);
            }
            case 1 -> {
                lightWest .setPhase(hasWest  ? Phase.YELLOW : Phase.RED);
                lightEast .setPhase(hasEast  ? Phase.YELLOW : Phase.RED);
                lightNorth.setPhase(Phase.RED);
                lightSouth.setPhase(Phase.RED);
            }
            case 2 -> {
                lightWest .setPhase(Phase.RED);
                lightEast .setPhase(Phase.RED);
                lightNorth.setPhase(hasNorth && vertArm ? Phase.GREEN  : Phase.RED);
                lightSouth.setPhase(hasSouth && vertArm ? Phase.GREEN  : Phase.RED);
            }
            case 3 -> {
                lightWest .setPhase(Phase.RED);
                lightEast .setPhase(Phase.RED);
                lightNorth.setPhase(hasNorth && vertArm ? Phase.YELLOW : Phase.RED);
                lightSouth.setPhase(hasSouth && vertArm ? Phase.YELLOW : Phase.RED);
            }
        }
    }

    private void applyFourWayPhase() {
        switch (currentPhase) {
            case 0 -> { lightNorth.setPhase(Phase.GREEN);  lightSouth.setPhase(Phase.GREEN);
                        lightEast .setPhase(Phase.RED);    lightWest .setPhase(Phase.RED);   }
            case 1 -> { lightNorth.setPhase(Phase.YELLOW); lightSouth.setPhase(Phase.YELLOW);
                        lightEast .setPhase(Phase.RED);    lightWest .setPhase(Phase.RED);   }
            case 2 -> { lightNorth.setPhase(Phase.RED);    lightSouth.setPhase(Phase.RED);
                        lightEast .setPhase(Phase.GREEN);  lightWest .setPhase(Phase.GREEN); }
            case 3 -> { lightNorth.setPhase(Phase.RED);    lightSouth.setPhase(Phase.RED);
                        lightEast .setPhase(Phase.YELLOW); lightWest .setPhase(Phase.YELLOW);}
        }
    }

    // -------- Getters --------

    public boolean isSpawnNode()    { return isSpawnNode; }
    public boolean isHasNorth()     { return hasNorth; }
    public boolean isHasSouth()     { return hasSouth; }
    public boolean isHasEast()      { return hasEast; }
    public boolean isHasWest()      { return hasWest; }
    public boolean isHasNW()        { return hasNW; }
    public TrafficLight getLightNorth() { return lightNorth; }
    public TrafficLight getLightSouth() { return lightSouth; }
    public TrafficLight getLightEast()  { return lightEast; }
    public TrafficLight getLightWest()  { return lightWest; }
    public TrafficLight getLightNW()    { return lightNW; }
    public String    getId()        { return id; }
    public double    getX()         { return x; }
    public double    getY()         { return y; }
    public NodeType  getType()      { return type; }
    public LightMode getLightMode() { return lightMode; }
    public void setLightMode(LightMode m) { this.lightMode = m; }
    public int getCurrentPhase()    { return currentPhase; }
}
