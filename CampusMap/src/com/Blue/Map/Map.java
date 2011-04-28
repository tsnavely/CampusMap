package com.Blue.Map;

import java.io.IOException;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.SQLException;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.FloatMath;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

public class Map extends Activity implements OnTouchListener {

	// These matrices will be used to move and zoom image
	Matrix matrix = new Matrix();
	Matrix savedMatrix = new Matrix();

	// We can be in one of these 3 states
	static final int NONE = 0;
	static final int DRAG = 1;
	static final int ZOOM = 2;
	int mode = NONE; //initialize mode to NONE

	// Coordinates that we need to hold to calculate drag and zoom values
	PointF start = new PointF(); //position of finger touching the screen
	PointF mid = new PointF(); //midpoint, if two fingers are touching the screen
	float oldDist = 1f; //initializes distance between two fingers

	// Map and screen attributes
	private ImageView view; //map image
	private float[] matrixValues = new float[9]; //contains attributes of the map image
	private float maxZoom; //maximum zoom level
	private float minZoom; //minimum zoom level
	private float mapHeight; //height of map image in pixels 
	private float mapWidth; //width of map image in pixels
	private RectF viewRect; //create rectangle object that will hold view information

	// Values for grid
	private float currentX; //current x position of screen on map, in pixels
	private float currentY; //current y position of screen on map, in pixels
	private float displayPixelsX; //number of x pixels displayed on screen
	private float displayPixelsY; //number of y pixels displayed on screen
	private float currentScale; //current map scale

	// ImageView and Matrices of GPS and Building Location Markers
	private ImageView buildingMarker;
	private Matrix buildingMarkerMatrix = new Matrix();
	private Matrix savedBuildingMarkerMatrix = new Matrix();
	private ImageView gpsMarker;
	private Matrix gpsMarkerMatrix = new Matrix();
	private Matrix savedgpsMarkerMatrix = new Matrix();

	// DatabaseHelper sets-up, initializes, and runs queries
	private DatabaseHelper myDBHelper;

	// User Interface Controls
	private Spinner mainSpinner; //drop-down list that will be filled with building names
	private AutoCompleteTextView searchAutoCompleteTextView; //autocompleting search textbox
	private ImageButton imageButton; //enter button for search



	//-----------------------------------------//
	//Called when the activity is first created//
	//-----------------------------------------//

