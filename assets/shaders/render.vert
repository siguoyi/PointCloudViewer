/*OpenGL ES 2.0*/

attribute vec3 vPosition; 
attribute vec3 vNormal; 
attribute vec2 vTexture;
attribute vec4 a_color;

uniform mat4 modelViewMatrix; 
uniform mat4 mMVPMatrix;
uniform mat4 mModelMatrix;
uniform mat4 mViewMatrix;
uniform vec4 lightPosVec;
uniform float lightingEnabled;
uniform float texturesEnabled;
uniform float colorEnabled;
uniform vec3 vBrightness;

varying vec4 v_color;
varying float lightsEnabled;
varying float texEnabled;
varying float colEnabled;
varying vec3 lightPosEye;
varying vec3 normalEye; 
varying vec3 vertEye;
varying vec2 texCoords;

void main() { 

	/*Calculate normal matrix*/
	vec4 normal = vec4(vNormal, 0.0);
	
	normalEye = normalize(vec3(modelViewMatrix * normal));
	
	lightsEnabled = lightingEnabled;
	
	texEnabled = texturesEnabled;
	
	colEnabled = colorEnabled;
	
	texCoords = vTexture;
	
	lightPosEye = vec3(mViewMatrix * lightPosVec);
	
	vertEye = vec3(modelViewMatrix * vec4(vPosition, 1.0));
	
	gl_PointSize = 5.0;
		
	v_color = a_color;
	
	gl_Position = mMVPMatrix * vec4(vPosition, 1.0);
}