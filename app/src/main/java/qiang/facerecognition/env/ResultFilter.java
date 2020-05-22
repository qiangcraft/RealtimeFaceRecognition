package qiang.facerecognition.env;

import android.util.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ResultFilter {
    /**
      * @ClassName:      ResultFilter
      * @Description:    对当前识别结果进行滤波处理
      * @Author:         JianboZhu
      * @CreateDate:     2019/8/26
      * @Update:
     */
    private float alpha = 0.5f;
    private float beta = 0.8f;
    private Map<String,Float> labelMap= new HashMap<String,Float>();

    public ResultFilter() {}

    public ResultFilter(float alpha, float beta) {
        this.alpha = alpha;
        this.beta = beta;
    }

    public Pair<String,Float> filter(String label, float prob){
        /**
         * @description:   对label对应的置信度进行更新，
         *                 对HashMap中的值进行降序排序，
         *                 返回置信度最高的一对key-value。
         * @param:         [label, prob]
         * @return:        android.util.Pair<java.lang.String,java.lang.Float>
         * @author:        JianboZhu
         * @date:          2019/8/26
         * @update:
         */
        if(labelMap.containsKey(label)){
            float value = ((1-alpha)*prob + alpha*labelMap.get(label)) / beta;
            value = value>1?1:value;
            labelMap.put(label,value);
        }else {
            labelMap.put(label,prob);
        }

        for(Map.Entry<String,Float> mapping:labelMap.entrySet()){
            String key = mapping.getKey();
            if(!label.equals(key)){
                float value = labelMap.get(key) * beta;
                labelMap.put(key, value);
            }
        }

        List<Map.Entry<String,Float>> list = sort();

//        int i=0;
//        for(Map.Entry<String,Float> mapping:list){
//            Log.d("FilterLabel",i+" "+mapping.getKey()+":"+mapping.getValue());
//        }

        return new Pair<>(list.get(0).getKey(),list.get(0).getValue());
    }

    private List<Map.Entry<String,Float>> sort(){
        List<Map.Entry<String,Float>> list = new ArrayList<Map.Entry<String,Float>>(labelMap.entrySet());
        Collections.sort(list,new Comparator<Map.Entry<String,Float>>() {
            //降序排序
            public int compare(Map.Entry<String, Float> o1,
                               Map.Entry<String, Float> o2) {
                return o2.getValue().compareTo(o1.getValue());
            }

        });
        return list;
    }



}