	//When application is started
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main); //display res/layout/main.xml
		//initialize map image found in res/drawable-nodpi
		view = (ImageView) findViewById(R.id.imageView);
		view.setOnTouchListener(this); //set map up to listen for touch events

		//initialize building marker image found in res/drawable-nodpi
		buildingMarker = (ImageView) findViewById(R.id.BuildingMarker); 
		//initialize GPS marker image found in res/drawable-nodpi
		gpsMarker = (ImageView) findViewById(R.id.gpsMarker);

		//Initialize GPS service
		LocationManager locationManager = (LocationManager) this
		.getSystemService(Context.LOCATION_SERVICE);

		LocationListener locationListener = new LocationListener() {
			//when user location changes, including initial location
			public void onLocationChanged(Location location) {
				//converts user's coordinates to pixels for placement on the map
				float[] gpsReturnVal = LatLongPixelConversion.calcXYPixels(
						(float) location.getLatitude(),
						(float) location.getLongitude());

				//place GPS marker onto map image
                //NOTE: adjust value places the center of the marker, instead of the 
				//top left corner of the marker
                float adjust = 15/currentScale;
                
                MarkerToScreen(gpsReturnVal[0]-adjust, gpsReturnVal[1]-adjust, gpsMarker,
                        gpsMarkerMatrix);
			}

			//The following methods are required to be overriden, but are not used
			@Override
			public void onProviderDisabled(String provider) {}
			@Override
			public void onProviderEnabled(String arg0) {}
			@Override
			public void onStatusChanged(String arg0, int arg1, Bundle arg2) {};
		};

		//request user's GPS coordinates as often as possible
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0,
				0, locationListener);


		//Upload database from assets folder if not already 
		//uploaded to the device's '/data/data/com.Blue.Map/databases/' directory
		myDBHelper = new DatabaseHelper(this);
		try {
			myDBHelper.createDatabase();
		} catch (IOException ioe) {
			throw new Error("Unable to create database");
		}
		//open database for SQL queries
		try {
			myDBHelper.openDatabase();
		} catch (SQLException sqle) {
			throw sqle;
		}

		// define user interface widgets
		searchAutoCompleteTextView = (AutoCompleteTextView) findViewById(R.id.searchAutoCompleteTextView);
		mainSpinner = (Spinner) findViewById(R.id.mainSpinner);
		imageButton = (ImageButton) findViewById(R.id.imageButton);

		// set-up adapters for widgets to display
		final ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_spinner_item); 
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mainSpinner.setAdapter(adapter);
		searchAutoCompleteTextView.setAdapter(adapter);

		// display drop-down list title
		mainSpinner.setPrompt("Select Building");
		// query for all buildings to be displayed in spinner
		myDBHelper.getAllBuildings(adapter);

		// set up search button to listen for click
		imageButton.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				//sets the searchImageButton to the autoCompleteTextView item selected
				mainSpinner.setAdapter(adapter);
			}
		});

		// Handles drop-down list selection events
		mainSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {

			//when building selected
			@Override
			public void onItemSelected(AdapterView<?> parentView,
					View selectedItemView, int position, long id) {
				Cursor c = myDBHelper.getGPSFromSpinner(parentView
						.getItemAtPosition(position).toString());
				double latValue = c.getDouble(0); //latitude for selected building
				double lonValue = c.getDouble(1); //longitude for selected building

				float[] returnval = LatLongPixelConversion.calcXYPixels(
						latValue, lonValue); //array of building coordinates in pixels

				//Unfinished and partially implemented code that would center map between 
				//GPS and building markers, and zoom to the necessary level to have 
				//both markers visible
				if (!figureScreenShown(returnval[0], returnval[1], 100, 100)) {
					zoomToFit(returnval[0], returnval[1], 100, 100);
					float[] centerPoint = calculateMidPixels(returnval[0],
							returnval[1], 100, 100);
					 //centerMapWithPixels(centerPoint[0], centerPoint[1]);
				}
//				float[] sendVal = returnScreenPoint(returnval[0], returnval[1]);
				
				// Place building marker to map image
				//NOTE: -63 and -57 centers the marker onto the correct location, due to
				//placement referencing the top left corner of the marker, 
				//not moving markers when zooming to fit,
				//and accounting for slight inaccuracy of the GPS grid
				MarkerToScreen(returnval[0]-63, returnval[1]-57, buildingMarker,
						buildingMarkerMatrix);
				
				
				/*To display LAT and LON values in a pop-up for testing purposes*/
				
//				Toast toast = Toast.makeText(getApplicationContext(), "LAT: "
//						+ latValue + " LON: " + lonValue, Toast.LENGTH_LONG);
//				toast.setGravity(Gravity.BOTTOM, 0, 0);
//				toast.show();
			}

			//The following method is required to be overriden, but are not used
			@Override
			public void onNothingSelected(AdapterView<?> parentView) {}
		});
	}




		//---------------------------------//
		// Handles when map has focus event//
		//---------------------------------//

		@Override
		public void onWindowFocusChanged(boolean hasFocus) {
			super.onWindowFocusChanged(hasFocus);
			if (hasFocus) {
				// get map dimensions
				mapHeight = view.getDrawable().getIntrinsicHeight(); //map image height in pixels
				mapWidth = view.getDrawable().getIntrinsicWidth(); //map image width in pixels

				// set zoom levels
				maxZoom = 4; //arbitrary maximum zoom level, meaning the map is 4X the original size
				//set minimum zoom to the width of the longer screen dimension, to disallow
				//zooming past the map boundaries
				minZoom = getMinZoom(view.getWidth(), view.getHeight());

				// set rectangle to map boundaries
				viewRect = new RectF(0, 0, view.getWidth(), view.getHeight());

				//update current X and Y location, and amount of pixels visible
				setGridValues();
			}
		}

		// --------------------//
		// Handles touch events//
		// --------------------//

		@Override
		public boolean onTouch(View v, MotionEvent rawEvent) {
			WrapMotionEvent event = WrapMotionEvent.wrap(rawEvent);
			ImageView view = (ImageView) v;

			float scale = 0; //scale of view must be initialized

			// Handle touch events
			switch (event.getAction() & MotionEvent.ACTION_MASK) {

			// One finger touch
			case MotionEvent.ACTION_DOWN:
				//set matrices to be manipulated, while keeping original matrices intact
				savedMatrix.set(matrix); 
				savedBuildingMarkerMatrix.set(buildingMarkerMatrix);
				savedgpsMarkerMatrix.set(gpsMarkerMatrix);
				// get location of touch
				start.set(event.getX(), event.getY());
				mode = DRAG; //set mode to drag
				break;

				// Two finger touch
			case MotionEvent.ACTION_POINTER_DOWN:
				oldDist = spacing(event); //distance between two fingers on touch
				// 10f make sure that there are actually two fingers because it's
				// possible to to have a misread from MotionEvent
				if (oldDist > 10f) {
					//set matrices to be manipulated, while keeping original matrices intact
					savedMatrix.set(matrix);
					savedBuildingMarkerMatrix.set(buildingMarkerMatrix);
					savedgpsMarkerMatrix.set(gpsMarkerMatrix);
					// get midpoint between fingers on touch-down
					midPoint(mid, event);
					mode = ZOOM; //set mode to zoom
				}
				break;

				// One finger lifted
			case MotionEvent.ACTION_UP:
				mode = NONE;
				setGridValues();
				break;

				// Two fingers lifted
			case MotionEvent.ACTION_POINTER_UP:
				mode = NONE;
				setGridValues();
				break;

				// When fingers are moved
			case MotionEvent.ACTION_MOVE:
				// for drag event (one finger)
				if (mode == DRAG) {
					//Set original matrices to saved matrices
					matrix.set(savedMatrix);
					buildingMarkerMatrix.set(savedBuildingMarkerMatrix);
					gpsMarkerMatrix.set(savedgpsMarkerMatrix);

					// Limit drag
					setGridValues(); //update current X and Y position and pixels visible
					float currentHeight = mapHeight * currentScale; //current height of map
					float currentWidth = mapWidth * currentScale; //current width of map
					// calculate change in x and y values
					float dx = event.getX() - start.x;
					float dy = event.getY() - start.y;
					// get new position for placement
					float newX = currentX + dx;
					float newY = currentY + dy;

					// Calculate boundaries for new position
					RectF drawingRect = new RectF(newX, newY, newX + currentWidth,
							newY + currentHeight);
					// calculate distance that boundaries are past map limits
					float diffUp = Math.min(viewRect.bottom - drawingRect.bottom,
							viewRect.top - drawingRect.top);
					float diffDown = Math.max(viewRect.bottom - drawingRect.bottom,
							viewRect.top - drawingRect.top);
					float diffLeft = Math.min(viewRect.left - drawingRect.left,
							viewRect.right - drawingRect.right);
					float diffRight = Math.max(viewRect.left - drawingRect.left,
							viewRect.right - drawingRect.right);
					// push map back into view
					if (diffUp > 0) {
						dy += diffUp;
					}
					if (diffDown < 0) {
						dy += diffDown;
					}
					if (diffLeft > 0) {
						dx += diffLeft;
					}
					if (diffRight < 0) {
						dx += diffRight;
					}
					// set matrices for map to be scrolled
					matrix.postTranslate(dx, dy);
					buildingMarkerMatrix.postTranslate(dx, dy);
					gpsMarkerMatrix.postTranslate(dx, dy);

					// for zoom event (two fingers)
				} else if (mode == ZOOM) {
					// calculate new distance between fingers
					float newDist = spacing(event);
					// 10f to make sure that there are actually two fingers because it's
					// possible to to have a misread from MotionEvent
					if (newDist > 10f) {
						//Set original matrices to saved matrices
						matrix.set(savedMatrix);
						buildingMarkerMatrix.set(savedBuildingMarkerMatrix);
						gpsMarkerMatrix.set(savedgpsMarkerMatrix);
						// calculate scale to be set, based on distance ratio
						scale = newDist / oldDist;

						matrix.getValues(matrixValues); //send matrix values to array to be read
						currentScale = matrixValues[Matrix.MSCALE_Y]; //scale of view

						// Limit zoom
						if (scale * currentScale > maxZoom) {
							scale = maxZoom / currentScale;
						} else if (scale * currentScale < minZoom) {
							scale = minZoom / currentScale;
						}

						// set matrix to new scale, and center map to midpoint of fingers
						matrix.postScale(scale, scale, mid.x, mid.y);
						buildingMarkerMatrix.postScale(scale, scale, mid.x, mid.y);
						gpsMarkerMatrix.postScale(scale, scale, mid.x, mid.y);
					}
				}
				break;
			}

			//Move map and markers to new position based on manipulated matrices
			//NOTE: GPS and Building markers set to same scale as map.  This should be fixed.
			view.setImageMatrix(matrix);
			buildingMarker.setImageMatrix(buildingMarkerMatrix);
			gpsMarker.setImageMatrix(gpsMarkerMatrix);
			return true; // event handled
		}

		
		
		//------------//
		//Options Menu//
		//------------//

		//Creates options menu from res/menu/menu.xml
		@Override
		public boolean onCreateOptionsMenu(Menu menu) {
			MenuInflater inflater = getMenuInflater();
			inflater.inflate(R.menu.menu, menu);
			return true;
		}

		//Handles selecting items in options menu events
		@Override
		public boolean onOptionsItemSelected(MenuItem item) {
			switch (item.getItemId()) {

			//when GPS button is selected in options menu
			case R.id.gps:
				//initializes GPS provider service
				LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
				//if GPS is turned on
				if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
					createGPSDisabledAlert(); //display prompt for enabling GPS
				}
				//if GPS is turned off
				else {
					createGPSEnabledAlert(); //display prompt for disabling GPS
				}
				return true; //event handled

				//when Clear Map button is selected in options menu	
			case R.id.clear_map:
				searchAutoCompleteTextView.setText(""); //clear autocompleting search textbox
				mainSpinner.setSelection(0); //resets drop-down list to the initial value
				return true; //event handled

				//when Exit button is selected in options menu	
			case R.id.application_exit:
				System.exit(mode); //terminate application
				return true; //event handled

				//if no button selected, keep menu visible	
			default:return super.onOptionsItemSelected(item);
			}

		}

		//display prompt when GPS is disabled
		private void createGPSDisabledAlert() {
			AlertDialog.Builder builder = new AlertDialog.Builder(this); //create prompt
			builder.setMessage("Your GPS is disabled.  Would you like to enable it?") //prompt message
			.setCancelable(false) //user must respond

			//Build enable GPS button, display system GPS options
			.setPositiveButton("Enable GPS", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int id) {
					showGPSOptions();
				}});
			//Build Cancel button, return to map
			builder.setNegativeButton("Cancel",
					new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					dialog.cancel();
				}});
			//compile and display prompt
			AlertDialog alert = builder.create();
			alert.show();
		}

		//display prompt when GPS is enabled
		private void createGPSEnabledAlert() {
			AlertDialog.Builder builder = new AlertDialog.Builder(this); //create prompt
			builder.setMessage("GPS is enabled.  Would you like to disable it?")
			.setCancelable(false) //user must respond

			//build disable GPS button, display system GPS options
			.setPositiveButton("Disable GPS", new DialogInterface.OnClickListener(){
				@Override
				public void onClick(DialogInterface dialog, int id) {
					showGPSOptions();
				}});
			//Build Cancel button, return to map
			builder.setNegativeButton("Cancel",
					new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					dialog.cancel();
				}});
			//compile and display prompt
			AlertDialog alert = builder.create();
			alert.show();
		}

		//When Enable GPS or Disable GPS button is clicked, display system GPS settings
		private void showGPSOptions() {
			Intent gpsOptionsIntent = new Intent(
					android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
			startActivity(gpsOptionsIntent);

		}
		
		
		
		
		
		
		// Determine the space between the two fingers
		private float spacing(WrapMotionEvent event) {
			float x = event.getX(0) - event.getX(1); //triangle side
			float y = event.getY(0) - event.getY(1); //triangle side
			return FloatMath.sqrt(x * x + y * y); //pythagorean theorem to calculate hypotenuse
		}

		// Calculate the mid point of two fingers
		private void midPoint(PointF point, WrapMotionEvent event) {
			float x = event.getX(0) + event.getX(1);
			float y = event.getY(0) + event.getY(1);
			point.set(x / 2, y / 2);
		}

		// return minimum zoom level to not show out of boundaries
		private float getMinZoom(float width, float height) {
			if (width < height) {
				return height / mapHeight;
			} else {
				return width / mapWidth;
			}
		}


		//Unfinished and partially implemented code to center map to midpoint of GPS 
		//and Building Markers
		public void centerMapWithPixels(float x, float y) {

			// calculate new top left corner
			float newX = x - displayPixelsX / 2;
			float newY = y - displayPixelsY / 2;
			// gets change in x and change in y
			float dx = newX + currentX;
			float dy = newY + currentY;

//			float currentHeight = mapHeight * currentScale;
//			float currentWidth = mapWidth * currentScale;

			// Calculate boundaries for new position
			RectF drawingRect = new RectF(newX, newY, newX + displayPixelsX, newY
					+ displayPixelsY);
			// calculate distance that boundaries are past map limits
			float diffUp = Math.min(viewRect.bottom - drawingRect.bottom,
					viewRect.top - drawingRect.top);
			float diffDown = Math.max(viewRect.bottom - drawingRect.bottom,
					viewRect.top - drawingRect.top);
			float diffLeft = Math.min(viewRect.left - drawingRect.left,
					viewRect.right - drawingRect.right);
			float diffRight = Math.max(viewRect.left - drawingRect.left,
					viewRect.right - drawingRect.right);
			// push map back into view
			if (diffUp > 0) {
				dy += diffUp;
			}
			if (diffDown < 0) {
				dy += diffDown;
			}
			if (diffLeft > 0) {
				dx += diffLeft;
			}
			if (diffRight < 0) {
				dx += diffRight;
			}
			// set matrix for map to be scrolled
			matrix.postTranslate(dx, dy);
			//Move map to new position based on manipulated matrix
			view.setImageMatrix(matrix);
		}

		//Unfinished and partially implemented code that calculates the midpoint in pixels
		//to center screen to map
		public float[] calculateMidPixels(float xPix1, float yPix1, float xPix2,float yPix2) {
			float xDiff = Math.abs(xPix1 - xPix2);
			float yDiff = Math.abs(yPix1 - yPix2);
			float xVal = 0;
			float yVal = 0;
			float[] returnVal = new float[2];

			if (xPix1 > xPix2) {
				xVal = xPix2 + (xDiff / 2);
			} else if (xPix2 > xPix1) {
				xVal = xPix1 + (xDiff / 2);
			} else {
				// equal values
				xVal = xPix1;
			}

			if (yPix1 > yPix2) {
				yVal = yPix2 + (yDiff / 2);
			} else if (yPix2 > yPix1) {
				yVal = yPix1 + (yDiff / 2);
			} else {
				// equal values
				yVal = yPix1;
			}

			returnVal[0] = xVal;
			returnVal[1] = yVal;

			return returnVal;

		}

		//Unfinished and partially implemented code that calculates the midpoint in 
		//latitude and longitude to center screen to map
		public float[] calculateMidLatLon(float lat1, float lon1, float lat2,
				float lon2) {
			float xDiff = Math.abs(lat1 - lat2);
			float yDiff = Math.abs(lon1 - lon2);
			float xVal = 0;
			float yVal = 0;
			float[] returnVal = new float[2];

			if (lat1 > lat2) {
				xVal = lat2 + (xDiff / 2);
			} else if (lat2 > lat1) {
				xVal = lat1 + (xDiff / 2);
			} else {
				// equal values
				xVal = lat1;
			}

			if (lon1 > lon2) {
				yVal = lon2 + (yDiff / 2);
			} else if (lon2 > lon1) {
				yVal = lon1 + (yDiff / 2);
			} else {
				// equal values
				yVal = lon1;
			}

			returnVal[0] = xVal;
			returnVal[1] = yVal;

			return returnVal;
		}

		// Tell whether two points are currently displayed on the screen
		public boolean figureScreenShown(float x1, float y1, float x2, float y2) {

			// calculates the pixel difference between the points
			float xDiff = Math.abs(x1 - x2);
			float yDiff = Math.abs(y1 - y2);

			// if either of the values are larger than the currently displayed
			// screen, returns false
			if (xDiff > displayPixelsX) {
				return false;
			}
			if (yDiff > displayPixelsY) {
				return false;
			}
			// if both values fit the screen size, return true
			return true;
		}

		//	   private float[] returnScreenPoint(float x, float y)
		//	   {
		//	   float[] returnVal = new float[2];
		//	  
		//	   float diffX = currentX + x;
		//	   float diffY = currentY + y;
		//	
		//	   returnVal[0] = diffX;
		//	   returnVal[1] = diffY;
		//
		//	   return returnVal;
		//	   }

		// Zooms the map to a level to accommodate two points
		public void zoomToFit(float x1, float y1, float x2, float y2) {
			float xDiff = Math.abs(x1 - x2);
			float yDiff = Math.abs(y1 - y2);
			float pixDiffx = xDiff - displayPixelsX;
			float pixDiffy = yDiff - displayPixelsY;

			float zoomChange = 0;

			// check to see if xDiff is the largest of the 4 values checked
			if ((pixDiffx > 0) && (pixDiffx > pixDiffy)) {
				zoomChange = displayPixelsX / (xDiff + 50);
				// Manipulate matrix to new zoom level
				matrix.postScale(zoomChange, zoomChange);
				// Zoom map based on manipulated matrix
				view.setImageMatrix(matrix);
			}
			// check to see if yDiff is the largest of the 4 values checked
			else if ((pixDiffy > 0) && (pixDiffy > pixDiffx)) {
				zoomChange = displayPixelsY / (yDiff + 50);
				// Manipulate matrix to new zoom level
				matrix.postScale(zoomChange, zoomChange);
				// Zoom map based on manipulated matrix
				view.setImageMatrix(matrix);
			} else {
				// Same level of zoom needed, no change
			}
			//get grid values of current X pixels displayed, and current Y pixels displayed
			setGridValues();
			float[] centerPoint = new float[2];
			centerPoint = calculateMidPixels(x1, y1, x2, y2);
			//centerMapWithPixels(centerPoint[0], centerPoint[1]);
			float newCenterPointX = displayPixelsX / 2 + centerPoint[0];
			float newCenterPointY = displayPixelsY / 2 + centerPoint[1];

			if (newCenterPointX > 0) {
				newCenterPointX = 0;
			}
			if (newCenterPointY > 0) {
				newCenterPointY = 0;
			}
			// Manipulate matrix to position
			matrix.postTranslate(newCenterPointX, newCenterPointY);
			// center map based on manipulated matrix
			view.setImageMatrix(matrix);
		}

		
		//get necessary values for GPS grid
		private void setGridValues() {
			//set current matrix values to array for reading
			matrix.getValues(matrixValues);
			currentY = matrixValues[Matrix.MTRANS_Y]; //current top left Y position on map
			currentX = matrixValues[Matrix.MTRANS_X]; //current top left X position on map
			currentScale = matrixValues[Matrix.MSCALE_Y]; //current map scale
			//number of pixels that are being displayed
			displayPixelsY = view.getHeight() / currentScale;
			displayPixelsX = view.getWidth() / currentScale;
		}

		//place GPS and Building markers onto the appropriate position on map,
		//by manipulating each of the marker's matrices, and then setting the image
		//to the new matrix
		private void MarkerToScreen(float xpixel, float ypixel,
				ImageView markerType, Matrix matrixType) {
			matrixType.reset(); //reset position to (0,0)
			//manipulate matrix to move to the passed parameters
			matrixType.postTranslate(xpixel * currentScale + currentX, ypixel
					* currentScale + currentY);
			//Markers are invisible to start, and are set to visible if activated
			markerType.setVisibility(ImageView.VISIBLE);
			//Move marker image to a position based on the manipulated matrix
			markerType.setImageMatrix(matrixType);
		}
	}