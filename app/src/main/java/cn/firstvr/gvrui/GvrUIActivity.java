package cn.firstvr.gvrui;

import android.app.Activity;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.util.Log;

import com.google.vr.sdk.base.AndroidCompat;
import com.google.vr.sdk.base.Eye;
import com.google.vr.sdk.base.GvrActivity;
import com.google.vr.sdk.base.GvrView;
import com.google.vr.sdk.base.HeadTransform;
import com.google.vr.sdk.base.Viewport;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;

import static cn.firstvr.gvrui.Utils.checkGLError;

/**
 *
 * Created by zhuangqianliu on 2016/11/30.
 */

public class GvrUIActivity extends GvrActivity implements GvrView.StereoRenderer{
    private String TAG=getClass().getSimpleName();

    private static final float CAMERA_Z = 0.01f;
    private static final float MIN_MODEL_DISTANCE = 3.0f;
    private static final float MAX_MODEL_DISTANCE = 7.0f;
    private static final float Z_NEAR = 0.1f;
    private static final float Z_FAR = 100.0f;
    private static final int COORDS_PER_VERTEX = 3;


    private static final float TIME_DELTA = 0.3f;

    private final float[] lightPosInEyeSpace = new float[4];
    private static final float[] LIGHT_POS_IN_WORLD_SPACE = new float[] {0.0f, 2.0f, 0.0f, 1.0f};//在上方

    private FloatBuffer cubeVertices;
    private FloatBuffer cubeNormals;

    private int cubeProgram;
    private int cubePositionParam;
    private int cubeNormalParam;
    private int cubeColorParam;
    private int cubeModelParam;
    private int cubeModelViewParam;
    private int cubeModelViewProjectionParam;
    private int cubeLightPosParam;


    private float[] view;

    private float[] camera;

    private float[] modelView;
    private float[] modelViewProjection;

