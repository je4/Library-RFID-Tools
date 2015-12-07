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
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacpp.opencv_core.CvSeq;
import org.bytedeco.javacpp.opencv_core.CvSize;
import org.bytedeco.javacpp.opencv_core.IplImage;
import org.bytedeco.javacpp.opencv_core.Size2f;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.javacv.FrameGrabber.Exception;
import org.bytedeco.javacv.VideoInputFrameGrabber;
import static org.bytedeco.javacpp.opencv_core.*;
import static org.bytedeco.javacpp.opencv_imgcodecs.*;
import static org.bytedeco.javacpp.opencv_imgproc.*;
import static org.bytedeco.javacpp.opencv_highgui.*;

/**
 * @author Juergen Enge
 *
 */
public class WebCamThread implements Runnable {

	private volatile boolean running = true;
	private MainDialog md;

	// private static final int CV_RGB2GRAY = 7;

	/**
	 * @param config
	 * @param md
	 * 
	 */
	public WebCamThread(AbstractConfiguration config, MainDialog md) {
		this.config = config;
		this.md = md;
		canvas = new CanvasFrame(config.getString("taghandle.camera.windowtitle"));
		canvas.setLocation(config.getInt("taghandle.camera.window.posx", 0),
				config.getInt("taghandle.camera.window.posy", 0));
		canvas.setSize(config.getInt("taghandle.camera.window.width", 640),
				config.getInt("taghandle.camera.window.height", 480));
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
			if (transpose) {
				tImage = cvCreateImage(cvSize(image.height(), image.width()), image.depth(), image.nChannels());
				cvTranspose(image, tImage);
			} else {
				tImage = cvCloneImage(image);
			}

			// flip
			if (flip == 0 || flip == 1 || flip == -1) {
				cvFlip(tImage, null, flip);
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
			e1.printStackTrace();
			return;
		}
		boolean firstFrame = true;
		try {
			// CvSeq squares = new CvSeq();
			while ((frame = grabber.grab()) != null && running) {
				if (!canvas.isValid())
					break;
/*				
				CvMemStorage storage = CvMemStorage.create();

				IplImage img = converter.convert(frame);
				CvSeq poly = findSquares4(img, storage, md.maxCanny);

				CvBox2D rect = cvMinAreaRect2(poly);
*/				
/*
				RotatedRect rr = new RotatedRect(rect);
				float angle = rr.angle();
				Size2f rSize = rr.size();
				
				if( rect.angle() < -45.0 ) {
					angle += 90.0;
					rSize = new Size2f( rSize.height(), rSize.width());
				}
				Mat M = getRotationMatrix2D(rr.center(), angle, 1.0 );
				Mat rotated = new Mat();
				Mat src = new Mat( img );
				warpAffine(src, rotated, M, src.size());
				Mat cropped = new Mat();
				getRectSubPix(rotated, new Size(rSize), rr.center(), cropped);
				
				IplImage n = new IplImage(cropped);
*/				
/*				
				float[] pts = new float[8];
				cvBoxPoints(rect, pts);
				for (int i = 0; i < 4; i++) {
					CvPoint p1 = new CvPoint( (int)(pts[i*2]), (int)(pts[i*2+1]));
					CvPoint p2 = new CvPoint( (int)(pts[((i+1)%4)*2]), (int)(pts[((i+1)%4)*2+1]));
					cvLine( img, p1, p2, CvScalar.BLUE, 3, CV_AA, 0 );
				}				
*/				
				if (canvas.isVisible()) {
//					canvas.showImage(converter.convert(img));
					canvas.showImage(frame);
					if (firstFrame) {
						canvas.setSize(config.getInt("taghandle.camera.window.width", 640),
								config.getInt("taghandle.camera.window.height", 480));
						firstFrame = false;
					}
				}
//				cvReleaseImage(img);
//				storage.release();
				Thread.sleep(sleep);
			}
		} catch (Exception | InterruptedException e) {
			e.printStackTrace();
		} finally {
			try {
				grabber.stop();
			} catch (Exception e1) {
				e1.printStackTrace();
			}
		}
	}

	/**
	 * finds a cosine of angle between vectors from pt0->pt1 and from pt0->pt2
	 * 
	 * @param pt1
	 * @param pt2
	 * @param pt0
	 * @return
	 */
	static private double angle(CvPoint pt1, CvPoint pt2, CvPoint pt0) {
		double dx1 = pt1.x() - pt0.x();
		double dy1 = pt1.y() - pt0.y();
		double dx2 = pt2.x() - pt0.x();
		double dy2 = pt2.y() - pt0.y();

		return (dx1 * dx2 + dy1 * dy2) / Math.sqrt((dx1 * dx1 + dy1 * dy1) * (dx2 * dx2 + dy2 * dy2) + 1e-10);
	}

