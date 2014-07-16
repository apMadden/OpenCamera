/*
The contents of this file are subject to the Mozilla Public License
Version 1.1 (the "License"); you may not use this file except in
compliance with the License. You may obtain a copy of the License at
http://www.mozilla.org/MPL/

Software distributed under the License is distributed on an "AS IS"
basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
License for the specific language governing rights and limitations
under the License.

The Original Code is collection of files collectively known as Open Camera.

The Initial Developer of the Original Code is Almalence Inc.
Portions created by Initial Developer are Copyright (C) 2013 
by Almalence Inc. All Rights Reserved.
*/

package com.almalence.plugins.processing.sequence;

import java.util.ArrayList;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;

import com.almalence.SwapHeap;
/* <!-- +++
import com.almalence.opencam_plus.MainScreen;
import com.almalence.opencam_plus.PluginManager;
import com.almalence.opencam_plus.R;
import com.almalence.opencam.cameracontroller.CameraController;

+++ --> */
// <!-- -+-
import com.almalence.opencam.MainScreen;
import com.almalence.opencam.PluginManager;
import com.almalence.opencam.R;
import com.almalence.opencam.cameracontroller.CameraController;
//-+- -->

import com.almalence.util.ImageConversion;
import com.almalence.util.Size;
import com.almalence.plugins.processing.sequence.OrderControl.SequenceListener;

/***
Implements night processing
***/

public class SequenceProcessingPlugin implements Handler.Callback, OnClickListener, SequenceListener {
	
	private View postProcessingView;
	
	private long sessionID=0;
	private static int mSensitivity = 15;
	private static int mMinSize = 1000;
	private static String mGhosting = "0";
    
	private static int mAngle = 0;

    private AlmaCLRShot mAlmaCLRShot;

    public static int imgWidthOR;
	public static int imgHeightOR;
	private int mDisplayOrientation;
	private boolean mCameraMirrored;

	private int[] indexes;
	
	private OrderControl sequenceView;
	private static ArrayList<Bitmap> thumbnails = new ArrayList<Bitmap>();

	//indicates that no more user interaction needed
	private boolean finishing = false;
	
	private static boolean isYUV = false;
	
	public View getPostProcessingView() {
		return postProcessingView;
	}
	
	public void onStart() {
		getPrefs();
	}
	
