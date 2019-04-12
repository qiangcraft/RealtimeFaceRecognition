package pp.facerecognizer.faceCompare;
import android.util.Log;
import android.util.Pair;

import java.io.FileNotFoundException;
import java.util.ArrayList;

import pp.facerecognizer.env.FileUtils;

import static android.content.ContentValues.TAG;
import static pp.facerecognizer.env.FileUtils.EMBEDDING_SIZE;

/**
 * Created by caydencui on 2018/9/6.
 * 人脸特征(512维特征值)
 * 相似度取特征向量之间的欧式距离.
 */
public class FaceFeature {
    public static final int DIMS=512;
    public static final double THRESHOLD = 1.00; //若小于compare结果小于此阈值则认为是同一个人

    //比较当前特征和另一个特征之间的相似度
    public static double compare(float fea1[], float fea2[]){
        double dist=0;
        for (int i=0;i<DIMS;i++)
            dist+=(fea1[i]-fea2[i])*(fea1[i]-fea2[i]);
        dist= Math.sqrt(dist);
        return dist;
    }

    //在熟人数据库中搜索，计算两个人脸的FaceFeature之间的欧式距离，若小于1.1则认为寻找成功。
    public static Pair<Integer,Double> search(float[] embbedingsArray, String FaceDBpath) throws FileNotFoundException {
        ArrayList<String> faceDbList;
        Pair<Integer, Double>  pair;
        faceDbList = FileUtils.readFileByLine(FaceDBpath);
        int label = -1; // return unknown
        double result = 1000;//初始化计算结果
        int p=1;//用于查看计算结果计数

        int labelResult = label;//寻找最小结果对应的label
        double minCmpResult = result; //用于寻找最小计算结果

        for(String faceStr: faceDbList){
//            Log.d(TAG, "search faceStr: "+faceStr);
            int i = 0;
            float[] faceArray = new float[EMBEDDING_SIZE];
            ArrayList<String> embeddingList = new ArrayList<>();
            embeddingList.clear();
            for(String tmp: faceStr.split(" ")){
                if(i == 0) {
                    label = Integer.parseInt(tmp); //第一个数字为label,获取label
                    i++;
                } else{
                    embeddingList.add(tmp);
//                    Log.d(TAG, "search tmp: "+tmp);
                }
            }
            for(String embeddingStr:embeddingList){
                int k = 0; // embeddingStr 如 0:0.014449
                int index=0;
                float embeddingNum = 0;
                for(String tmp:embeddingStr.split(":")){
                    if(k == 0) {
                        index = Integer.parseInt(tmp);
//                        Log.d(TAG, "embedding index: "+index);
                        k++;
                    }else{
                         embeddingNum= Float.parseFloat(tmp);
//                         Log.d(TAG, "embedding num: "+embeddingNum);
                         break;
                    }
                }
                faceArray[index] = embeddingNum;
            }
//            Log.d(TAG, "search faceArray: "+faceArray.length);

            result = compare(faceArray, embbedingsArray);
            Log.d(TAG, "search Result"+p+":"+result);
            p++;
            if(result < THRESHOLD){
                if(result < minCmpResult) {
                    minCmpResult = result;
                    labelResult = label;
                }
            }
        }
        pair = new Pair<>(labelResult, minCmpResult);
        return pair;
    }
}
