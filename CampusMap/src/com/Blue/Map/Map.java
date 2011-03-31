package com.Blue.Map;

import java.io.IOException;

import android.app.Activity;
import android.database.Cursor;
import android.database.SQLException;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.FloatMath;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

public class Map extends Activity implements OnTouchListener{
	AutoCompleteTextView searchAutoCompleteTextView;
	Spinner mainSpinner;
	Cursor c;

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
	
	DatabaseHelper myDBHelper;
	double latValue;
	double lonValue;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		//Upload database from assets folder 
		//if not already uploaded to '/data/data/com.Blue.Map/databases/'
		myDBHelper = new DatabaseHelper(this);
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

		searchAutoCompleteTextView = (AutoCompleteTextView) findViewById(R.id.searchAutoCompleteTextView);
		mainSpinner = (Spinner) findViewById(R.id.mainSpinner);

		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mainSpinner.setAdapter(adapter);
		mainSpinner.setPrompt("Select Building");
		searchAutoCompleteTextView.setAdapter(adapter);

		myDBHelper.getAllBuildings(adapter);

		mainSpinner.setOnItemSelectedListener(new OnItemSelectedListener(){

			@Override
			public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
				c = myDBHelper.getGPSFromSpinner(id);
				latValue = c.getDouble(1);
				lonValue = c.getDouble(2);
				Toast toast = Toast.makeText(getApplicationContext(), "LAT: "+latValue+" LON: "+lonValue, Toast.LENGTH_LONG);
				toast.setGravity(Gravity.BOTTOM, 0, 0);
				toast.show();
			}

			@Override
			public void onNothingSelected(AdapterView<?> parentView) {
				//Do nothing
			}

		});
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
		
		//int displayHeight = screen.getHeight();
		int displayWidth = screen.getWidth();

		height = view.getDrawable().getIntrinsicHeight();
		width = view.getDrawable().getIntrinsicWidth();
		maxZoom = 4;
		minZoom = displayWidth / width;
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