	public void onStartProcessing(long SessionID) {
		finishing = false;
		Message msg = new Message();
		msg.what = PluginManager.MSG_PROCESSING_BLOCK_UI;
		MainScreen.getMessageHandler().sendMessage(msg);	
		
		Message msg2 = new Message();
		msg2.arg1 = PluginManager.MSG_CONTROL_LOCKED;
		msg2.what = PluginManager.MSG_BROADCAST;
		MainScreen.getMessageHandler().sendMessage(msg2);
		
		MainScreen.getGUIManager().lockControls = true;
		
		sessionID=SessionID;

		PluginManager.getInstance().addToSharedMem("modeSaveName"+Long.toString(sessionID), PluginManager.getInstance().getActiveMode().modeSaveName);
		
		mDisplayOrientation = MainScreen.getGUIManager().getDisplayOrientation();
		int orientation = MainScreen.getGUIManager().getLayoutOrientation();    	
    	mLayoutOrientationCurrent = (orientation == 0 || orientation == 180)? orientation: (orientation + 180)%360;
    	mCameraMirrored = CameraController.isFrontCamera();
        
        if(mDisplayOrientation == 0 || mDisplayOrientation == 180)
        {
        	imgWidthOR = MainScreen.getImageHeight();
        	imgHeightOR = MainScreen.getImageWidth();
        }
        else
        {
        	imgWidthOR = MainScreen.getImageWidth();
        	imgHeightOR = MainScreen.getImageHeight();
        }
        
        int iSaveImageWidth = MainScreen.getSaveImageWidth();
		int iSaveImageHeight = MainScreen.getSaveImageHeight();
		
		mAlmaCLRShot = AlmaCLRShot.getInstance();
		
        getPrefs();
         
     	try {
     		Size input = new Size(MainScreen.getImageWidth(), MainScreen.getImageHeight());
            int imagesAmount = Integer.parseInt(PluginManager.getInstance().getFromSharedMem("amountofcapturedframes"+Long.toString(sessionID)));
     		int minSize = 1000;
     		if (mMinSize == 0) {
     			minSize = 0;
     		} else {
     			minSize = input.getWidth() * input.getHeight() / mMinSize;
     		}
    		
    		if (imagesAmount==0)
    			imagesAmount=1;
    		
    		int iImageWidth = MainScreen.getImageWidth();
    		int iImageHeight = MainScreen.getImageHeight();
    		
    		isYUV = Boolean.parseBoolean(PluginManager.getInstance().getFromSharedMem("isyuv"+Long.toString(sessionID)));
    
    		thumbnails.clear();
    		for (int i=1; i<=imagesAmount; i++) {
    			if(isYUV) {
    				thumbnails.add(Bitmap.createScaledBitmap(ImageConversion.decodeYUVfromBuffer(mYUVBufferList.get(i-1), iImageWidth, iImageHeight),
	    	    			MainScreen.getInstance().getResources().getDisplayMetrics().heightPixels / imagesAmount,
	    	    			(int)(iImageHeight * (((float)MainScreen.getInstance().getResources().getDisplayMetrics().heightPixels / imagesAmount) / iImageWidth)),
	    	    			false));
    			}
    			else {
	    			byte[] in = SwapHeap.CopyFromHeap(
	    	        		Integer.parseInt(PluginManager.getInstance().getFromSharedMem("frame" + i+Long.toString(sessionID))),
	    	        		Integer.parseInt(PluginManager.getInstance().getFromSharedMem("framelen" + i+Long.toString(sessionID)))
	    	        		);
	    			
	    			BitmapFactory.Options opts = new BitmapFactory.Options();
	    			thumbnails.add(Bitmap.createScaledBitmap(BitmapFactory.decodeByteArray(in, 0, in.length, opts),
	    	    			MainScreen.getInstance().getResources().getDisplayMetrics().heightPixels / imagesAmount,
	    	    			(int)(opts.outHeight * (((float)MainScreen.getInstance().getResources().getDisplayMetrics().heightPixels / imagesAmount) / opts.outWidth)),
	    	    			false));
    			}
    		}
    		
    		if(!isYUV)
    			getDisplaySize(mJpegBufferList.get(0));
    		else
    		{
    			Display display= ((WindowManager) MainScreen.getInstance().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();    			
    			Point dis = new Point();
    			display.getSize(dis);
    			
    			float imageRatio = (float)iImageWidth / (float)iImageHeight;
    			float displayRatio = (float)dis.y / (float)dis.x;
    			
    			if (imageRatio > displayRatio) {
    				mDisplayWidth = dis.y;
    				mDisplayHeight = (int)((float)dis.y / (float)imageRatio);
    			} else {
    				mDisplayWidth = (int)((float)dis.x * (float)imageRatio);
    				mDisplayHeight = dis.x;
    			}
    		}
    		
    		Size preview = new Size(mDisplayWidth, mDisplayHeight);
    		
    		PluginManager.getInstance().addToSharedMem("amountofresultframes"+Long.toString(sessionID), String.valueOf(imagesAmount));
    		
			PluginManager.getInstance().addToSharedMem("saveImageWidth"+String.valueOf(sessionID), String.valueOf(iSaveImageWidth));
	    	PluginManager.getInstance().addToSharedMem("saveImageHeight"+String.valueOf(sessionID), String.valueOf(iSaveImageHeight));
     		
    		this.indexes = new int[imagesAmount];
            for (int i = 0; i < imagesAmount; i++)
            {
            	this.indexes[i] = i;
            }
            
     		//frames!!! should be taken from heap
            if(!isYUV)
            	mAlmaCLRShot.addJPEGInputFrame(mJpegBufferList, input);
            else
            	mAlmaCLRShot.addYUVInputFrame(mYUVBufferList, input);

     		mAlmaCLRShot.initialize(preview,
     				mAngle,
 					/*
 					 * sensitivity for objection detection
 					 * 
 					 */
 					mSensitivity - 15,
 					/*
 					 *  Minimum size of object to be able to detect
 					 *  -15 ~ 15
 					 *  max -> easy detection dull detection
 					 *  min -> 
 					 */
 					minSize,
 					/*
 					 * ghosting parameter
    					 * 0 : normal operation
 					 * 1 : detect ghosted objects but not remove them
 					 * 2 : detect and remove all object
 					 */
 					Integer.parseInt(mGhosting),
 					indexes);
     		
     		//compressed_frame.clear();
 		} 
     	catch (Exception e) 
 		{
 			e.printStackTrace();
 		}
     		}
/************************************************
 * 		POST PROCESSING
 ************************************************/
	public boolean isPostProcessingNeeded() {
		return true;
	}
	
	private ImageView mImgView;
	private Button mSaveButton;
	private static final int MSG_REDRAW = 1;
	private static final int MSG_LEAVING = 3;
	private static final int MSG_END_OF_LOADING = 4;
	private final Handler mHandler = new Handler(this);
	private int mLayoutOrientationCurrent;
	private int mDisplayOrientationCurrent;
	private Bitmap PreviewBmp = null;
	public static int mDisplayWidth;
	public static int mDisplayHeight;
	
	private static ArrayList<byte[]> mJpegBufferList;
	public static void setmJpegBufferList(ArrayList<byte[]> mJpegBufferList) {
		SequenceProcessingPlugin.mJpegBufferList = mJpegBufferList;
	}

	private static ArrayList<Integer> mYUVBufferList;
	public static void setmYUVBufferList(ArrayList<Integer> mYUVBufferList) {
		SequenceProcessingPlugin.mYUVBufferList = mYUVBufferList;
	}
	
	public static ArrayList<Bitmap> mInputBitmapList = new ArrayList<Bitmap>();
	Paint paint=null;
	
	private boolean postProcessingRun = false;
	
	public void onStartPostProcessing() {	
		LayoutInflater inflator = MainScreen.getInstance().getLayoutInflater();
		postProcessingView = inflator.inflate(R.layout.plugin_processing_sequence_postprocessing, null, false);
		
		mImgView = ((ImageView)postProcessingView.findViewById(R.id.sequenceImageHolder));
		
        if (PreviewBmp != null) {
        	PreviewBmp.recycle();
        }

		paint = new Paint();
		paint.setColor(0xFF00AAEA);
		paint.setStrokeWidth(5);
		paint.setPathEffect(new DashPathEffect(new float[] {5,5},0));

    	PreviewBmp = mAlmaCLRShot.getPreviewBitmap();

        if (PreviewBmp != null)  
        {
        	Matrix matrix = new Matrix();
        	matrix.postRotate(90);
        	Bitmap rotated = Bitmap.createBitmap(PreviewBmp, 0, 0, PreviewBmp.getWidth(), PreviewBmp.getHeight(),
        	        matrix, true);
        	mImgView.setImageBitmap(rotated);
        	mImgView.setRotation(CameraController.isFrontCamera()? ((mDisplayOrientation == 0 || mDisplayOrientation == 180) ? 0 : 180) : 0);
        }		

        sequenceView = ((OrderControl)postProcessingView.findViewById(R.id.seqView));
    	final Bitmap[] thumbnailsArray = new Bitmap[thumbnails.size()];
    	for (int i = 0; i < thumbnailsArray.length; i++)
    	{
    		Bitmap bmp = thumbnails.get(i);
    		Matrix matrix = new Matrix();
        	matrix.postRotate(CameraController.isFrontCamera()? ((mDisplayOrientation == 0 || mDisplayOrientation == 180) ? 270 : 90) : 90);
        	Bitmap rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(),
        	        matrix, true);
    		thumbnailsArray[i] = rotated;
    	}
    	sequenceView.setContent(thumbnailsArray, this);
    	LayoutParams lp = (LayoutParams)sequenceView.getLayoutParams();
    	lp.height = thumbnailsArray[0].getHeight();
    	sequenceView.setLayoutParams(lp);
    	
    	sequenceView.setRotation(CameraController.isFrontCamera()?180:0);
    	
	    mHandler.sendEmptyMessage(MSG_END_OF_LOADING);
	}
	
