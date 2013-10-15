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

import java.io.ByteArrayOutputStream;
import java.util.List;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.util.Log;

import com.almalence.SwapHeap;
import com.almalence.opencam.util.Size;

public class AlmaCLRShot
{
	private final String TAG = this.getClass().getName()
			.substring(this.getClass().getName().lastIndexOf(".") + 1);
	
	private int IMAGE_TO_LAYOUT = 8;
	private final static int MAX_INPUT_FRAME = 8;
	
	private List<byte []> mJpegData;
	private Size mPreviewSize;
	private Size mInputFrameSize;
	private int mNumOfFrame;
	private int[] ARGBBuffer = null;
	private int[] mCrop = null;
	private int mSensitivity; // sensitivity: default is 0, useful range is [-15..+15]
	private int mMinSize;  // 	minSize: the smallest object size which will be detected (object area, in pixels).
	private int mGhosting;
	private int mAngle;

    private int mOutNV21 = 0;
    private Rect[] mBoarderRect = null;
    
    private static final Object syncObject = new Object();

    private AlmaCLRShot() {super();}
    private static final AlmaCLRShot mInstance = new AlmaCLRShot();
 
    public static AlmaCLRShot getInstance() {
        return mInstance;
    }
    
	
	public void addInputFrame(List<byte[]> inputFrame, Size size)  throws Exception
	{
		mNumOfFrame = inputFrame.size();
		mInputFrameSize = size;
		
		if (mNumOfFrame > MAX_INPUT_FRAME) {
    		Log.d(TAG, "Number of Input Frame = " + mNumOfFrame);
    		throw new Exception("Too Many Input Frame");
		}
		
		if (mInputFrameSize.isValid() == false) {
    		Log.d(TAG, "Input frame size is wrong ");
    		throw new Exception("Too Many Input Frame");
		}
		
		long pixels = mInputFrameSize.getWidth() * mInputFrameSize.getHeight();
		
		if (pixels >= 7680000) {
			IMAGE_TO_LAYOUT = 16;
		} else {
			IMAGE_TO_LAYOUT = 8;
		}
		
		Initialize();
		
		synchronized (syncObject)
		{
    		int[] PointOfJpegData = new int[mNumOfFrame];
    		int[] LengthOfJpegData = new int[mNumOfFrame];
    		
    		for (int i = 0;i < mNumOfFrame;i++) {
    			PointOfJpegData[i] = SwapHeap.SwapToHeap(inputFrame.get(i));
    			LengthOfJpegData[i] = inputFrame.get(i).length;
    	    	if (PointOfJpegData[i] == 0) {
    	    		Log.d(TAG, "Out of Memory in Native");
    	    		throw new Exception("Out of Memory in Native");
    	    	}
    		}
    		
    	    int error = ConvertFromJpeg(PointOfJpegData, LengthOfJpegData, mNumOfFrame, size.getWidth(), size.getHeight());
    	    if (error < 0) {
    	    	Log.d(TAG, "Out Of Memory");
    	    	throw new Exception("Out Of Memory");
    		} else if (error < mNumOfFrame) {
    			Log.d(TAG, "JPEG buffer is wrong in " + error + " frame");
    			throw new Exception("Out Of Memory");
    		}
		}
		return;
	}

