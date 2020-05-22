package qiang.facerecognition.face;
import android.util.Pair;

import java.io.FileNotFoundException;
import java.util.ArrayList;

import qiang.facerecognition.utils.FileUtils;

/**
 * Created by caydencui on 2018/9/6.
 * 人脸特征(512维特征值)
 * 相似度取特征向量之间的欧式距离.
 */
public class FaceFeature {
    public static final int DIMS=512;
    public static final float THRESHOLD = (float) 1.00; //若小于compare结果小于此阈值则认为是同一个人

    //比较当前特征和另一个特征之间的相似度
    public static float compare(float fea1[], float fea2[]){
        float dist=0;
        for (int i=0;i<DIMS;i++)
            dist+=(fea1[i]-fea2[i])*(fea1[i]-fea2[i]);
        dist= (float) Math.sqrt(dist);
        return dist;
    }

    //在熟人数据库中搜索，计算两个人脸的FaceFeature之间的欧式距离，若小于1.1则认为寻找成功。
    public static Pair<Integer, Float> search(float[] embbedingsArray, String FaceDBpath) throws FileNotFoundException {
        ArrayList<String> faceDbList = null;
        Pair<Integer, Float>  pair;
        try {
            faceDbList = FileUtils.readFileByLine(FaceDBpath);
        }catch (Exception e){
            e.printStackTrace();
        }
        int label = -1; // return unknown
        float result = 100;//初始化计算结果

        int labelResult = label;//寻找最小结果对应的label
        float minCmpResult = result; //用于寻找最小计算结果

        if(faceDbList!=null && !faceDbList.isEmpty()){
            int index;
            String[] embbeddings;
            float[] faceArray = new float[FileUtils.EMBEDDING_SIZE];

            for (String faceStr : faceDbList) {
                embbeddings = faceStr.split(" ");
                label++;
                index = 0;
                for(String embbedding : embbeddings){
                    faceArray[index++] = Float.parseFloat(embbedding);
                }
                result = compare(embbedingsArray, faceArray);

                if (result < THRESHOLD) {
                    if (result < minCmpResult) {
                        minCmpResult = result;
                        labelResult = label;
                    }
                }
            }
        }
        pair = new Pair<>(labelResult, minCmpResult);

        return pair;
    }
}
