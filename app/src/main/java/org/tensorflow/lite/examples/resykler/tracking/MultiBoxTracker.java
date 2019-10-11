/* Copyright 2019 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package org.tensorflow.lite.examples.resykler.tracking;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Join;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.RectF;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Pair;
import android.util.TypedValue;

import org.tensorflow.lite.examples.resykler.env.BorderedText;
import org.tensorflow.lite.examples.resykler.env.ImageUtils;
import org.tensorflow.lite.examples.resykler.env.Logger;
import org.tensorflow.lite.examples.resykler.tflite.Classifier.Recognition;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;


/**
 * A tracker that handles non-max suppression and matches existing objects to new detections.
 */
public class MultiBoxTracker {
    private static final float TEXT_SIZE_DIP = 18;
    private static final float MIN_SIZE = 16.0f;
    private static final int[] COLORS = {
            Color.parseColor("#ff0000"),
            Color.parseColor("#009900"),
            Color.parseColor("#0080ff")
//            Color.parseColor("#F4924F")
    };
    final List<Pair<Float, RectF>> screenRects = new LinkedList<Pair<Float, RectF>>();
    private final Logger logger = new Logger();
    private final Queue<Integer> availableColors = new LinkedList<Integer>();
    private final List<TrackedRecognition> trackedObjects = new LinkedList<TrackedRecognition>();
    private final Paint boxPaint = new Paint();
    private final float textSizePx;
    private final BorderedText borderedText;
    private Matrix frameToCanvasMatrix;
    private int frameWidth;
    private int frameHeight;
    private int sensorOrientation;

    private int numDetect;
    private float lastX;
    private float lastY;
    private boolean first = true;
    private int count = 0;
    private int endCount = 0;
    private boolean flag = false;
    Path mPath = new Path();
    public static Bitmap bmap;


    public MultiBoxTracker(final Context context) {
        for (final int color : COLORS) {
            availableColors.add(color);
        }

        boxPaint.setColor(ContextCompat.getColor(context, android.R.color.holo_orange_dark));
        boxPaint.setStyle(Style.STROKE);
        boxPaint.setStrokeWidth(45.0f);
        boxPaint.setStrokeCap(Cap.ROUND);
        boxPaint.setStrokeJoin(Join.ROUND);
        boxPaint.setStrokeMiter(100);

        textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, context.getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
    }

    public synchronized void setFrameConfiguration(
            final int width, final int height, final int sensorOrientation) {
        frameWidth = width;
        frameHeight = height;
        this.sensorOrientation = sensorOrientation;
    }

    public synchronized void drawDebug(final Canvas canvas) {
        final Paint textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(60.0f);

        final Paint boxPaint = new Paint();
        boxPaint.setColor(Color.RED);
        boxPaint.setAlpha(200);
        boxPaint.setStyle(Style.STROKE);

        for (final Pair<Float, RectF> detection : screenRects) {
            final RectF rect = detection.second;

            canvas.drawRect(rect, boxPaint);
            // #####
//            canvas.drawText("BOTTOM" + Float.toString(rect.bottom), rect.centerX(), rect.centerY()-100, textPaint);
//            canvas.drawText("TOP"+Float.toString(rect.top), rect.centerX(),rect.centerY()+100, textPaint);
////            canvas.drawText("TOP" + Float.toString(rect.top), rect.top, rect.right, textPaint);
////            canvas.drawText("LEFT" + Float.toString(rect.left), rect.bottom, rect.left+100, textPaint);
////            canvas.drawText("RIGHT" + Float.toString(rect.right), rect.top+100, rect.right, textPaint);
            if (rect.top <= 0) {
                rect.top = 1;
            }
            if (rect.left <= 0) {
                rect.left = 1;
            }
            float area = (rect.bottom - rect.top) * (rect.right - rect.left) / 1000;
            canvas.drawText(Float.toString(area), rect.centerX(), rect.centerY(), textPaint);
            // #####

//            canvas.drawText("" + detection.first, rect.left, rect.top, textPaint);
            borderedText.drawText(canvas, rect.centerX(), rect.centerY(), "" + detection.first);
            break;
        }
    }

    public synchronized int trackResults(final List<Recognition> results, final long timestamp) {
        logger.i("Processing %d results from %d", results.size(), timestamp);
        numDetect = results.size();
        if(numDetect == 0){
            flag = false;
        }else{
            flag=true;
        }
//        if (flag && numDetect == 0) {
//            endCount++;
//        } else {
//            endCount = 0;
//        }
//        if (numDetect == 1) {
//            flag = true;
//            count++;
//        }
//        else if (numDetect >= 3 && flag) {
//            flag = false;
//            count = 0;
//            return 3;
//        }

//        else if (count > 10 && flag && endCount > 7) {
//            flag = false;
//            count = 0;
//            return 3;
//
//        }

        processResults(results);

        return 1;
    }

    private Matrix getFrameToCanvasMatrix() {
        return frameToCanvasMatrix;
    }

