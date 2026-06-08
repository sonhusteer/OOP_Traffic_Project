package com.traffic.model.map;

public class RoadEdge {
    public enum RoadType { AVENUE, STREET, ALLEY } // 3 Loại đường

    private IntersectionNode startNode;
    private IntersectionNode endNode;
    
    private RoadType type;
    private double width;
    private double speedLimit;
    private int lanesPerDirection;

    // Constructor mới
    public RoadEdge(IntersectionNode startNode, IntersectionNode endNode, RoadType type) {
        this.startNode = startNode;
        this.endNode = endNode;
        this.type = type;

        switch (type) {
            case AVENUE: this.width = 120; this.speedLimit = 5.0; this.lanesPerDirection = 2; break; // Đại lộ to
            case STREET: this.width = 80;  this.speedLimit = 3.0; this.lanesPerDirection = 1; break; // Phố vừa
            case ALLEY:  this.width = 50;  this.speedLimit = 1.5; this.lanesPerDirection = 0; break; // Ngõ hẹp
        }
    }

    // Constructor dự phòng (mặc định là Phố)
    public RoadEdge(IntersectionNode startNode, IntersectionNode endNode) {
        this(startNode, endNode, RoadType.STREET);
    }

    public IntersectionNode getStartNode() { return startNode; }
    public IntersectionNode getEndNode() { return endNode; }
    public RoadType getType() { return type; }
    public double getWidth() { return width; }
    public double getSpeedLimit() { return speedLimit; }
    public int getLanesPerDirection() { return lanesPerDirection; }
}