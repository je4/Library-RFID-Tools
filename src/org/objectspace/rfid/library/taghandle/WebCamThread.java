/*******************************************************************************
 * Copyright 2015
 * Center for Information, Media and Technology (ZIMT)
 * HAWK University for Applied Sciences and Arts Hildesheim/Holzminden/Göttingen
 *
 * This file is part of HAWK RFID Library Tools.
 * 
 * HAWK RFID Library Tools is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Diese Datei ist Teil von HAWK RFID Library Tools.
 *  
 * HAWK RFID Library Tools ist Freie Software: Sie können es unter den Bedingungen
 * der GNU General Public License, wie von der Free Software Foundation,
 * Version 3 der Lizenz oder (nach Ihrer Wahl) jeder neueren
 * veröffentlichten Version, weiterverbreiten und/oder modifizieren.
 * 
 * Dieses Programm wird in der Hoffnung, dass es nützlich sein wird, aber
 * OHNE JEDE GEWÄHRLEISTUNG, bereitgestellt; sogar ohne die implizite
 * Gewährleistung der MARKTFÄHIGKEIT oder EIGNUNG FÜR EINEN BESTIMMTEN ZWECK.
 * Siehe die GNU General Public License für weitere Details.
 * 
 * Sie sollten eine Kopie der GNU General Public License zusammen mit diesem
 * Programm erhalten haben. Wenn nicht, siehe <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package org.objectspace.rfid.library.taghandle;

import org.apache.commons.configuration2.AbstractConfiguration;
import org.bytedeco.javacpp.opencv_core.IplImage;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.javacv.FrameGrabber.Exception;
import org.bytedeco.javacv.VideoInputFrameGrabber;
import static org.bytedeco.javacpp.opencv_core.*;
import static org.bytedeco.javacpp.opencv_imgcodecs.*;

/**
 * @author Juergen Enge
 *
 */
public class WebCamThread implements Runnable {

	private volatile boolean running = true;
	private volatile String save = null;

	/**
	 * @param config
	 * 
	 */
	public WebCamThread(AbstractConfiguration config) {
		this.config = config;
		canvas = new CanvasFrame(config.getString("taghandle.camera.windowtitle"));
		canvas.setLocation(config.getInt("taghandle.camera.window.posx", 0), config.getInt("taghandle.camera.window.posy", 0));
		canvas.setSize(config.getInt("taghandle.camera.window.width", 640), config.getInt("taghandle.camera.window.height", 480));
		sleep = config.getInt("taghandle.camera.sleep", 50);
		transpose = config.getBoolean("taghandle.camera.transpose", false);
		flip = config.getInt("taghandle.camera.flip", -2);
		cropX = config.getInt("taghandle.camera.crop.x", 0);
		cropY = config.getInt("taghandle.camera.crop.y", 0);
		cropWidth = config.getInt("taghandle.camera.crop.width", 1080);
		cropHeight = config.getInt("taghandle.camera.crop.height", 1900);

		converter = new OpenCVFrameConverter.ToIplImage();
	}

	public void dispose() {
		running = false;
		canvas.dispose();
	}

	public synchronized void storeImage(String filename) throws Exception {
		if (frame == null)
			throw new Exception("no frame available");
		IplImage image = converter.convert(frame);
		if (image != null) {
			IplImage tImage = null;
			
			// transpose
			if( transpose ) {
				tImage = cvCreateImage(cvSize(image.height(), image.width()), image.depth(), image.nChannels());
				cvTranspose( image, tImage );
			}
			else {
				tImage = cvCloneImage( image );
			}
			
			// flip
			if( flip == 0 || flip == 1 || flip == -1 ) {
				cvFlip( tImage, null, flip );
			}
			
			// crop
			cvSetImageROI(tImage, cvRect(cropX, cropY, cropWidth, cropHeight));
			
			
			cvSaveImage(filename, tImage);
			
			cvReleaseImage(tImage);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		FrameGrabber grabber = new VideoInputFrameGrabber(config.getInt("taghandle.camera.id", 0));
		grabber.setImageWidth(config.getInt("taghandle.camera.width", 640));
		grabber.setImageHeight(config.getInt("taghandle.camera.heigt", 480));
		try {
			grabber.start();
		} catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return;
		}
		boolean firstFrame = true;
		try {
			while ((frame = grabber.grab()) != null && running) {
				// cvFlip(img, img, 1);// l-r = 90_degrees_steps_anti_clockwise
				// cvSaveImage((i++)+"-capture.jpg", img);
				// show image on window
				if (!canvas.isValid())
					break;
				if (canvas.isVisible()) {
					canvas.showImage(frame);
					if (firstFrame) {
						canvas.setSize(config.getInt("taghandle.camera.window.width", 640), config.getInt("taghandle.camera.window.height", 480));
						firstFrame = false;
					}
				}

				Thread.sleep(sleep);
			}
		} catch (Exception | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				grabber.stop();
			} catch (Exception e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
	}

	OpenCVFrameConverter.ToIplImage converter;
	CanvasFrame canvas;
	Frame frame;
	int sleep;
	boolean transpose;
	int flip;
	int cropX, cropY, cropWidth, cropHeight;
	AbstractConfiguration config;

}
