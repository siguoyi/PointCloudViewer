package edu.buptant.pointscloudviewer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Vector;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import edu.buptant.pointscloudviewer.GLUtils;
import edu.buptant.pointscloudviewer.Vector3;
import edu.buptant.pointscloudviewer.MainActivity.ActionIcons;
import edu.buptant.pointscloudviewer.RendererGL.RotationAxis;
import edu.buptant.timestatistic.TimeStatistics;

public class RenderFragment extends Fragment {
	private static final String TAG = RenderFragment.class.getSimpleName();

	private Context mContext;
	private CustomGLView mGLView;
	private RendererGL mRenderer;
	private RenderConfig renderConfig;
	private Menu menu;
	private float xOld = 0.0f, yOld = 0.0f;
	private String mModelPath;
	private float screenDensity = 0.0f;
	private int WINDOW_WIDTH = 0, WINDOW_HEIGHT = 0;

	private static float TOUCH_SCALE_FACTOR = 180.0f / 320.0f;
	private boolean toggleMoveLight = false, settingsCreated = false, explorerCreated = false, recentExplorerCreated = false, brightnessSliderCreated = false,
					statsCreated = false;
	private static volatile boolean doneLoading = false;
	public ParsedObj parsedModel = null;
	public PointSet pointSet = null;
	private String file_type;
	
	private static String timeStatisticPath = Environment.getExternalStorageDirectory().getAbsolutePath() 
			+ File.separator +"time_statistic";

	private static File saveTimePath = new File(timeStatisticPath);

	private volatile boolean parsingDone = false;
	volatile ProgressBar parsingProgress;
	AlertDialog settingsDialog, explorerDialog, recentDialog, progressDialog, brightnessDialog,
				statsDialog;
	
	TextView statsMessage;
	
	FileExplorerFragment explorerFrag;
	SettingsFragment settingsFrag;
	
	Thread parseThread;
	ProgressBarTask progressThread;
	
	StopWatch elapsedTime;
	
	SparseArray<Drawable> actionListItems;

	enum ProjectionView {
		ORTHO_VIEW,
		PERSPECTIVE_VIEW
	};
	
	public interface OnSettingsLaunched {
		public View launchSettings();
	}
	
	public interface OnExplorerLaunched {
		public View getExplorerView(boolean goToRecent);
	}
	
	public interface OnActionItemsReceived {
		public SparseArray<Drawable> getActionItemArray();
	}
	
	public RenderFragment(){
		
	}
	
	public void parseModelFile(String modelPath, String texturePath, final String fileType){
		Log.d("fileType", "fileType: "+fileType);
		file_type = fileType;
		mModelPath = modelPath;
		if(fileType.equals("obj")){
			parsedModel = new ParsedObj(mModelPath, texturePath);
		}else if(fileType.equals("csv")){
			pointSet = new PointSet(mModelPath, texturePath);
		}
		Log.d("filePath", " "+mModelPath);
		
//		enableFullscreen();

//		mGLView = new CustomGLView(getActivity());

		// display

		// set up loading bar
		View progressView = getActivity().getLayoutInflater().inflate(R.layout.progress_bar,
				null);
		LayoutParams params = new LayoutParams();
		params.gravity = Gravity.CENTER;
		
		progressView.setBackgroundResource(R.color.darkdark);
		progressView.setLayoutParams(params);
		progressDialog = new AlertDialog.Builder(getActivity())
		.setView(progressView)
		.setOnKeyListener(new DialogInterface.OnKeyListener() {
			
			@Override
			public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
				if (keyCode == KeyEvent.KEYCODE_BACK && 
		                event.getAction() == KeyEvent.ACTION_UP && 
		                !event.isCanceled()){
					progressThread.cancel(true);
					progressDialog.dismiss();
				}
				return false;
			}
		}).create();
		progressDialog.setCanceledOnTouchOutside(false);
		progressDialog.show();
		
