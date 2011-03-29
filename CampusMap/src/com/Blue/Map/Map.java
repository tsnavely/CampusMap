package com.Blue.Map;

import java.io.IOException;

import android.app.Activity;
import android.database.SQLException;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.FloatMath;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.ImageView;

public class Map extends Activity implements OnTouchListener{
	/** Called when the activity is first created. */

	// These matrices will be used to move and zoom image
	Matrix matrix = new Matrix();
	Matrix savedMatrix = new Matrix();

	// We can be in one of these 3 states
	static final int NONE = 0;
	static final int DRAG = 1;
	static final int ZOOM = 2;
	int mode = NONE;

	// Remember some things for zooming
	PointF start = new PointF();
	PointF mid = new PointF();
	float oldDist = 1f;

	// Limit zoomable/pannable image
	private ImageView view;
	private float[] matrixValues = new float[9];
	private float maxZoom;
	private float minZoom;
	private float height;
	private float width;
	private RectF viewRect;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		//Upload database from assets folder 
		//if not already uploaded to '/data/data/com.Blue.Map/databases/'
		DatabaseHelper myDBHelper = new DatabaseHelper(this);
		try{
			myDBHelper.createDatabase();
		}catch (IOException ioe){
			throw new Error("Unable to create database");
		}
		try{
			myDBHelper.openDatabase();
		}catch (SQLException sqle){
			throw sqle;
		}

		setContentView(R.layout.main);
		view = (ImageView) findViewById(R.id.imageView);
		view.setOnTouchListener(this);
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if(hasFocus){
			init();
		}
	}

	private void init() {
		//Get dimensions of display
		Display screen = getWindowManager().getDefaultDisplay();
		int displayHeight = screen.getHeight();
		
		height = view.getDrawable().getIntrinsicHeight();
		width = view.getDrawable().getIntrinsicWidth();
		maxZoom = 4;
		minZoom = displayHeight / height;
		viewRect = new RectF(0, 0, view.getWidth(), view.getHeight());
	}

	@Override
	public boolean onTouch(View v, MotionEvent rawEvent) {
		WrapMotionEvent event = WrapMotionEvent.wrap(rawEvent);
		ImageView view = (ImageView) v;

		// Handle touch events here...
		switch (event.getAction() & MotionEvent.ACTION_MASK) {
		case MotionEvent.ACTION_DOWN:
			savedMatrix.set(matrix);
			start.set(event.getX(), event.getY());
			mode = DRAG;
			break;
		case MotionEvent.ACTION_POINTER_DOWN:
			oldDist = spacing(event);
			if (oldDist > 10f) {
				savedMatrix.set(matrix);
				midPoint(mid, event);
				mode = ZOOM;
			}
			break;
		case MotionEvent.ACTION_UP:
			mode = NONE;
			break;
		case MotionEvent.ACTION_POINTER_UP:
			mode = NONE;
			break;
		case MotionEvent.ACTION_MOVE:
			if (mode == DRAG) {
				matrix.set(savedMatrix);

				// limit pan
				matrix.getValues(matrixValues);
				float currentY = matrixValues[Matrix.MTRANS_Y];
				float currentX = matrixValues[Matrix.MTRANS_X];
				float currentScale = matrixValues[Matrix.MSCALE_Y];
				float currentHeight = height * currentScale;
				float currentWidth = width * currentScale;
				float dx = event.getX() - start.x;
				float dy = event.getY() - start.y;
				float newX = currentX+dx;
				float newY = currentY+dy; 

				RectF drawingRect = new RectF(newX, newY, newX+currentWidth, newY+currentHeight);
				float diffUp = Math.min(viewRect.bottom-drawingRect.bottom, viewRect.top-drawingRect.top);
				float diffDown = Math.max(viewRect.bottom-drawingRect.bottom, viewRect.top-drawingRect.top);
				float diffLeft = Math.min(viewRect.left-drawingRect.left, viewRect.right-drawingRect.right);
				float diffRight = Math.max(viewRect.left-drawingRect.left, viewRect.right-drawingRect.right);
				if(diffUp > 0 ){
					dy +=diffUp; 
				}
				if(diffDown < 0){
					dy +=diffDown;
				} 
				if( diffLeft> 0){ 
					dx += diffLeft;
				}
				if(diffRight < 0){
					dx += diffRight;
				}
				matrix.postTranslate(dx, dy);
			} else if (mode == ZOOM) {
				float newDist = spacing(event);
				if (newDist > 10f) {
					matrix.set(savedMatrix);
					float scale = newDist / oldDist;

					matrix.getValues(matrixValues);
					float currentScale = matrixValues[Matrix.MSCALE_Y];

					// limit zoom
					if (scale * currentScale > maxZoom) {
						scale = maxZoom / currentScale;
					} else if (scale * currentScale < minZoom) {
						scale = minZoom / currentScale;
					}
					matrix.postScale(scale, scale, mid.x, mid.y);
				} 
			}
			break;
		}

		view.setImageMatrix(matrix);
		return true; // indicate event was handled
	}

	/** Determine the space between the first two fingers */
	private float spacing(WrapMotionEvent event) {
		float x = event.getX(0) - event.getX(1);
		float y = event.getY(0) - event.getY(1);
		return FloatMath.sqrt(x * x + y * y);
	}

	/** Calculate the mid point of the first two fingers */
	private void midPoint(PointF point, WrapMotionEvent event) {
		float x = event.getX(0) + event.getX(1);
		float y = event.getY(0) + event.getY(1);
		point.set(x / 2, y / 2);
	}
}