    protected float[] modelCube;
    protected float[] modelPosition;





    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gvr_ui);
        initGvrView();

        camera = new float[16];
        view = new float[16];

        modelCube = new float[16];
        modelPosition = new float[] {0.0f, 0.0f, -MAX_MODEL_DISTANCE / 2.0f};

        modelViewProjection = new float[16];
        modelView = new float[16];
    }

    private void initGvrView() {
        GvrView gvrView = (GvrView) findViewById(R.id.gvr_view);
        gvrView.setEGLConfigChooser(8, 8, 8, 8, 16, 8);
        gvrView.setRenderer(this);
        //gvrView.setTransitionViewEnabled(true);
        if (gvrView.setAsyncReprojectionEnabled(true)) {
            AndroidCompat.setSustainedPerformanceMode(this, true);
        }
        setGvrView(gvrView);
    }
    /***
     * 在这里创建3d环境所需要的buffers, OpenGL 环境使用ByteBuffer
     * @param eglConfig 创建surface 所使用的配置
     *
     */
    @Override
    public void onSurfaceCreated(EGLConfig eglConfig) {
        Log.i(TAG, "onSurfaceCreated");
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 0.5f); // Dark background so text shows up well.
        //立方体顶点
        ByteBuffer bbVertices = ByteBuffer.allocateDirect(WorldLayoutData.CUBE_COORDS.length * 4);
        bbVertices.order(ByteOrder.nativeOrder());
        cubeVertices = bbVertices.asFloatBuffer();
        cubeVertices.put(WorldLayoutData.CUBE_COORDS);
        cubeVertices.position(0);

        ByteBuffer bbNormals = ByteBuffer.allocateDirect(WorldLayoutData.CUBE_NORMALS.length * 4);
        bbNormals.order(ByteOrder.nativeOrder());
        cubeNormals = bbNormals.asFloatBuffer();
        cubeNormals.put(WorldLayoutData.CUBE_NORMALS);
        cubeNormals.position(0);

        int vertexShader = Utils.loadGLShader(GLES20.GL_VERTEX_SHADER,Utils.readRawTextFile(this,R.raw.light_vertex));
        int passthroughShader = Utils.loadGLShader(GLES20.GL_FRAGMENT_SHADER, Utils.readRawTextFile(this,R.raw.passthrough_fragment));

        cubeProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(cubeProgram, vertexShader);
        GLES20.glAttachShader(cubeProgram, passthroughShader);
        GLES20.glLinkProgram(cubeProgram);
        GLES20.glUseProgram(cubeProgram);
        checkGLError("Cube program");

        cubePositionParam = GLES20.glGetAttribLocation(cubeProgram, "a_Position");
        cubeNormalParam = GLES20.glGetAttribLocation(cubeProgram, "a_Normal");
        cubeColorParam = GLES20.glGetAttribLocation(cubeProgram, "a_Color");

        cubeModelParam = GLES20.glGetUniformLocation(cubeProgram, "u_Model");
        cubeModelViewParam = GLES20.glGetUniformLocation(cubeProgram, "u_MVMatrix");
        cubeModelViewProjectionParam = GLES20.glGetUniformLocation(cubeProgram, "u_MVP");
        cubeLightPosParam = GLES20.glGetUniformLocation(cubeProgram, "u_LightPos");

        checkGLError("Cube program params");

        updateModelPosition();
    }
    private void updateModelPosition(){
        Matrix.setIdentityM(modelCube, 0);
        Matrix.translateM(modelCube, 0, modelPosition[0], modelPosition[1], modelPosition[2]);
        checkGLError("updateCubePosition");
    }
    @Override
    public void onSurfaceChanged(int i, int i1) {

    }
    @Override
    public void onNewFrame(HeadTransform headTransform) {
        setCubeRotation();
        Matrix.setLookAtM(camera, 0, 0.0f, 0.0f, CAMERA_Z, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);

    }

    @Override
    public void onDrawEye(Eye eye) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        checkGLError("colorParam");

        // Apply the eye transformation to the camera.
        Matrix.multiplyMM(view, 0, eye.getEyeView(), 0, camera, 0);

        // Set the position of the light
        Matrix.multiplyMV(lightPosInEyeSpace, 0, view, 0, LIGHT_POS_IN_WORLD_SPACE, 0);

        // Build the ModelView and ModelViewProjection matrices
        // for calculating cube position and light.
        float[] perspective = eye.getPerspective(Z_NEAR, Z_FAR);
        Matrix.multiplyMM(modelView, 0, view, 0, modelCube, 0);
        Matrix.multiplyMM(modelViewProjection, 0, perspective, 0, modelView, 0);
        drawCube();
    }

    @Override
    public void onFinishFrame(Viewport viewport) {

    }


    public void drawCube() {
        GLES20.glUseProgram(cubeProgram);

        GLES20.glUniform3fv(cubeLightPosParam, 1, lightPosInEyeSpace, 0);

        // Set the Model in the shader, used to calculate lighting
        GLES20.glUniformMatrix4fv(cubeModelParam, 1, false, modelCube, 0);

        // Set the ModelView in the shader, used to calculate lighting
        GLES20.glUniformMatrix4fv(cubeModelViewParam, 1, false, modelView, 0);

        // Set the position of the cube
        GLES20.glVertexAttribPointer(
                cubePositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, cubeVertices);

        // Set the ModelViewProjection matrix in the shader.
        GLES20.glUniformMatrix4fv(cubeModelViewProjectionParam, 1, false, modelViewProjection, 0);

        // Set the normal positions of the cube, again for shading
        GLES20.glVertexAttribPointer(cubeNormalParam, 3, GLES20.GL_FLOAT, false, 0, cubeNormals);
        //GLES20.glVertexAttribPointer(cubeColorParam, 4, GLES20.GL_FLOAT, false, 0,isLookingAtObject() ? cubeFoundColors : cubeColors);

        // Enable vertex arrays
        GLES20.glEnableVertexAttribArray(cubePositionParam);
        GLES20.glEnableVertexAttribArray(cubeNormalParam);
        //     GLES20.glEnableVertexAttribArray(cubeColorParam);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 36);
        checkGLError("Drawing cube");
    }

    protected void setCubeRotation() {
        Matrix.rotateM(modelCube, 0, TIME_DELTA, 0.5f, 0.5f, 1.0f);
    }



    @Override
    public void onRendererShutdown() {

    }
}