    public synchronized void draw(final Canvas canvas) {
//        if (!flag) {
//            mPath.reset();
//            first = true;
//        }

        final boolean rotated = sensorOrientation % 180 == 90;
        final float multiplier =
                Math.min(
                        canvas.getHeight() / (float) (rotated ? frameWidth : frameHeight),
                        canvas.getWidth() / (float) (rotated ? frameHeight : frameWidth));
        frameToCanvasMatrix =
                ImageUtils.getTransformationMatrix(
                        frameWidth,
                        frameHeight,
                        (int) (multiplier * (rotated ? frameHeight : frameWidth)),
                        (int) (multiplier * (rotated ? frameWidth : frameHeight)),
                        sensorOrientation,
                        false);
//        if (count > 3 && flag) {
        if (!flag){
            trackedObjects.clear();
        }
        for (final TrackedRecognition recognition : trackedObjects) {
            final RectF trackedPos = new RectF(recognition.location);

            getFrameToCanvasMatrix().mapRect(trackedPos);
            if (recognition.title.equals("can")) {
                boxPaint.setColor(Color.parseColor("#ff0000"));
            } else if (recognition.title.equals("glass")) {
                boxPaint.setColor(Color.parseColor("#009900"));

            } else if (recognition.title.equals("pet")) {
                boxPaint.setColor(Color.parseColor("#0080ff"));
            }

//            boxPaint.setColor(recognition.color);

            float cornerSize = Math.min(trackedPos.width(), trackedPos.height()) / 8.0f;
//            canvas.drawRoundRect(trackedPos, cornerSize, cornerSize, boxPaint);  // Drawing BOXES!
            canvas.drawRect(trackedPos, boxPaint);
            final String labelString =
                    !TextUtils.isEmpty(recognition.title)
                            ? String.format("%s %.2f", recognition.title, (100 * recognition.detectionConfidence))
                            : String.format("%.2f", (100 * recognition.detectionConfidence));
            borderedText.drawText(canvas, trackedPos.left + cornerSize, trackedPos.top,
                    labelString);
//      borderedText.drawText(
//          canvas, trackedPos.left + cornerSize, trackedPos.top, labelString + "%", boxPaint);

//                if (first) {
//                    mPath.moveTo(trackedPos.centerX(), trackedPos.centerY() + 50);
//                    first = false;
//                } else {
//                    mPath.lineTo(trackedPos.centerX(), trackedPos.centerY() + 50);
//                }
//                canvas.drawPath(mPath, boxPaint);
//                bmap = Bitmap.createBitmap(canvas.getWidth(), canvas.getHeight(), Bitmap.Config.ARGB_8888);
//                Canvas C = new Canvas(bmap);
//                Paint P = new Paint();
//                C.drawColor(Color.WHITE);
//                P.setColor(Color.BLACK);
////                P.setAlpha(200);
//                P.setStyle(Style.STROKE);
//                P.setStrokeWidth(45.0f);        //두께 width
//                P.setStrokeCap(Cap.ROUND);
//                P.setStrokeJoin(Join.ROUND);
//                P.setStrokeMiter(100);
//                C.drawPath(mPath, P);
//                lastX = trackedPos.centerX();
//                lastY = trackedPos.centerY();

        }


//        }

    }

    private void processResults(final List<Recognition> results) {
        final List<Pair<Float, Recognition>> rectsToTrack = new LinkedList<Pair<Float, Recognition>>();

        screenRects.clear();
        final Matrix rgbFrameToScreen = new Matrix(getFrameToCanvasMatrix());

        for (final Recognition result : results) {
            if (result.getLocation() == null) {
                continue;
            }
            final RectF detectionFrameRect = new RectF(result.getLocation());

            final RectF detectionScreenRect = new RectF();
            rgbFrameToScreen.mapRect(detectionScreenRect, detectionFrameRect);

            logger.v(
                    "Result! Frame: " + result.getLocation() + " mapped to screen:" + detectionScreenRect);

            screenRects.add(new Pair<Float, RectF>(result.getConfidence(), detectionScreenRect));

            if (detectionFrameRect.width() < MIN_SIZE || detectionFrameRect.height() < MIN_SIZE) {
                logger.w("Degenerate rectangle! " + detectionFrameRect);
                continue;
            }

            rectsToTrack.add(new Pair<Float, Recognition>(result.getConfidence(), result));
        }

        if (rectsToTrack.isEmpty()) {
            logger.v("Nothing to track, aborting.");
            return;
        }

        trackedObjects.clear();
        for (final Pair<Float, Recognition> potential : rectsToTrack) {
            final TrackedRecognition trackedRecognition = new TrackedRecognition();
            trackedRecognition.detectionConfidence = potential.first;
            trackedRecognition.location = new RectF(potential.second.getLocation());
            trackedRecognition.title = potential.second.getTitle();
            trackedRecognition.color = COLORS[trackedObjects.size()];
            trackedObjects.add(trackedRecognition);

            if (trackedObjects.size() >= COLORS.length) {
                break;
            }
        }
    }

    private static class TrackedRecognition {
        RectF location;
        float detectionConfidence;
        int color;
        String title;
    }
}
