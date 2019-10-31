package qiang.facerecognition.faceCompare;

import android.graphics.RectF;

/*
* MTCNN检测到的人脸包含的信息，probability,landmarks,rectF
* 分别代表：是人脸的概率，人脸关键点坐标
* A json format example:
* [ {
  "bbox" : { "x" : 331, "y" : 92, "w" : 58, "h" : 71 }, "confidence" : 0.9999871253967285,
  "landmarks" : [ {
    "type" : "LEFT_EYE", "position" : { "x" : 346, "y" : 120 } }, {
    "type" : "RIGHT_EYE", "position" : { "x" : 374, "y" : 119 } }, {
    "type" : "NOSE", "position" : { "x" : 359, "y" : 133 } }, {
    "type" : "MOUTH_LEFT", "position" : { "x" : 347, "y" : 147 } }, {
    "type" : "MOUTH_RIGHT", "position" : { "x" : 371, "y" : 147 },
  } ]
* */
public class DetectedFace {
    private float probability;
    private float[] landmarks;
    private RectF rectF;

    public DetectedFace(float probability, float[] landmarks, RectF rectF) {
        this.probability = probability;
        this.landmarks = landmarks;
        this.rectF = rectF;
    }

    public float getProbability() {
        return probability;
    }

    public float[] getLandmarks() {
        return landmarks;
    }

    public RectF getRectF() {
        return rectF;
    }
}