		parsingProgress = (ProgressBar) progressDialog.findViewById(R.id.progress_bar_id);
		if(fileType.equals("obj")){
			parsingProgress.setMax(parsedModel.getProgressMax());
		}else if(fileType.equals("csv")){
			parsingProgress.setMax(pointSet.getProgressMax());
		}		

		progressThread = new ProgressBarTask();
		progressThread.execute();
		
		elapsedTime = new StopWatch();
		
		parseThread = new Thread(new Runnable() {
			@Override
			public void run() {
				elapsedTime.startTime();
				TimeStatistics.parseStartTime = System.currentTimeMillis();
				if(fileType.equals("obj")){
					parsedModel.parse();
					TimeStatistics.parseCompleteTime = System.currentTimeMillis();
					mGLView.loadModel(parsedModel);
				}else if(fileType.equals("csv")){
					pointSet.parse();
					TimeStatistics.parseCompleteTime = System.currentTimeMillis();
					mGLView.loadModel(pointSet);
				}
				
				try {
					mGLView.requestRender();
				} catch (Exception e) {
					e.printStackTrace();
				}
				
			}
		});
		parseThread.start();
	}
	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
	}

	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		Log.d(TAG, "RenderFragment onCreate");
		setHasOptionsMenu(true);
		settingsFrag = new SettingsFragment();
		explorerFrag = new FileExplorerFragment();
		getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		if(!saveTimePath.exists()){
			saveTimePath.mkdirs();
		}
		Bundle bundle = getArguments();
		if(bundle != null){
			String modelPath = bundle.getString(FileExplorerFragment.fileToParse),
					   texturePath = bundle.getString(FileExplorerFragment.texToParse);
			String fileType = bundle.getString("fileType");
				if(explorerDialog != null){
					explorerDialog.dismiss();
				}
				parseModelFile(modelPath, texturePath, fileType);
		}
		
	}
	
	@Override
	public View onCreateView(LayoutInflater layoutInflater, ViewGroup container, Bundle savedInstanceState){
		Log.d(TAG, "RenderFragment onCreateView");
		renderConfig = new RenderConfig();
		mGLView = new CustomGLView(getActivity());
		return mGLView;
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		Log.d(TAG, "RenderFragment onActivityCreated");
		FragmentManager fm = getFragmentManager();
		FragmentTransaction ft = fm.beginTransaction();
		ft.add(settingsFrag,"settings_tag")
		.add(explorerFrag,"explorer_tag")
		.commit();
		settingsFrag.setRenderConfig(renderConfig);
	}

	@Override
	public void onConfigurationChanged(Configuration config) {
		super.onConfigurationChanged(config);
		
		mRenderer.rotationAngle = 0f;
		WINDOW_WIDTH = getWindowWidth();
		WINDOW_HEIGHT = getWindowHeight();
		mGLView.requestRender();
	}
	
	@Override
	public void onResume(){
		super.onResume();
		if(mGLView != null) mGLView.onResume();
	}

	@Override
	public void onPause(){
		super.onPause();
		if(mGLView != null) mGLView.onPause();
	}
	
	@Override
	public void onStop() {
		super.onStop();
		doneLoading = false;
	}
	

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		// Inflate the menu; this adds items to the action bar if it is present.
		this.menu = menu;
		inflater.inflate(R.menu.action_bar_menu, menu);
	}
	 
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		// handle items selected from menu
		switch (item.getItemId()) {

		case R.id.brightness_adjust_id:{	
			Log.d("brightness", "brightness");
			LinearLayout layout = new LinearLayout(getActivity());
			LinearLayout.LayoutParams params;
			layout.setOrientation(LinearLayout.VERTICAL);

			SeekBar seekBar = new SeekBar(getActivity());
			seekBar.setProgress(50);
			seekBar.setMax(100);
			seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

					@Override
					public void onStopTrackingTouch(SeekBar seekBar) { }

					@Override
					public void onStartTrackingTouch(SeekBar seekBar) {	}

					@Override
					public void onProgressChanged(SeekBar seekBar, int progress,
												  boolean fromUser) {
						renderConfig.setBrightnessOffset(((float)progress-50)/100.0f + 0.2f);
						renderConfig.refreshRender();
					}
				});
			params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
			params.gravity = Gravity.CENTER_HORIZONTAL;
			params.setMargins(10, 20, 20, 5);

			layout.addView(seekBar, params);
			final LinearLayout finalLayout = layout;
			
			if(brightnessSliderCreated){
				brightnessDialog.show();
			}
			else {
				brightnessSliderCreated = true;
			    brightnessDialog = new AlertDialog.Builder(getActivity())
			        .setView(finalLayout).create();
			    brightnessDialog.setOnCancelListener(new DialogInterface.OnCancelListener(){
					@Override
					public void onCancel(DialogInterface dialog){
						
					}
				});
				brightnessDialog.setCanceledOnTouchOutside(true);
				Window dialogWindow = brightnessDialog.getWindow();
				WindowManager.LayoutParams dialogParams = dialogWindow.getAttributes();
				dialogWindow.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
				
				dialogParams.gravity = Gravity.BOTTOM;
				dialogParams.y = 100;
			    brightnessDialog.show();
			}
			break;
		}
		case R.id.toggle_lights_push_id: {
			MenuItem toggleLightsItem = menu.findItem(R.id.toggle_lights_push_id);
			if (renderConfig.isLightingEnabled()){
				toggleLightsItem.setIcon(ActionIcons.BRIGHTNESS_LOW.getValue()
						);
			}
			else {
				toggleLightsItem.setIcon(ActionIcons.BRIGHTNESS_HIGH.getValue());
			}
			renderConfig.setBrightnessOffset(0.0f);
			renderConfig.toggleLighting();
			break;
		}
		case R.id.projection_view_id:{
			MenuItem toggle_projectionItem = menu.findItem(R.id.projection_view_id);
			if(renderConfig.isOrthoView())
				toggle_projectionItem.setIcon(ActionIcons.PERSPECTIVE.getValue()
						);
			else
				toggle_projectionItem.setIcon(ActionIcons.ORTHO.getValue()
						);
			renderConfig.toggleProjView();
			break;
		}
		case R.id.open_file_id:{
			FragmentManager fm = getFragmentManager();
			
			if(!explorerFrag.isAdded()){
				FragmentTransaction ft = fm.beginTransaction();
				ft.add(explorerFrag,"explorer_tag").commit();
			}
			View explorerView = explorerFrag.getExplorerView(false);
			if(explorerCreated){
				explorerDialog.show();
			}
			else {
				explorerCreated = true;
				explorerDialog = new AlertDialog.Builder(getActivity()).setView(explorerView).show();
			}
			break;
		}
		case R.id.recent_files_id:{
			View recentView = explorerFrag.getExplorerView(true);
			if(explorerCreated){
				explorerDialog.setView(recentView);
				explorerDialog.show();
			}
			else {
				explorerCreated = true;
				explorerDialog = new AlertDialog.Builder(getActivity()).setView(recentView).show();
			}
			
			break;
		}
		case R.id.stats_id:{
			String loadTime, vertices, textures, normals, triangles;
			if(parsedModel != null && parsedModel.isLoaded()){
				loadTime = String.format(Locale.CANADA, "%.3f", elapsedTime.getElapsedFloat()) + " seconds";
				vertices = String.valueOf(parsedModel.getNumVertices());
				textures = String.valueOf(parsedModel.getNumTextures());
				normals = String.valueOf(parsedModel.getNumNormals());
				triangles = String.valueOf(parsedModel.getNumTriangles());
			}
			else {
				loadTime = vertices = textures = normals = triangles = "not loaded";
			}
			
			if(statsCreated){
				statsMessage.setText("Loading Time: \t\t" + loadTime + "\n"
						+ "Vertices: \t\t" + vertices + "\n"
						+ "Textures: \t\t" + textures + "\n"
						+ "Normals: \t\t" + normals + "\n"
						+ "Triangles: \t\t" + triangles);
				statsMessage.setPadding(50, 50, 50, 50);

				statsDialog.show();
			}
			else {
				
				statsCreated = true;
				statsMessage = new TextView(getActivity());
				
				statsMessage.setText("Loading Time: \t\t" + loadTime + "\n"
						+ "Vertices: \t\t" + vertices + "\n"
						+ "Textures: \t\t" + textures + "\n"
						+ "Normals: \t\t" + normals + "\n"
						+ "Triangles: \t\t" + triangles);
				statsMessage.setPadding(50, 50, 50, 50);
				
				statsDialog = new AlertDialog.Builder(getActivity())
						.setTitle("Statistics")
						.setView(statsMessage)
						.setPositiveButton("OK", new DialogInterface.OnClickListener(){
							
							@Override
							public void onClick(DialogInterface dialog, int which){
								statsDialog.dismiss();
							}
						}).create();
				statsDialog.show();
			}
			break;
		}
		case R.id.settings_push_id:{
			FragmentManager fm = getFragmentManager();
			
			if(!settingsFrag.isAdded()){
				FragmentTransaction ft = fm.beginTransaction();
				ft.add(settingsFrag,"settings_tag").commit();
			}
			View settingsView = settingsFrag.getView();
			if(settingsCreated){
				settingsDialog.show();
			}
			else {
				settingsCreated = true;
				settingsDialog = new AlertDialog.Builder(getActivity()).setTitle("Settings")
						.setView(settingsView).create();
				settingsDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
					
					@Override
					public void onDismiss(DialogInterface dialog) {
						mGLView.requestRender();
					}
				});
				settingsDialog.show();
			}
			break;
		}

		default:
			return super.onOptionsItemSelected(item);
		}
		return true;
	}

	public void enableFullscreen() {
		getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		getActivity().getWindow().clearFlags(
				WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
	}

	public void disableFullscreen() {
		getActivity().getWindow().addFlags(
				WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
		getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
	}
	
	/**
	 * This method is called from the parent activity to the RenderFragment.
	 * Sends modelfile and texturefile paths retrieved from FileExplorer.
	 * @param bundle
	 */
	public void sendToRender(Bundle bundle){
		String modelPath = bundle.getString(FileExplorerFragment.fileToParse),
			   texturePath = bundle.getString(FileExplorerFragment.texToParse);
		String fileType = bundle.getString("fileType");
		if(explorerDialog != null){
			explorerDialog.dismiss();
		}
		parseModelFile(modelPath, texturePath,fileType);
	}
	
	public int getWindowWidth(){
		Display display = getActivity().getWindow().getWindowManager().getDefaultDisplay();

		Point pt = new Point();
		display.getSize(pt);
		return pt.x;
	}
	
	public int getWindowHeight(){
		Display display = getActivity().getWindow().getWindowManager().getDefaultDisplay();

		Point pt = new Point();
		display.getSize(pt);
		return pt.y;
	}
	
	/***********************************
	 * 
	 * RenderConfig class
	 * 
	 ***********************************/
	/**
	 * RenderConfig is a helper class that groups together useful variables and
	 * settings to pass onto the GLSurfaceView.Renderer class
	 * 
	 * @author Jin
	 * 
	 */
	class RenderConfig {

		private boolean wireframeOn = false, lightingOn = true;
		public boolean projectionChanged = false;
		public float bgBrightness = 0.0f;
		private float brightness = 0.0f;
		ProjectionView pv = ProjectionView.PERSPECTIVE_VIEW;
		private RotationAxis rotAxis;

		public RenderConfig() {
			
		}

		/**
		 * Sets wireframe rendering
		 * 
		 * @param setting
		 * @return this
		 */
		public RenderConfig setWireframe(boolean setting) {
			wireframeOn = setting;
			mGLView.requestRender();
			return this;
		}

		/**
		 * Toggles wireframe rendering
		 * 
		 * @return this
		 */
		public RenderConfig toggleWireframe() {
			wireframeOn = !wireframeOn;
			mGLView.requestGLRender();
			return this;
		}

		public RenderConfig setLighting(boolean setting) {
			lightingOn = setting;
			mGLView.requestRender();
			return this;
		}

		public RenderConfig toggleLighting() {
			lightingOn = !lightingOn;
			mGLView.requestRender();
			return this;
		}
		
		public RenderConfig setProjView(ProjectionView pp){
			pv = pp;
			projectionChanged = true;
			mGLView.requestRender();
			return this;
		}
		
		public RenderConfig toggleProjView(){
			pv = (pv == ProjectionView.ORTHO_VIEW) 
					? ProjectionView.PERSPECTIVE_VIEW : ProjectionView.ORTHO_VIEW;
			projectionChanged = true;
			mGLView.requestRender();
			return this;
		}
		
		public void brightnessUp(){
			brightness += 0.1f;
			mGLView.requestRender();
		}
		
		public void brightnessDown(){
			brightness -= 0.1f;
			mGLView.requestRender();
		}
		
		/**
		 * Manually sets the offset of brightness from the model's current color value.
		 * Default offset is 0.
		 * @param set float
		 */
		public void setBrightnessOffset(float offset){
			brightness = Math.max(Math.min(offset, 0.7f), -0.3f);
		}
		
		public void refreshRender(){
			mGLView.requestRender();
		}


		/**
		 * 
		 * @return Wireframe setting
		 */
		public boolean isWireframeEnabled() {
			return wireframeOn;
		}

		public boolean isLightingEnabled() {
			return lightingOn;
		}
		public boolean isOrthoView(){
			return (pv == ProjectionView.ORTHO_VIEW) ? true : false;
		}
		public float getBrightness(){
			return brightness;
		}
		
		/**
		 * 
		 * @return which axis to base rotations from (X, Y, Z, or Free rotation)
		 */
		public RotationAxis getRotationAxis(){
			return rotAxis;
		}
		
		public void updateRotationAxis(){
			SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
			boolean freeRotation = sharedPref.getBoolean("pref_toggle_rotation", false);
			if(freeRotation) {
				rotAxis = RotationAxis.FREE_ROTATION;
			}
			else {
				rotAxis = RotationAxis.Y_AXIS;
			}
		}
	}

	/*************************************************************************
	 * 
	 * SurfaceViewGL Class
	 * 
	 * ***********************************************************************/
	class CustomGLView extends GLSurfaceView {

		private ScaleGestureDetector pinchToScale;
		private static final int INVALID_POINTER_ID = -1;

		private int activePointerId = INVALID_POINTER_ID,
				activePointerId2 = INVALID_POINTER_ID;
		private float xAngleTravelled = 0, yAngleTravelled = 0;
		private float[] mMVMatrixInv, tempVec;
		private PointF pointer1, pointer1Old, pointer2, pointer2Old, mid,
				midOld;
		private Vector3 ndcPt, ndcPtOld;
//		private Matrix3 mvMatrix;

		private boolean pointer1Down = false, pointer2Down = false;

		public CustomGLView(Context context) {
			super(context);
			Log.d(TAG, "CustomGLView");
			// Resources res = context.getResources();
			// AssetManager assetManager = context.getAssets();
			WINDOW_WIDTH = getWindowWidth();
			WINDOW_HEIGHT = getWindowHeight();

			pinchToScale = new ScaleGestureDetector(context, new ScaleListener());
			
			pointer1 = new PointF();
			pointer1Old = new PointF();
			pointer2 = new PointF();
			pointer2Old = new PointF();
			mid = new PointF();
			midOld = new PointF();
			ndcPt = new Vector3();
			ndcPtOld = new Vector3();
			mMVMatrixInv = new float[16];
			tempVec = new float[4];
//			mvMatrix = new Matrix3();

			// create OpenGL ES 2.0 context
//			setEGLContextClientVersion(2);
//			super.setEGLConfigChooser(8 , 8, 8, 8, 16, 0);
			setEGLContextClientVersion(2);
			setEGLConfigChooser(8,8,8,8,16,0);
			// set the Renderer to draw on this surfaceView
			setRenderer(mRenderer = new RendererGL(context, renderConfig));

			// render only when drawing data changes
			// frames will not be redrawn unless you call requestRender()
			setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
		}


		@Override
		public boolean onTouchEvent(MotionEvent e) {
			
			pinchToScale.onTouchEvent(e);
			final int action = e.getAction();
			final int numPointers = e.getPointerCount();
			
			int pointerIndex = e.getActionIndex();
			int pointerId = e.getPointerId(pointerIndex);
//			int maskedAction = e.getActionMasked();

			// Isolate the action using ACTION_MASK
			switch (action & MotionEvent.ACTION_MASK) {

				// the first finger touches on the screen
				case MotionEvent.ACTION_DOWN: {
					float x = pointer1.x = e.getX(pointerIndex);
					float y = pointer1.y = e.getY(pointerIndex);
					
					pointer1Down = true;
					xOld = x;
					yOld = y;
					midOld.x = mid.x;
					midOld.y = mid.y;
	
					// save the current active pointer ID
					activePointerId = e.getPointerId(pointerIndex);
					break;
				}
				
				//second finger to touch the screen
				case MotionEvent.ACTION_POINTER_DOWN: {
					if(numPointers > 2)
						break;
					float x = pointer2.x = e.getX(pointerIndex);
					float y = pointer2.y = e.getY(pointerIndex);
					pointer2Down = true;
					
					activePointerId2 = e.getPointerId(pointerIndex);
					
					mid.x = (pointer1.x + pointer2.x) / 2;
					mid.y = (pointer1.y + pointer2.y) / 2;
					
					break;
				}
	
				// either finger movement could trigger this case
				// so we must check its index using the current active
				// pointer ID
				case MotionEvent.ACTION_MOVE: {
					// this is the INDEX of the pointer, which has location info
	//				int pointerIndex = e.findPointerIndex(activePointerId);
					
					if(!mRenderer.modelLoaded){
						break;
					}
					float x = xOld, y = yOld;
					
					if(e.getPointerCount() < 2){
						x = e.getX(pointerIndex);
						y = e.getY(pointerIndex);
					}
					
					else if (e.getPointerCount() == 2 ) {
						//translate model
						
						
						//rotate model about Z-axis
						
						/*ndcPt.setX((2 * x / WINDOW_WIDTH) - 1);
						ndcPt.setY(-((2 * y / WINDOW_HEIGHT) - 1));
						float xy = ndcPt.getX() * ndcPt.getX() + 
								 ndcPt.getY() * ndcPt.getY();
						if(xy >= 1.0f) ndcPt.normalize();
						ndcPt.setZ((float)Math.sqrt(1 - xy));
						ndcPt.normalize();
						
						ndcPtOld.setX((2 * xOld / WINDOW_WIDTH) - 1);
						ndcPtOld.setY(-((2 * yOld / WINDOW_HEIGHT) - 1));
						float xyOld = ndcPtOld.getX() * ndcPtOld.getX() + 
								 ndcPtOld.getY() * ndcPtOld.getY();
						if(xyOld >= 1.0f) ndcPtOld.normalize();
						ndcPtOld.setZ((float) Math.sqrt(1 - xyOld));
						ndcPtOld.normalize();*/
//						requestRender();
					}
					// don't register single touch inputs when pinchToScale gesture
					// processing is happening
					if (!pinchToScale.isInProgress() && e.getPointerCount() == 1) {
						float dx = x - xOld;
						float dy = y - yOld;
	
						// if switch is toggled on, touch only affects the lightball
						if (toggleMoveLight) {
							mRenderer.lightX += (dx * TOUCH_SCALE_FACTOR * 0.05);
							mRenderer.lightY += (-dy * TOUCH_SCALE_FACTOR * 0.05);
						} 
						else {
							RotationAxis rotationType = renderConfig.getRotationAxis();
							if(rotationType == RotationAxis.Y_AXIS){
								mRenderer.xAngle += (dy * TOUCH_SCALE_FACTOR) % 360.0f;
								mRenderer.yAngle += (dx * TOUCH_SCALE_FACTOR) % 360.0f;
							}
							
							//if free rotation, convert 2D viewport coordinates to
							// 3D Normalized Device Coordinates
							else if(rotationType == RotationAxis.FREE_ROTATION)
							{
								if(dx != 0 || dy != 0) {
									ndcPt.setX((2 * x / WINDOW_WIDTH) - 1);
									ndcPt.setY(-((2 * y / WINDOW_HEIGHT) - 1));
									float xy = ndcPt.getX() * ndcPt.getX() + ndcPt.getY() * ndcPt.getY();
									
									if(xy >= 1.0f) { 
										ndcPt.normalize();
									}
									ndcPt.setZ(ndcPt.getMissingZ());
									ndcPt.normalize();
									
									
									ndcPtOld.setX((2 * xOld / WINDOW_WIDTH) - 1);
									ndcPtOld.setY(-((2 * yOld / WINDOW_HEIGHT) - 1));
									float xyOld = ndcPtOld.getX() * ndcPtOld.getX() + ndcPtOld.getY() * ndcPtOld.getY();
									
									if(xyOld >= 1.0f) {
										ndcPtOld.normalize();
									}
									ndcPtOld.setZ(ndcPtOld.getMissingZ());
									ndcPtOld.normalize();
									
									Matrix.invertM(mMVMatrixInv, 0, mRenderer.mMVMatrix, 0);
									Matrix.multiplyMV(tempVec, 0, mMVMatrixInv, 0, 
											GLUtils.crossProduct(ndcPtOld, ndcPt).getVector4(0), 0);
									mRenderer.rotationAxis.setNewVector(tempVec);
									mRenderer.rotationAngle = -3.0f * (float) Math.acos(
											Math.max(-1.0000000d,
													Math.min(1.0000000d, GLUtils.dotProduct(ndcPtOld, ndcPt))
											));
									if(Float.isNaN(mRenderer.rotationAngle)){
										android.util.Log.e("NUMBER_ERROR", "mRenderer.rotationAngle has a wonky value");
									}
								}
								else {
//									mRenderer.rotationAxis.setVector(0f, 1f, 0f);
									mRenderer.rotationAngle = 0.0f;
								}
							}
						} 
						requestRender();
					}
					
					
	
					xOld = x;
					yOld = y;
//					ndcPtOld = ndcPt;
					midOld.x = mid.x;
					midOld.y = mid.y;
					break;
				}
	
				// when first finger leaves touchscreen, invalidate current pointer
				case MotionEvent.ACTION_UP: {
					activePointerId = INVALID_POINTER_ID;
					xAngleTravelled = mRenderer.xAngle;
					yAngleTravelled = mRenderer.yAngle;
					pointer1Down = false;
					midOld.x = mid.x;
					midOld.y = mid.y;
					break;
				}
	
				case MotionEvent.ACTION_CANCEL: {
	//				if(e.getPointerId(e.getActionIndex()) == activePointerId)
						activePointerId = INVALID_POINTER_ID;
	//				else
						activePointerId2 = INVALID_POINTER_ID;
						
						mRenderer.rotationAngle = 0.0f;
					break;
				}
	
				// second finger leaves touchscreen
				case MotionEvent.ACTION_POINTER_UP: {		
					if(numPointers > 2)
						break;
					
					// compare the pointer that left, to the current active
					// pointer
					//if first finger left screen, set the activePointer
					// to the remaining pointer
					if (pointerId == activePointerId) {
						int newPointerIndex = (pointerIndex == 0) ? 1 : 0;
						xOld = e.getX(newPointerIndex);
						yOld = e.getY(newPointerIndex);
						activePointerId = e.getPointerId(newPointerIndex);
						activePointerId2 = INVALID_POINTER_ID;
					} 
					
					//second finger left screen
					else if(pointerId == activePointerId2){
						int newPointerIndex = (pointerIndex == 0) ? 1 : 0;
						xOld = e.getX(newPointerIndex);
						yOld = e.getY(newPointerIndex);
						activePointerId = e.getPointerId(newPointerIndex);
						activePointerId2 = INVALID_POINTER_ID;
					}
					break;
				}
			}

			return true;
		}
		
		// Listener class for pinch-zoom gestures
		private class ScaleListener extends
				ScaleGestureDetector.SimpleOnScaleGestureListener {
			
			@Override
			public boolean onScale(ScaleGestureDetector det) {
				if (toggleMoveLight) {
					mRenderer.pinchLightDist *= det.getScaleFactor();
					// mRenderer.pinchLightDist = Math.min(
					// Math.max(-5.0f, mRenderer.pinchLightDist), 30.0f);
				} else {
					mRenderer.pinchScaleFactor *= det.getScaleFactor();
					mRenderer.pinchScaleFactor = Math.min(
							Math.max(0.01f, mRenderer.pinchScaleFactor), 25.0f);
				}
				mRenderer.rotationAngle = 0f;
				requestRender();
				return true;
			}

		}
		
		

		public void loadModel(PointSet pointSet){
			mRenderer.loadModel(pointSet);
		}

		public void loadModel(ParsedObj obj) {
			mRenderer.loadModel(obj);
		}

		public void requestGLRender() {
			requestRender();
		}
		
	}

	/*************************************************************************
	 * 
	 * Progress Bar
	 * 
	 * ***********************************************************************/

	public class ProgressBarTask extends AsyncTask<Void, Integer, Void> {
		
		public void saveToSDcard(){
			String filename = "time_statistic.txt";
			String filepath = timeStatisticPath + File.separator + filename;
			File file = new File(filepath);
			if(!file.exists()){
				try {
					file.createNewFile();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			long parseTime = TimeStatistics.parseCompleteTime - TimeStatistics.parseStartTime;
			long loadTime = TimeStatistics.loadCompleteTime - TimeStatistics.loadStartTime;
			String s = parseTime + "\t" + loadTime + "\n";
			try {
//				FileOutputStream fos = getActivity().openFileOutput(filename, Context.MODE_APPEND);
				FileOutputStream fos = new FileOutputStream(file, true);
				fos.write(s.getBytes());
				fos.flush();
				fos.close();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		@Override
		protected void onPreExecute() {
			Toast.makeText(getActivity(), "Loading Mesh...",
					Toast.LENGTH_SHORT).show();
			TimeStatistics.loadStartTime = System.currentTimeMillis();
			TextView text = (TextView) progressDialog.findViewById(R.id.loading_text_id);
			text.setTextSize(16);
			text.setTextColor(Color.argb(255, 255, 255, 255));
			text.setText("Parsing");
		}

		@Override
		protected Void doInBackground(Void... params) {
			int currentProgress = 0;
			int maxProgress = 0;
			if(file_type.equals("obj")){
				currentProgress = parsedModel.getProgress();
				maxProgress = parsedModel.getProgressMax();
			}else if(file_type.equals("csv")){
				currentProgress = pointSet.getProgress();
				maxProgress = pointSet.getProgressMax();
			}
			

			while (currentProgress < maxProgress) {

				publishProgress(currentProgress);

				SystemClock.sleep(100);
				if(file_type.equals("obj")){
					currentProgress = parsedModel.getProgress();
					maxProgress = parsedModel.getProgressMax();
				}else if(file_type.equals("csv")){
					currentProgress = pointSet.getProgress();
					maxProgress = pointSet.getProgressMax();
				}
			}
			return null;
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			parsingProgress.setProgress(values[0]);
			
		}

		@Override
		protected void onPostExecute(Void Result) {
			elapsedTime.stopTime();
			TimeStatistics.loadCompleteTime = System.currentTimeMillis();
			Toast.makeText(getActivity(), "Done!\nLoading Time: "+ String.format("%.3f",elapsedTime.getElapsedFloat()) + " seconds", Toast.LENGTH_LONG)
					.show();
			saveToSDcard();
			TextView text = (TextView) progressDialog.findViewById(R.id.loading_text_id);
			text.setTextSize(16);
			text.setText("Done!");
//			setContentView(mGLView);
			doneLoading = true;
			mGLView.requestRender();
			progressDialog.dismiss();
		}

	}
}
