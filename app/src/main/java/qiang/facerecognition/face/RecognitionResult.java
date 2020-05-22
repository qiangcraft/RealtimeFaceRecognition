package qiang.facerecognition.face;

import android.graphics.Bitmap;
import android.graphics.RectF;

public class RecognitionResult {
        /**
         * A unique identifier for what has been recognized. Specific to the class, not the instance of
         * the object.
         */
        private final String id;

        /**
         * Display name for the recognition.
         */
        private final String title;

        /**
         * A sortable score for how good the recognition is relative to others. Higher should be better.
         */
        private final Float faceConfidence;

        /**
         * The calculation result of searching in the face DB. Lower means more similar.
         */
        private final Float cmpResult;

        /** Optional location within the source image for the location of the recognized object. */
        private RectF location;

        private final Bitmap face;


        RecognitionResult(
                final String id, final String title, final Float confidence, Float cmpResult, final RectF location,
                final Bitmap face) {
            this.id = id;
            this.title = title;
            this.faceConfidence = confidence;
            this.cmpResult = cmpResult;
            this.location = location;
            this.face = face;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public Float getFaceConfidence() {
            return faceConfidence;
        }

        public Float getCmpResult() {
            return cmpResult;
        }

        public RectF getLocation() {
            return new RectF(location);
        }

        void setLocation(RectF location) {
            this.location = location;
        }

        public Bitmap getFace(){
            return face;
        }

        @Override
        public String toString() {
            String resultString = "";
            if (id != null) {
                resultString += "[" + id + "] ";
            }

            if (title != null) {
                resultString += title + " ";
            }

            if (faceConfidence != null) {
                resultString += String.format("(%.1f%%) ", faceConfidence * 100.0f);
            }

            if (location != null) {
                resultString += location + " ";
            }

            if (face != null) {
                resultString += face + " ";
            }

            return resultString.trim();
        }
    }