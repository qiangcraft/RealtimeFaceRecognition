package qiang.facerecognition.env;

import android.graphics.RectF;

public class ObjectInfo {
    private String label;
    private float score;
    private RectF bbox;
    private float depth;

    public ObjectInfo() {}

    public ObjectInfo(String label, float score, RectF bbox) {
        this.label = label;
        this.score = score;
        this.bbox = bbox;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public float getScore() {
        return score;
    }

    public void setScore(float score) {
        this.score = score;
    }

    public RectF getBbox() {
        return bbox;
    }

    public void setBbox(RectF bbox) {
        this.bbox = bbox;
    }

    public float getDepth() {
        return depth;
    }

    public void setDepth(float depth) {
        this.depth = depth;
    }
}
