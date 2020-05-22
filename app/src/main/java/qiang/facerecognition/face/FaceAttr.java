package qiang.facerecognition.face;

import android.graphics.RectF;

public class FaceAttr {
    /**
      * @ClassName:      FaceAttr
      * @Description:    表示检测到的人脸的 概率、关键点、坐标
      * @Author:         JianboZhu
      * @CreateDate:     2019/7/19
      * @Update:
     */
    private float prob;
    private float[] landmark;   //  [0:5] Y坐标  [5:10] X坐标
    private RectF bbox;

    public FaceAttr() { }

    public FaceAttr(float prob, RectF bbox) {
        this.prob = prob;
        this.bbox = bbox;
    }

    public FaceAttr(float prob, float[] landmark, RectF bbox){
        this.prob = prob;
        this.landmark = landmark;
        this.bbox = bbox;
    }

    public float getProb() {
        return prob;
    }

    public void setProb(float prob) {
        this.prob = prob;
    }

    public float[] getLandmark() {
        return landmark;
    }

    public void setLandmark(float[] landmark) {
        this.landmark = landmark;
    }

    public RectF getBbox() {
        return bbox;
    }

    public void setBbox(RectF bbox) {
        this.bbox = bbox;
    }
}