	public void getDisplaySize(byte[] data) 
	{
		Display display= ((WindowManager) MainScreen.getInstance().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inPreferredConfig = Config.ARGB_8888;
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeByteArray(data, 0, data.length, options);
		Point dis = new Point();
		display.getSize(dis);

		float imageRatio = (float)options.outWidth / (float)options.outHeight;
		float displayRatio = (float)dis.y / (float)dis.x;
		
		if (imageRatio > displayRatio) {
			mDisplayWidth = dis.y;
			mDisplayHeight = (int)((float)dis.y / (float)imageRatio);
		} else {
			mDisplayWidth = (int)((float)dis.x * (float)imageRatio);
			mDisplayHeight = dis.x;
		}
		return;
	}
	
	
    public void setupSaveButton() {
    	// put save button on screen
        mSaveButton = new Button(MainScreen.getInstance());
        mSaveButton .setBackgroundResource(R.drawable.button_save_background);
        mSaveButton .setOnClickListener(this);
        LayoutParams saveLayoutParams = new LayoutParams(
        		(int) (MainScreen.getMainContext().getResources().getDimension(R.dimen.postprocessing_savebutton_size)), 
        		(int) (MainScreen.getMainContext().getResources().getDimension(R.dimen.postprocessing_savebutton_size)));
        saveLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        saveLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        saveLayoutParams.setMargins(
        		(int)(MainScreen.getInstance().getResources().getDisplayMetrics().density * 8), 
        		(int)(MainScreen.getInstance().getResources().getDisplayMetrics().density * 8), 
        		0, 
        		0);
		((RelativeLayout)postProcessingView.findViewById(R.id.sequenceLayout)).addView(mSaveButton, saveLayoutParams);
		mSaveButton.setRotation(mLayoutOrientationCurrent);
    }
    
    public void onOrientationChanged(int orientation)
    {	    	
    	if(orientation != mDisplayOrientationCurrent)
    	{
    		mDisplayOrientationCurrent = orientation;
    		mLayoutOrientationCurrent = (orientation == 0 || orientation == 180) ? orientation + 90 : orientation - 90;
    		if(postProcessingRun)
    			mSaveButton.setRotation(mLayoutOrientationCurrent);
    	}
    }
    
    @Override
	public void onClick(View v) 
	{
    	if (v == mSaveButton)
    	{
    		if (finishing == true)
				return;
    		finishing = true;
    		savePicture(MainScreen.getMainContext());
    		
    		mHandler.sendEmptyMessage(MSG_LEAVING);
    	}
	}
    
    public void savePicture(Context context)
    {
    	byte[] result = mAlmaCLRShot.processingSaveData();
		int frame_len = result.length;
		int frame = SwapHeap.SwapToHeap(result);

		PluginManager.getInstance().addToSharedMem("resultframeformat1"+Long.toString(sessionID), "jpeg");
		PluginManager.getInstance().addToSharedMem("resultframe1"+Long.toString(sessionID), String.valueOf(frame));
    	PluginManager.getInstance().addToSharedMem("resultframelen1"+Long.toString(sessionID), String.valueOf(frame_len));
    	
    	PluginManager.getInstance().addToSharedMem("resultframeorientation1" + String.valueOf(sessionID), String.valueOf(mDisplayOrientation));
    	PluginManager.getInstance().addToSharedMem("resultframemirrored1" + String.valueOf(sessionID), String.valueOf(mCameraMirrored));
		
		
		PluginManager.getInstance().addToSharedMem("amountofresultframes"+Long.toString(sessionID), String.valueOf(1));
		
		PluginManager.getInstance().addToSharedMem("sessionID", String.valueOf(sessionID));
		mAlmaCLRShot.release();
    }
    
    @Override
	public boolean handleMessage(Message msg)
	{
    	switch (msg.what)
    	{
    	case MSG_END_OF_LOADING:
			setupSaveButton();
			postProcessingRun = true;
    		break;
    	case MSG_LEAVING:
    		MainScreen.getMessageHandler().sendEmptyMessage(PluginManager.MSG_POSTPROCESSING_FINISHED);
    		mJpegBufferList.clear();
    		
    		Message msg2 = new Message();
    		msg2.arg1 = PluginManager.MSG_CONTROL_UNLOCKED;
    		msg2.what = PluginManager.MSG_BROADCAST;
    		MainScreen.getMessageHandler().sendMessage(msg2);
    		
    		MainScreen.getGUIManager().lockControls = false;   		

    		postProcessingRun = false;
        	return false;
        	
    	case MSG_REDRAW:
            if (PreviewBmp != null)
            	PreviewBmp.recycle();
            if (finishing == true)
				return true;
    		PreviewBmp = mAlmaCLRShot.getPreviewBitmap();
            if (PreviewBmp != null) 
        	{
            	Matrix matrix = new Matrix();
            	matrix.postRotate(90);
            	Bitmap rotated = Bitmap.createBitmap(PreviewBmp, 0, 0, PreviewBmp.getWidth(), PreviewBmp.getHeight(),
            	        matrix, true);
            	mImgView.setImageBitmap(rotated);
            	mImgView.setRotation(CameraController.isFrontCamera()? ((mDisplayOrientation == 0 || mDisplayOrientation == 180) ? 0 : 180) : 0);
        	}
            
            sequenceView.setEnabled(true);
            break;
    	default:
			break;
    	}    	
    	return true;
	}
	
	
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && MainScreen.getInstance().findViewById(R.id.postprocessingLayout).getVisibility() == View.VISIBLE)
		{
			if (finishing == true)
				return true;
			finishing = true;
			mHandler.sendEmptyMessage(MSG_LEAVING);
			mAlmaCLRShot.release();
			return true;
		}
		
