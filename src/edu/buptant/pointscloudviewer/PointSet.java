package edu.buptant.pointscloudviewer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import android.util.Log;

public class PointSet {

	private static volatile ArrayList<Float> vertArray, normArray, colArray, texArray;
	private volatile float[] maxVerts, minVerts;
	private volatile float[] center;
	private float boundingRadius = 0;
	private String textureFile = null;
	private volatile boolean textureEnabled = false;
	
	private volatile int numVerts = 0, numNorms = 0;

	public ArrayList<Mesh> meshList;
	private volatile int progress = 0;
	private volatile int parsing_progress_max;

	enum ElementType {
		MESHES, VERTICES, TEXTURES, NORMALS, FACES
	};

	ElementType element;
	
	private String modelPath;
	
	public PointSet(String filePath, String textureFile){
		this.modelPath = filePath;
		this.textureFile = textureFile;

		if (!this.textureFile.equals("textures_disabled")) {
			textureEnabled = true;
		} else {
			textureEnabled = false;
		}

		vertArray = new ArrayList<Float>();
		normArray = new ArrayList<Float>();
		colArray = new ArrayList<Float>();
		texArray = new ArrayList<Float>();
		meshList = new ArrayList<Mesh>();

		maxVerts = new float[3];
		minVerts = new float[3];
		center = new float[3];
		
		try {
			LineNumberReader temp = new LineNumberReader(new FileReader(new File(modelPath)));
			temp.skip(Long.MAX_VALUE);
			parsing_progress_max = temp.getLineNumber();
			temp.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	public boolean parse() {
		readFileByLine(modelPath, vertArray, normArray, colArray, ",");
		Log.d("readFileByLine", "Read finished ");
		
		numVerts = vertArray.size()/3;
		numNorms = normArray.size()/3;
			
		Log.d("ps parse", "V: " + numVerts + " N: " + numNorms);
		
		generateArrays();

		//get bounding radius
		boundingRadius = (float) Math.sqrt((maxVerts[0] - center[0]) * (maxVerts[0] - center[0]) 
				+ (maxVerts[1] - center[1]) * (maxVerts[1] - center[1]) 
				+ (maxVerts[2] - center[2]) * (maxVerts[2] - center[2]));
		
		return true;
	}

	public Mesh[] getMeshes() {
		Mesh[] meshes = meshList.toArray(new Mesh[meshList.size()]);
		for (int c = 0; c < meshes.length; c++) {
			meshes[c].setBoundingSphere(center, boundingRadius);
			meshes[c].setMaterial(textureFile);
			if (textureEnabled) {
				meshes[c].enableTextures();
			}
		}
		return meshes;
	}

	private void generateArrays() {

		Mesh m = new Mesh();

		// add vertices
		int size = vertArray.size(), normSize = normArray.size(), texSize = texArray
				.size(), numVerts = size / 3, colSize = colArray.size();
		float[] ff = new float[size], nn = new float[normSize], 
				tt = new float[texSize], cc = new float[colSize];

		for (int i = 0; i < numVerts; i++) {
			for (int j = 0; j < 3; j++) {
				ff[j + 3 * i] = vertArray.get(j + 3 * i);
				if (normSize > 0 && (j + 3 * i) < normSize)
					nn[j + 3 * i] = normArray.get(j + 3 * i);
				float value = vertArray.get(j + 3 * i);
				minVerts[j] = (minVerts[j] > value) ? value : minVerts[j];
				maxVerts[j] = (maxVerts[j] < value) ? value : maxVerts[j];
				center[j] += value;
			}
			for (int k = 0; k < 2; k++) {
				if (texSize > 0 && (k + 2 * i) < texSize) {
					tt[k + 2 * i] = texArray.get(k + 2 * i);
				} else
					break;
			}
			for (int n = 0; n < 4; n++) {
				cc[n + 4 * i] = colArray.get(n + 4 * i);
			}
		}
		
		// determine center point of mesh(es)
		for (int t = 0; t < 3; t++) {
			center[t] = center[t]/numVerts;
		}

		m.meshVerts = ff;
		m.meshNormals = nn;
		m.meshTex = tt;
		m.pointsColor = cc;
		meshList.add(m);
		vertArray.clear();
		texArray.clear();
		normArray.clear();
		colArray.clear();
	}
	
	public void readFileByLine(String inPath, ArrayList<Float> vertArray, 
			ArrayList<Float> normArray, ArrayList<Float> colArray, String delimiter){
		File in = new File(inPath);
		BufferedReader buf = null;
		try {
			buf = new BufferedReader(new FileReader(in));
			String line = null;
			progress = 0;
			while((line = buf.readLine()) != null) {	
				String[] arr = line.trim().split(delimiter);
				if (arr.length == 9) {
					for(int i = 0; i < 3; i++){
						vertArray.add(Float.parseFloat(arr[i]));
						normArray.add(Float.parseFloat(arr[i+3]));
						colArray.add(Float.parseFloat(arr[i+6])/256);
					}
					colArray.add(1f);
				}
				progress++;
			}
		} catch (Exception e) {
			e.getStackTrace();
		} finally {
			if (buf != null) {
				try {
					buf.close();
				} catch (IOException e) {
					e.getStackTrace();
				}
			}
		}
	}

	public int getProgress() {
		return progress;
	}

	public int getProgressMax() {
		return parsing_progress_max;
	}
}
