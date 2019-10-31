package qiang.facerecognition.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * 修改文件
 */
public class FileModify {

    /**
     * 读取文件内容
     *
     * @param filePath
     * @return
     */
    public String read(String filePath, int index) {
        BufferedReader br = null;
        String line = null;
        StringBuffer buf = new StringBuffer();
        int cnt = 0; //计数
        try {
            // 根据文件路径创建缓冲输入流
            br = new BufferedReader(new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8));
            // 循环读取文件的每一行, 对需要修改的行进行修改, 放入缓冲对象中
            while ((line = br.readLine()) != null) {
                // 舍弃需要删除的那一行
                if(cnt != index) {
                    buf.append(line);
                    buf.append(System.getProperty("line.separator"));
//                    System.out.println(line);
                }
                cnt++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 关闭流
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    br = null;
                }
            }
        }
//        Log.d("FileModify", "string in read: "+buf.toString());
        return buf.toString().trim();
    }

    /**
     * 将内容回写到文件中
     *
     * @param filePath
     * @param content
     */
    public void write(String filePath, String content) {
        BufferedWriter bw = null;
//        Log.d("FileModify", "string in write: "+content);
        try {
            // 根据文件路径创建缓冲输出流
            bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8));
            // 将内容写入文件中
            bw.write(content);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 关闭流
            if (bw != null) {
                try {
                    bw.close();
                } catch (IOException e) {
                    bw = null;
                }
            }
        }
    }
}