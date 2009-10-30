// Copyright (C) 2009 Mihai Preda

package calculator;

import android.view.View;
import android.view.MotionEvent;
import android.widget.TextView;
import android.widget.EditText;
import android.content.Context;
import android.text.Editable;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.Region;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;

import android.util.AttributeSet;

import android.util.Log;
import arity.calculator.R;

import org.javia.arity.*;

public class GraphView extends View {
    private int width, height;
    private Matrix matrix = new Matrix();
    private Paint paint = new Paint(), textPaint = new Paint();
    private Function function;
    private Data next = new Data(), graph = new Data();
    private boolean invalidated = true;
    private Bitmap bitmap;

    public GraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setAntiAlias(false);

        textPaint.setColor(0xff00b000);
        textPaint.setTextSize(10);
        textPaint.setAntiAlias(true);
    }

    void setFunction(Function f) {
        this.function = f;
        invalidated = true;
    }

    protected void onSizeChanged(int w, int h, int ow, int oh) {
        width = w;
        height = h;
        invalidated = true;
        bitmap = null;
    }

    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (function == null) {
            return;
        }
        if (invalidated) {
            drawBitmap();
        }
        canvas.drawBitmap(bitmap, 0, 0, null);
    }

    private static final float 
        NEG_INF = Float.NEGATIVE_INFINITY, 
        POS_INF = Float.POSITIVE_INFINITY;

    private float eval(float x) {
        float v = (float) function.eval(x);
        if (v == NEG_INF) {
            return -10000f;
        }
        if (v == POS_INF) {
            return 10000f;
        }
        return v;
    }

    private Data computeGraph(float minX, float maxX, float minY, float maxY) {
        final float scale = width / (maxX - minX);
        final float maxStep = 15.8976f / scale;
        final float minStep = .1f / scale;
        Calculator.log("step min " + minStep + " max " + maxStep);
        final float ythresh = 1/scale;
        next.clear();
        graph.clear();
        graph.push(minX, eval(minX));
        float leftX = graph.topX();
        float leftY = graph.topY();
        float rightX = 0, rightY = 0;
        boolean advance = false;
        while (true) {
            if (advance) {
                leftX = rightX;
                leftY = rightY;
                next.pop();
            }
            advance = true;
            if (next.empty()) {
                float x = leftX + maxStep;
                next.push(x, eval(x));                
            }
            rightX = next.topX();
            rightY = next.topY();
            if (leftX > maxX) {
                break;
            }
            if (leftY != leftY && rightY != rightY) { // NaN
                continue;
            }
            float span = rightX - leftX;
            if (span <= minStep ||
                (leftY < minY && rightY < minY) ||
                (leftY > maxY && rightY > maxY)) {
                graph.push(rightX, rightY);
                continue;
            }
            if ((leftY < -100 && rightY > 100) || 
                (leftY > 100 && rightY < -100)) {
                graph.push(rightX, Float.NaN);
                graph.push(rightX, rightY);
                continue;
            }

            float middleX = (leftX + rightX) / 2;
            float middleY = eval(middleX);
            float diff = Math.abs(leftY + rightY - middleY - middleY);
            if (diff < ythresh) {
                graph.push(rightX, rightY);
            } else {
                next.push(middleX, middleY);
                advance = false;
            }
        }
        return graph;
    }
    
    private Path graphToPath(Data graph) {
        boolean first = true;
        int size = graph.size;
        float[] xs = graph.xs;
        float[] ys = graph.ys;
        Path path = new Path();
        for (int i = 0; i < size; ++i) {
            float y = ys[i];
            float x = xs[i];
            // Calculator.log("path " + x + ' ' + y);
            if (y == y) { // !NaN
                if (first) {
                    path.moveTo(x, y);
                    first = false;
                } else {
                    path.lineTo(x, y);
                }
            } else {
                first = true;
            }
        }
        return path;
    }

    private void drawBitmap() {
        invalidated = false;

        float maxX = 4;
        float minX = -maxX;
        float maxY = maxX * height / width;
        float minY = -maxY;
        Data graph = computeGraph(minX, maxX, minY, maxY);
        Calculator.log("Uses points " + graph.size);
        Path path = graphToPath(graph);  

        if (bitmap == null) {
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        }
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(0xffffffff);
                
        paint.setColor(0xffa0ffa0);
        paint.setStrokeWidth(0);
        paint.setAntiAlias(false);
        paint.setStyle(Paint.Style.STROKE);

        final float w2 = width/2f, h2 = height/2f;

        canvas.drawLine(w2, 0, w2, height, paint);
        canvas.drawLine(0, h2, width, h2, paint);

        final float scale = width / (maxX - minX);
        final float tickSize = 3;
        final float y1 = h2 - tickSize;
        final float y2 = h2 + tickSize;
        paint.setColor(0xff00ff00);
        int v = (int)minX;
        textPaint.setTextAlign(Paint.Align.CENTER);
        for (float x = ((int)minX - minX) * scale; x <= width; x += scale, ++v) {
            canvas.drawLine(x, y1, x, y2, paint);
            if (v != 0) {
                canvas.drawText("" + v, x, y2+10, textPaint);
            }
        }
        
        final float x1 = w2 - tickSize;
        final float x2 = w2 + tickSize;
        v = (int)minY;
        textPaint.setTextAlign(Paint.Align.RIGHT);
        for (float y = height - ((int)minY - minY) * scale; y >= 0; y -= scale, ++v) {
            canvas.drawLine(x1, y, x2, y, paint);
            if (v != 0) {
                canvas.drawText("" + v, x1, y+4, textPaint);
            }
        }

        matrix.reset();
        matrix.preScale(scale, -scale);
        matrix.postTranslate(width/2, height/2);

        paint.setColor(0xff000000);
        paint.setStrokeWidth(0);
        paint.setAntiAlias(true);
        path.transform(matrix);
        canvas.drawPath(path, paint);
    }
}