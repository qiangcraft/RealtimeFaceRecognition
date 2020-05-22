package qiang.facerecognition.face;

import android.graphics.RectF;
import android.util.Pair;


public class FaceInfo {
/**
  * @ClassName:      FaceInfo
  * @Description:    表示人脸的 坐标、姓名、表情 信息
  * @Author:         JianboZhu
  * @CreateDate:     2019/7/19
  * @Update:
 */
    private Pair<RectF,Float> bbox;
    private Pair<String,Float> person = new Pair<>("",0f);

    public FaceInfo() {}
    public FaceInfo(Pair<RectF,Float> bbox) {
        this.bbox = bbox;
    }
    public FaceInfo(Pair<RectF,Float> bbox, Pair<String, Float> person) {
        this.bbox = bbox;
        this.person = person;
    }

    public Pair<RectF,Float> getBbox() {
        return bbox;
    }

    public void setBbox(Pair<RectF,Float> bbox) {
        this.bbox = bbox;
    }

    public Pair<String, Float> getPerson() {
        return person;
    }

    public void setPerson(Pair<String, Float> person) {
        this.person = person;
    }

}
