package com.Blue.Map;

public class LatLongPixelConversion {
	

	
	final static float latmax = 42.035864f;
	final static float longmax = -93.633217f;
	final static float latmin = 42.022475f;
	final static float longmin = -93.655382f;
	
	final static float totalYPixelLength = 1631f;
	final static float totalXPixelLength = 2000f;
	
	final static float totalChangeXLong = 0.022172f;
	final static float totalChangeYLat = 0.013389f;


	protected float[] calcLatLon(float xpix, float ypix)
	{
		float[] returnval = new float[2];
		
		
		
		//Latitude equations
		float RevYPixel = ypix;
		RevYPixel = RevYPixel / 1631;
		
		RevYPixel = (RevYPixel * (latmax-latmin));
		float LatValueRev = (RevYPixel + latmin);
		
		//Longitude equations
		float RevXPixel = xpix;
		RevXPixel = RevXPixel/2000;
		RevXPixel = (RevXPixel * (longmax-longmin));
		
		float LonValueRev = (RevXPixel + longmin);
		
		returnval[0] = LatValueRev;
		returnval[1] = LonValueRev;
		
		return returnval;
	}
	protected static float[] calcXYPixels(double latValue, double lonValue)
	{
		float[] returnval = new float[2];
		
		
		//Calculates latitude to pixels
		double pixelDecY = ((latValue - latmin)/(latmax-latmin))*1631;
		int yPixel = 1631 - (int)pixelDecY;
		
		//Calculates longitude to pixels
		double pixelDecX = ((lonValue - longmin)/(longmax-longmin))*2000;
		int xPixel = (int)pixelDecX;
		
		
		returnval[0] = xPixel;
		returnval[1] = yPixel;
		
		
		return returnval;
	}
}
