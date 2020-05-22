package qiang.facerecognition.env;

import android.app.Activity;

import net.gotev.speech.Speech;

/**
 * Text To Speech
 * Set the rate
 * Set the pitch
 */
public class TTS {
    private float speechRate;
    private float speechPitch;
    private Speech speech;
    public TTS(Activity activity, float speechRate, float speechPitch){
        this.speechRate = speechRate;
        this.speechPitch = speechPitch;
        this.speech = Speech.init(activity);
        this.speech.setTextToSpeechRate(this.speechRate);
        this.speech.setTextToSpeechPitch(this.speechPitch);
    }
    public TTS(Activity activity, float speechRate){
        this.speechRate = speechRate;
        this.speech = Speech.init(activity);
        this.speech.setTextToSpeechRate(this.speechRate);
    }
    public TTS(Activity activity){
        this.speech = Speech.init(activity);
    }
    public void setRate(float speechRate){
        this.speechRate = speechRate;
        this.speech.setTextToSpeechRate(this.speechRate);
    }
    public float getRate(){
        return this.speechRate;
    }
    public void setPitch(float speechPitch){
        this.speechPitch = speechPitch;
        this.speech.setTextToSpeechPitch(this.speechPitch);
    }
    public float getPitch(){
        return this.speechPitch;
    }
    public void setMode(int mode){
        this.speech.setTextToSpeechQueueMode(mode);
    }
    public void say(String text){
        speech.say(text);
    }
    public void stop(){
        speech.stopTextToSpeech();
    }
//    public void close(){
//        speech.shutdown();
//
//    }
}