	public boolean initialize(Size previewSize, int angle, int sensitivity, int minSize, int ghosting, int[] sports_order) throws Exception
	{
		mGhosting = ghosting;
		mPreviewSize = previewSize;
		mSensitivity = sensitivity;
		mMinSize = minSize;
		mAngle = angle;
		
		if (mAngle != 0 && mAngle != 90 && mAngle != 180 && mAngle != 270) {
    		Log.d(TAG, "Angle is invalid");
    		throw new Exception("Angle is invalid");
		}
		
		if (mPreviewSize.isValid() == false) {
    		Log.d(TAG, "Preview size is wrong");
    		throw new Exception("Too Many Input Frame");
		}
		
		if (mSensitivity < -15 || mSensitivity > 15) {
			Log.d(TAG, "Sensitivity value is wrong");
			throw new Exception("Sensitivity value is wrong");
		}
		
		if (mMinSize < 0 || mMinSize > mInputFrameSize.getWidth() * mInputFrameSize.getHeight()) {
			Log.d(TAG, "MinSize value is wrong");
			throw new Exception("Sensitivity value is wrong");
		}
		
		mCrop = new int[4];
    	
		removeProcessing(sports_order);
		
		return true;
	}

	
	private Bitmap rotateBitmap(Bitmap b, int w, int h, float angle) {
		if (b == null) {
			return b;
		}
		if (angle == 0) {
			return b;
		}
		Matrix matrix = new Matrix();
		matrix.preRotate(angle);
		Bitmap rotImage = Bitmap.createBitmap(b, 0, 0, w, h, matrix, true);
		b.recycle();
		return rotImage;
	}

	
	public Bitmap getPreviewBitmap()
	{
		Bitmap bitmap = Bitmap.createBitmap(mPreviewSize.getWidth(), mPreviewSize.getHeight(), Config.ARGB_8888);

		Rect rect = new Rect(0, 0, mInputFrameSize.getWidth(), mInputFrameSize.getHeight());
		ARGBBuffer = NV21toARGB(mOutNV21, mInputFrameSize, rect, mPreviewSize);
		bitmap.setPixels(ARGBBuffer, 0, mPreviewSize.getWidth(), 0, 0, mPreviewSize.getWidth(), mPreviewSize.getHeight());
		ARGBBuffer = null;
		
		Log.d(TAG, "getPreviewBitmap() -- end");
		return rotateBitmap(bitmap, bitmap.getWidth(), bitmap.getHeight(), mAngle);
	}
	

	public byte[] processingSaveData()
	{
		byte[] jpegBuffer = null;

		android.graphics.YuvImage out = new android.graphics.YuvImage(
				SwapHeap.SwapFromHeap(mOutNV21, mInputFrameSize.getWidth() * mInputFrameSize.getHeight() * 3 / 2), 
    			ImageFormat.NV21, mInputFrameSize.getWidth(), mInputFrameSize.getHeight(), null);
		mOutNV21 = 0;
		try
        {		
			ByteArrayOutputStream os = new ByteArrayOutputStream();
            
            Rect r = new Rect(mCrop[0], mCrop[1], mCrop[0] + mCrop[2], mCrop[1] + mCrop[3]);

            if (out.compressToJpeg(r, 95, os) == false)
            {
            	Log.d(TAG, "the compression is not successful");
            }
            jpegBuffer = os.toByteArray();
            os.close();
                        
        }
        catch (Exception e)
        {
        	Log.d(TAG, "Exception occured");
        	e.printStackTrace();
        }
		
		return jpegBuffer;
	}

	
	public void release()
	{
		synchronized (syncObject)
		{
			Release(mNumOfFrame);
			
			mPreviewSize = null;
			mInputFrameSize = null;
	
			ARGBBuffer = null;
			mCrop = null;
	
		    mBoarderRect = null;
		    
		    IMAGE_TO_LAYOUT = 8;
		    
	    	if (mOutNV21 != 0) {
	    		SwapHeap.FreeFromHeap(mOutNV21);
	    		mOutNV21 = 0;
	    	}
	
			try {
				this.finalize();
			} catch (Throwable e) {
				Log.d(TAG, "Instance is not finalized correctly");
				e.printStackTrace();
			}
		}
		return;
	}
	
	synchronized private void removeProcessing(int[] sports_order) {
    	if (mOutNV21 != 0) {
    		SwapHeap.FreeFromHeap(mOutNV21);
    		mOutNV21 = 0;
    	}

		mOutNV21 = MovObjProcess(mNumOfFrame, mInputFrameSize, mSensitivity, mMinSize, mCrop, mGhosting, IMAGE_TO_LAYOUT, sports_order);
		return;
	}
	
    private static native String Initialize();
    private static native int Release(int nFrames);
    private static native int ConvertFromJpeg(int frame[], int frame_len[], int nFrames, int sx, int sy);
    private static native int[] NV21toARGB(int inptr, Size src, Rect rect, Size dst);
    private static native int MovObjProcess(int nFrames, Size size,	int sensitivity, int minSize, int[] crop, int ghosting, int ratio, int[] sports_order);
    
    static
    {
        System.loadLibrary("almashot-sequence");
    }
}
//*/