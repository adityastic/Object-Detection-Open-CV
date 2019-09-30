package application;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class MotionDetectionController
{
	// FXML camera button
	@FXML
	private Button cameraButton;
	// the FXML area for showing the current frame
	@FXML
	private ImageView originalFrame;
	// the FXML area for showing the mask

	// a timer for acquiring the video stream
	private ScheduledExecutorService timer;
	// the OpenCV object that performs the video capture
	private VideoCapture capture = new VideoCapture();
	// a flag to change the button behavior
	private boolean cameraActive;
	
	// property for object binding
	private ObjectProperty<String> hsvValuesProp;
		
	@FXML
	private void startCamera()
	{
		// bind a text property with the string containing the current range of
		// HSV values for object detection
		hsvValuesProp = new SimpleObjectProperty<>();
				
		// set a fixed width for all the image to show and preserve image ratio
		this.imageViewProperties(this.originalFrame, 1000);
		
		if (!this.cameraActive)
		{
			// start the video capture
			this.capture.open(0);
			
			// is the video stream available?
			if (this.capture.isOpened())
			{
				this.cameraActive = true;
				
				// grab a frame every 33 ms (30 frames/sec)
				Runnable frameGrabber = new Runnable() {
					
					@Override
					public void run()
					{
						// effectively grab and process a single frame
						Mat frame = grabFrame();
						// convert and show the frame
						Image imageToShow = Utils.mat2Image(frame);
						updateImageView(originalFrame, imageToShow);
					}
				};
				
				this.timer = Executors.newSingleThreadScheduledExecutor();
				this.timer.scheduleAtFixedRate(frameGrabber, 0, 16 , TimeUnit.MILLISECONDS);
				
				// update the button content
				this.cameraButton.setText("Stop Camera");
			}
			else
			{
				// log the error
				System.err.println("Failed to open the camera connection...");
			}
		}
		else
		{
			// the camera is not active at this point
			this.cameraActive = false;
			// update again the button content
			this.cameraButton.setText("Start Camera");
			
			// stop the timer
			this.stopAcquisition();
		}
	}
	
	private Mat grabFrame()
	{
		Mat frame = new Mat();
		
		// check if the capture is open
		if (this.capture.isOpened())
		{
			try
			{
				// read the current frame
				this.capture.read(frame);
				
				// if the frame is not empty, process it
				if (!frame.empty())
				{
					// init
					Mat blurredImage = new Mat();
					Mat hsvImage = new Mat();
					Mat mask = new Mat();
					Mat morphOutput = new Mat();
					
					// remove some noise
					Imgproc.blur(frame, blurredImage, new Size(7, 7));
					
					// convert the frame to HSV
					Imgproc.cvtColor(blurredImage, hsvImage, Imgproc.COLOR_BGR2HSV);
					
					// get thresholding values from the UI
					// remember: H ranges 0-180, S and V range 0-255
					Scalar minValues = new Scalar(84.19, 113.1,
							141);
					Scalar maxValues = new Scalar(180, 255,
							255);
					
					// show the current selected HSV range
					String valuesToPrint = "Hue range: " + minValues.val[0] + "-" + maxValues.val[0]
							+ "\tSaturation range: " + minValues.val[1] + "-" + maxValues.val[1] + "\tValue range: "
							+ minValues.val[2] + "-" + maxValues.val[2];
					Utils.onFXThread(this.hsvValuesProp, valuesToPrint);
					
					// threshold HSV image to select soap
					Core.inRange(hsvImage, minValues, maxValues, mask);
					// show the partial output
					
					// morphological operators
					// dilate with large element, erode with small ones
					Mat dilateElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(24, 24));
					Mat erodeElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(12, 12));
					
					Imgproc.erode(mask, morphOutput, erodeElement);
					Imgproc.erode(morphOutput, morphOutput, erodeElement);
					
					Imgproc.dilate(morphOutput, morphOutput, dilateElement);
					Imgproc.dilate(morphOutput, morphOutput, dilateElement);
					
					
					// find the soap contours and show them
					frame = this.findAndDrawBalls(morphOutput, frame);

				}
				
			}
			catch (Exception e)
			{
				// log the (full) error
				System.err.print("Exception during the image elaboration...");
				e.printStackTrace();
			}
		}
		
		return frame;
	}
	
	int lastX = 0;
	int lastY = 0;
	int offX = 100;
	int offY = 100;

	void sendDifference(int x,int y,int lx,int ly)
	{
		int dispx,dispy;
		
		dispx = lx - x;
		dispy = ly - y;
	
		
		int check = greaterintwo(Math.abs(dispx), Math.abs(dispy));
		
		if(check == Math.abs(dispx))
		{
			if(dispx>0)
			{
				System.out.println("Right");
			     
			}else if (dispx < 0)
			{
				System.out.println("Left");
			}
		}else if(check == Math.abs(dispy))
		{
			if(dispy>0)
			{
				System.out.println("Up");
			}else if (dispy < 0)
			{
				System.out.println("Down");
			}
		}
	}
	
	int greaterintwo(int a,int b)
	{
		if(a>b)
			return a;
		else if(b>a)
			return b;
		else
			return -1;
	}
	
	private Mat findAndDrawBalls(Mat maskedImage, Mat frame)
	{
		// init
		List<MatOfPoint> contours = new ArrayList<>();
		
		Mat hierarchy = new Mat();
		
		// find contours
		Imgproc.findContours(maskedImage, contours, hierarchy, Imgproc.RETR_CCOMP, Imgproc.CHAIN_APPROX_SIMPLE);
		
		if(contours.size() == 0)
		{
			lastX =0;
			lastY =0;
		}
		
		for(int i=0; i< contours.size() && i< 1 ;i++)
        {
            		Rect rect = Imgproc.boundingRect(contours.get(i));
				if(lastX==0 && lastY==0)
				{
	            		lastX =  rect.x;
	            		lastY =  rect.y;
				}
				
                if ((rect.height > 100 && rect.height < 300) && (rect.width > 100 && rect.width < 300))
                {
                    if( !(Math.abs(rect.x - lastX) < offX && Math.abs(rect.y - lastY) < offY))
                    {
                    	
                    		sendDifference(rect.x,rect.y,lastX,lastY);
//                    	
//	                    	if((rect.y - lastY -offY) >= 50)
//	                		{
//	                			System.out.println("Down");
//	                		}
//	                		else if((rect.y - lastY -offY) > -300 && (rect.y - lastY -offY) < 0)
//	                		{
//	                			System.out.println("Up");
//	                		}
//	                		else if((rect.x - lastX -offX) >= 50)
//	                		{
//	                    		System.out.println("Left");
//	                		}else if((rect.x - lastX -offX) > -50 && (rect.x - lastX -offX) < 0)
//	                		{
//	                    		System.out.println("Right");
//	                		}
                    }
                    lastX =  rect.x;
                    lastY =  rect.y;
                    Imgproc.rectangle(frame, new Point(rect.x,rect.y), new Point(rect.x+rect.width,rect.y+rect.height),new Scalar(0,0,255));
                }
        }
		
		// if any contour exist...
//		if (hierarchy.size().height > 0 && hierarchy.size().width > 0)
//		{
//			// for each contour, display it in blue
//			for (int idx = 0; idx >= 0; idx = (int) hierarchy.get(0, idx)[0])
//			{
//				
//				Imgproc.drawContours(frame, contours, idx, new Scalar(0, 0, 255));
//			    
//				
//			}
//		}
//		
		return frame;
	}
	
	private void imageViewProperties(ImageView image, int dimension)
	{
		// set a fixed width for the given ImageView
		image.setFitWidth(dimension);
		// preserve the image ratio
		image.setPreserveRatio(true);
	}

	private void stopAcquisition()
	{
		if (this.timer!=null && !this.timer.isShutdown())
		{
			try
			{
				// stop the timer
				this.timer.shutdown();
				this.timer.awaitTermination(33, TimeUnit.MILLISECONDS);
			}
			catch (InterruptedException e)
			{
				// log any exception
				System.err.println("Exception in stopping the frame capture, trying to release the camera now... " + e);
			}
		}
		
		if (this.capture.isOpened())
		{
			// release the camera
			this.capture.release();
		}
	}
	
	private void updateImageView(ImageView view, Image image)
	{
		Utils.onFXThread(view.imageProperty(), image);
	}

	protected void setClosed()
	{
		this.stopAcquisition();
	}
	
}