	/**
	 * returns sequence of squares detected on the image. the sequence is stored
	 * in the specified memory storage
	 * 
	 * @param img
	 * @param storage
	 * @return sequence of squares
	 */
	static public CvSeq findSquares4(IplImage img, CvMemStorage storage, int thresh) {
		// Java translation: moved into loop
		// CvSeq contours = new CvSeq();
		int i, c, l, N = 8;
		CvSize sz = cvSize(img.width() & -2, img.height() & -2);
		// IplImage timg = new IplImage(img);
		// cvSmooth(img, timg, CV_BLUR, 9, 9, 2, 2);
		IplImage timg = cvCloneImage(img); // make a copy of input image
		IplImage gray = cvCreateImage(sz, 8, 1);
		IplImage pyr = cvCreateImage(cvSize(sz.width() / 2, sz.height() / 2), 8, 3);
		IplImage tgray = null;
		// Java translation: moved into loop
		// CvSeq result = null;
		// double s = 0.0, t = 0.0;

		// create empty sequence that will contain points -
		// 4 points per square (the square's vertices)
//		CvSeq squares = cvCreateSeq(0, Loader.sizeof(CvSeq.class), Loader.sizeof(CvPoint.class), storage);

		CvSeq retval = null;

		// select the maximum ROI in the image
		// with the width and height divisible by 2
		cvSetImageROI(timg, cvRect(0, 0, sz.width(), sz.height()));

		// down-scale and upscale the image to filter out the noise
		cvPyrDown(timg, pyr, 7);
		cvPyrUp(pyr, timg, 7);
		tgray = cvCreateImage(sz, 8, 1);

		double largest = 0;
		// find squares in every color plane of the image
		for (c = 0; c < 3; c++) {
			// extract the c-th color plane
			cvSetImageCOI(timg, c + 1);
			cvCopy(timg, tgray);

			// try several threshold levels
			for (l = 0; l < N; l++) {
				// hack: use Canny instead of zero threshold level.
				// Canny helps to catch squares with gradient shading
				if (l == 0) {
					// apply Canny. Take the upper threshold from slider
					// and set the lower to 0 (which forces edges merging)
					cvCanny(tgray, gray, 0, thresh, 5);
					// dilate canny output to remove potential
					// holes between edge segments
					cvDilate(gray, gray, null, 1);
				} else {
					// apply threshold if l!=0:
					// tgray(x,y) = gray(x,y) < (l+1)*255/N ? 255 : 0
					cvThreshold(tgray, gray, (l + 1) * 255 / N, 255, CV_THRESH_BINARY);
				}

				// find contours and store them all as a list
				// Java translation: moved into the loop
				CvSeq contours = new CvSeq();
				cvFindContours(gray, storage, contours, Loader.sizeof(CvContour.class), CV_RETR_LIST,
						CV_CHAIN_APPROX_SIMPLE, cvPoint(0, 0));

				// test each contour
				while (contours != null && !contours.isNull()) {
					// approximate contour with accuracy proportional
					// to the contour perimeter
					// Java translation: moved into the loop
					CvSeq result = cvApproxPoly(contours, Loader.sizeof(CvContour.class), storage, CV_POLY_APPROX_DP,
							cvContourPerimeter(contours) * 0.02, 0);
					// square contours should have 4 vertices after
					// approximation
					// relatively large area (to filter out noisy contours)
					// and be convex.
					// Note: absolute value of an area is used because
					// area may be positive or negative - in accordance with the
					// contour orientation
					if (result.total() == 4 && Math.abs(cvContourArea(result, CV_WHOLE_SEQ, 0)) > 1000
							&& cvCheckContourConvexity(result) != 0) {

						// Java translation: moved into loop
						double s = 0.0, t = 0.0;

						for (i = 0; i < 5; i++) {
							// find minimum angle between joint
							// edges (maximum of cosine)
							if (i >= 2) {
								// Java translation:
								// Comment from the HoughLines.java sample code:
								// " Based on JavaCPP, the equivalent of the C
								// code:
								// CvPoint* line =
								// (CvPoint*)cvGetSeqElem(lines,i);
								// CvPoint first=line[0];
								// CvPoint second=line[1];
								// is:
								// Pointer line = cvGetSeqElem(lines, i);
								// CvPoint first = new
								// CvPoint(line).position(0);
								// CvPoint second = new
								// CvPoint(line).position(1);
								// "
								// ... so after some trial and error this seem
								// to work
								// t = fabs(angle(
								// (CvPoint*)cvGetSeqElem( result, i ),
								// (CvPoint*)cvGetSeqElem( result, i-2 ),
								// (CvPoint*)cvGetSeqElem( result, i-1 )));
								t = Math.abs(angle(new CvPoint(cvGetSeqElem(result, i)),
										new CvPoint(cvGetSeqElem(result, i - 2)),
										new CvPoint(cvGetSeqElem(result, i - 1))));
								s = s > t ? s : t;
							}
						}

						// if cosines of all angles are small
						// (all angles are ~90 degree) then write quandrange
						// vertices to resultant sequence
						if (s < 0.2) {
							boolean ignore = false;
							for (i = 0; i < 4; i++) {
								CvPoint p = new CvPoint(cvGetSeqElem(result, i));
								if (p.x() <= 0 + 2 || p.y() <= 0 + 2 || p.x() > sz.width() - 2
										|| p.y() > sz.height() - 2) {
									ignore = true;
								}
							}
							if (!ignore) {
								double a = cvContourArea(result);
								if (largest < a) {
									largest = a;
//									clearSeq(squares);
									retval = result;
									/*
									 * for (i = 0; i < 4; i++) {
									 * cvSeqPush(squares, cvGetSeqElem(result,
									 * i)); retval.position(i).put(new
									 * CvPoint(cvGetSeqElem(result, i))); }
									 */
								}
							}
						}
					}

					// take the next contour
					contours = contours.h_next();
//					cvReleaseSeq( result );

				}
			}
		}

		// release all the temporary images
		cvReleaseImage(gray);
		cvReleaseImage(pyr);
		cvReleaseImage(tgray);
		cvReleaseImage(timg);

		return retval;
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
