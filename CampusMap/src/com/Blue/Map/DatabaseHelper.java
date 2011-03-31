package com.Blue.Map;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.widget.ArrayAdapter;

public class DatabaseHelper extends SQLiteOpenHelper{
	
    //Declare class variables
	private static String DB_PATH = "/data/data/com.Blue.Map/databases/";
	private static String DB_NAME = "Buildings.db";
	private SQLiteDatabase myDatabase;
	private final Context myContext;
	
	//Contructor to set context from calling class
	public DatabaseHelper(Context context){
		super(context, DB_NAME, null, 1);
		myContext = context;
	}
	
	//create the database
	public void createDatabase() throws IOException{
		boolean dbExist = checkDatabase();
		
		if(dbExist){}
		else{
			getReadableDatabase();
		}
		
		try{
			copyDatabase();
		}catch(IOException e){
			throw new Error("Error copying database");
		}
	}
	
	//check to see if the database exists in the database directory
	private boolean checkDatabase(){
		SQLiteDatabase checkDB = null;
		
		try{
			String myPath = DB_PATH + DB_NAME;
			checkDB = SQLiteDatabase.openDatabase(myPath, null, SQLiteDatabase.OPEN_READONLY);
		}catch(SQLiteException e){}
		
		if(checkDB != null){
			checkDB.close();
		}
		
		return checkDB != null ? true : false;
	}
	
	//copy database from assets folder to database
	private void copyDatabase() throws IOException{
		InputStream myInput = myContext.getAssets().open(DB_NAME);
		String outFileName = DB_PATH + DB_NAME;
		OutputStream myOutput = new FileOutputStream(outFileName);
		
		byte[] buffer = new byte[1024];
		int length;
		while((length = myInput.read(buffer))>0){
			myOutput.write(buffer, 0, length);
		}
		
		myOutput.flush();
		myOutput.close();
		myInput.close();
	}
	
	//open database, read-only
	public void openDatabase() throws SQLException{
		String myPath = DB_PATH + DB_NAME;
		myDatabase = SQLiteDatabase.openDatabase(myPath, null, SQLiteDatabase.OPEN_READONLY);
	}
	
	//close database
	@Override
	public synchronized void close(){
		if(myDatabase != null){
			myDatabase.close();
			super.close();
		}
	}
	
	//these methods must be overriden, however they will not be used
	@Override
	public void onCreate(SQLiteDatabase db){}
	
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion){}
	
	
	
	
	//database queries that return a cursor to be handled
	
	//get row of a distinct building
	public Cursor getGPS(String buildingName){
		Cursor myCursor = myDatabase.query(false, "Buildings", new String[] {"BuildingName", "LAT", "LON"}, "BuildingName='"+buildingName+"'", null, null, null, null, null);
		if(myCursor != null){
			myCursor.moveToFirst();
		}
		return myCursor;
	}
	
	//get value from spinner, using the row id
	public Cursor getGPSFromSpinner(Long id){
		Cursor myCursor = myDatabase.query(false, "Buildings", new String[] {"BuildingName", "LAT", "LON"}, "RowID='"+id+"'", null, null, null, null, null);
		if(myCursor != null){
			myCursor.moveToFirst();
		}
		return myCursor;
	}
	
	//get all rows to populate the activity's spinner
	public ArrayAdapter<String> getAllBuildings(ArrayAdapter<String> adapter){
		Cursor myCursor = myDatabase.rawQuery("select * from Buildings order by BuildingName", null);
		myCursor.moveToFirst();
		while (myCursor.isAfterLast() == false) {
			adapter.add(myCursor.getString(1));
			myCursor.moveToNext();
		}
		return adapter;
	}
}