		return false;
	}
		

	public void onSequenceChanged(final int[] idx) {
		sequenceView.setEnabled(false);

		Size input = new Size(MainScreen.getImageWidth(), MainScreen.getImageHeight());
 		int minSize = 1000;
 		if (mMinSize == 0) {
 			minSize = 0;
 		} else {
 			minSize = input.getWidth() * input.getHeight() / mMinSize;
 		}
 		
		Size preview = new Size(mDisplayWidth, mDisplayHeight);
		try {
			mAlmaCLRShot.initialize(preview,
					mAngle,
						/*
						 * sensitivity for objection detection
						 * 
						 */
						mSensitivity - 15,
						/*
						 *  Minimum size of object to be able to detect
						 *  -15 ~ 15
						 *  max -> easy detection dull detection
						 *  min -> 
						 */
						minSize,
						/*
						 * ghosting parameter
						 * 0 : normal operation
						 * 1 : detect ghosted objects but not remove them
						 * 2 : detect and remove all object
						 */
						Integer.parseInt(mGhosting),
						idx);
		} catch (NumberFormatException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		mHandler.sendEmptyMessage(MSG_REDRAW);
	}
	
	private void getPrefs() {
		// Get the xml/preferences.xml preferences
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainScreen.getInstance().getBaseContext());
        mSensitivity = prefs.getInt("Sensitivity", 19);
        mMinSize = prefs.getInt("MinSize", 1000);
        mGhosting = prefs.getString("Ghosting", "2");
    }
/************************************************
 * 		POST PROCESSING END
 ************************************************/
}
