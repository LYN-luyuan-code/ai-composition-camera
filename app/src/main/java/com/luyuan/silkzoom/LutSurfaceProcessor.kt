package com.luyuan.silkzoom

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.*
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import androidx.camera.core.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executor

/** CameraX preview effect: OES camera texture -> trilinear LUT atlas -> PreviewView. API 29+. */
internal class LutSurfaceProcessor(private val onFailure: (Throwable)->Unit) : SurfaceProcessor {
    private val thread=HandlerThread("CameraLutGL").apply { start() }
    private val handler=Handler(thread.looper)
    val executor=Executor { handler.post(it) }
    val effect=object: CameraEffect(PREVIEW,executor,this,{ onFailure(it) }) {}
    private var display:EGLDisplay=EGL14.EGL_NO_DISPLAY
    private var context:EGLContext=EGL14.EGL_NO_CONTEXT
    private var config:EGLConfig?=null
    private var parking:EGLSurface=EGL14.EGL_NO_SURFACE
    private var program=0;private var lutTexture=0;private var currentFilter:StreetFilter?=null;private var amount=0f
    private var lut=StreetFilter.ORIGINAL.lut
    data class Choice(val filter:StreetFilter,val strength:Float)
    @Volatile var displayed=Choice(StreetFilter.ORIGINAL,1f)
        private set
    private var choice=displayed
    private var closed=false;private var failed=false
    private class Input(val texture:Int,val image:SurfaceTexture,val surface:Surface) { var ready=false }
    private class Output(val output:SurfaceOutput,val egl:EGLSurface)
    private val inputs=mutableListOf<Input>();private val outputs=mutableListOf<Output>()
    private val originalTransform=FloatArray(16);private val transform=FloatArray(16)
    private val vertices=ByteBuffer.allocateDirect(16*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(floatArrayOf(-1f,-1f,0f,0f,1f,-1f,1f,0f,-1f,1f,0f,1f,1f,1f,1f,1f));position(0) }
    private val uniforms=mutableMapOf<String,Int>()
    fun setFilter(filter:StreetFilter,strength:Float) { executor.execute {
        if(closed||failed)return@execute
        try {
            initialize()
            if(currentFilter!==filter) { lut=filter.lut;uploadLut();currentFilter=filter }
            amount=if(filter===StreetFilter.ORIGINAL)0f else strength.coerceIn(0f,1f);choice=Choice(filter,strength)
        } catch(error:Throwable){fail(error)}
    } }
    override fun onInputSurface(request:SurfaceRequest) {
        if(closed||failed){request.willNotProvideSurface();return}
        try {
            initialize();makeCurrent(parking)
            val ids=IntArray(1);GLES20.glGenTextures(1,ids,0);GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,ids[0]);parameters(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
            val image=SurfaceTexture(ids[0]).apply { setDefaultBufferSize(request.resolution.width,request.resolution.height) }
            val input=Input(ids[0],image,Surface(image));inputs.add(input)
            image.setOnFrameAvailableListener({
                if(!closed && !failed && input===inputs.lastOrNull()) {
                    try { makeCurrent(parking);image.updateTexImage();input.ready=true;render(input) } catch(error:Throwable){fail(error)}
                }
            },handler)
            request.provideSurface(input.surface,executor) {
                image.setOnFrameAvailableListener(null);input.surface.release();image.release();inputs.remove(input)
                if(display!=EGL14.EGL_NO_DISPLAY){makeCurrent(parking);GLES20.glDeleteTextures(1,intArrayOf(input.texture),0)}
                finishIfReleased()
            }
        }catch(error:Throwable){request.willNotProvideSurface();fail(error);throw ProcessingException().apply { initCause(error) }}
    }
    override fun onOutputSurface(surfaceOutput:SurfaceOutput) {
        if(closed||failed){surfaceOutput.close();return}
        try {
            initialize()
            val surface=surfaceOutput.getSurface(executor) {
                val existing=outputs.firstOrNull { it.output===surfaceOutput }
                if(existing!=null) { makeCurrent(parking);EGL14.eglDestroySurface(display,existing.egl);outputs.remove(existing) }
                surfaceOutput.close();finishIfReleased()
            }
            val window=EGL14.eglCreateWindowSurface(display,config,surface,intArrayOf(EGL14.EGL_NONE),0)
            check(window!=EGL14.EGL_NO_SURFACE){"Cannot create LUT output"};outputs.add(Output(surfaceOutput,window))
        }catch(error:Throwable){surfaceOutput.close();fail(error);throw ProcessingException().apply { initCause(error) }}
    }
    private fun render(input:Input) {
        input.image.getTransformMatrix(originalTransform)
        for(out in outputs.toList()) {
            makeCurrent(out.egl);out.output.updateTransformMatrix(transform,originalTransform)
            draw(input,out.output.size.width,out.output.size.height,amount)
            EGLExt.eglPresentationTimeANDROID(display,out.egl,input.image.timestamp)
            check(EGL14.eglSwapBuffers(display,out.egl)){"Cannot show LUT frame"};displayed=choice
        }
    }
    /** Raw camera-backed card source; avoids applying a preset over an already graded preview. */
    fun snapshotRaw(done:(Bitmap?)->Unit) { snapshotRawWhenReady(done,80) }
    private fun snapshotRawWhenReady(done:(Bitmap?)->Unit,remaining:Int) { executor.execute {
        if(closed||failed){done(null);return@execute}
        val input=inputs.lastOrNull();val output=outputs.lastOrNull()
        if(input?.ready!=true||output==null) {
            if(remaining>0)handler.postDelayed({snapshotRawWhenReady(done,remaining-1)},100L) else done(null)
            return@execute
        }
        var texture=0;var framebuffer=0
        try {
            makeCurrent(parking);input.image.getTransformMatrix(originalTransform);output.output.updateTransformMatrix(transform,originalTransform)
            val factor=256f/maxOf(output.output.size.width,output.output.size.height)
            val w=(output.output.size.width*factor).toInt().coerceAtLeast(1);val h=(output.output.size.height*factor).toInt().coerceAtLeast(1)
            val ids=IntArray(1);GLES20.glGenTextures(1,ids,0);texture=ids[0];GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);parameters(GLES20.GL_TEXTURE_2D)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,w,h,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,null)
            GLES20.glGenFramebuffers(1,ids,0);framebuffer=ids[0];GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,framebuffer)
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,GLES20.GL_COLOR_ATTACHMENT0,GLES20.GL_TEXTURE_2D,texture,0)
            check(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)==GLES20.GL_FRAMEBUFFER_COMPLETE)
            draw(input,w,h,0f)
            val bytes=ByteBuffer.allocateDirect(w*h*4);GLES20.glReadPixels(0,0,w,h,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,bytes)
            val pixels=IntArray(w*h)
            for(y in 0 until h)for(x in 0 until w) { val i=((h-1-y)*w+x)*4;pixels[y*w+x]=(255 shl 24) or ((bytes.get(i).toInt() and 255) shl 16) or ((bytes.get(i+1).toInt() and 255) shl 8) or (bytes.get(i+2).toInt() and 255) }
            done(Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888))
        }catch(_:Throwable){done(null)}finally{
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,0)
            if(framebuffer!=0)GLES20.glDeleteFramebuffers(1,intArrayOf(framebuffer),0)
            if(texture!=0)GLES20.glDeleteTextures(1,intArrayOf(texture),0)
        }
    } }
    private fun draw(input:Input,w:Int,h:Int,strength:Float) {
        GLES20.glViewport(0,0,w,h);GLES20.glUseProgram(program)
        val position=GLES20.glGetAttribLocation(program,"aPosition");val uv=GLES20.glGetAttribLocation(program,"aUv")
        vertices.position(0);GLES20.glVertexAttribPointer(position,2,GLES20.GL_FLOAT,false,16,vertices);GLES20.glEnableVertexAttribArray(position)
        vertices.position(2);GLES20.glVertexAttribPointer(uv,2,GLES20.GL_FLOAT,false,16,vertices);GLES20.glEnableVertexAttribArray(uv)
        GLES20.glUniformMatrix4fv(location("uTransform"),1,false,transform,0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,input.texture);GLES20.glUniform1i(location("uCamera"),0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,lutTexture);GLES20.glUniform1i(location("uLut"),1)
        GLES20.glUniform1f(location("uSize"),lut.size.toFloat());GLES20.glUniform1f(location("uTiles"),lut.tiles.toFloat());GLES20.glUniform1f(location("uAtlas"),lut.atlasSide.toFloat());GLES20.glUniform1f(location("uStrength"),strength)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4)
    }
    private fun location(name:String)=uniforms.getOrPut(name){GLES20.glGetUniformLocation(program,name)}
    private fun initialize() {
        if(program!=0)return
        display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);check(display!=EGL14.EGL_NO_DISPLAY)
        check(EGL14.eglInitialize(display,IntArray(2),0,IntArray(2),0))
        val configs=arrayOfNulls<EGLConfig>(1);val count=IntArray(1)
        check(EGL14.eglChooseConfig(display,intArrayOf(EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,EGL14.EGL_NONE),0,configs,0,1,count,0)&&count[0]>0)
        config=configs[0]
        context=EGL14.eglCreateContext(display,config,EGL14.EGL_NO_CONTEXT,intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE),0);check(context!=EGL14.EGL_NO_CONTEXT)
        parking=EGL14.eglCreatePbufferSurface(display,config,intArrayOf(EGL14.EGL_WIDTH,1,EGL14.EGL_HEIGHT,1,EGL14.EGL_NONE),0);makeCurrent(parking)
        val vertex=shader(GLES20.GL_VERTEX_SHADER,"attribute vec4 aPosition;attribute vec4 aUv;uniform mat4 uTransform;varying vec2 vUv;void main(){gl_Position=aPosition;vUv=(uTransform*aUv).xy;}")
        val fragment=shader(GLES20.GL_FRAGMENT_SHADER,FRAGMENT)
        program=GLES20.glCreateProgram();GLES20.glAttachShader(program,vertex);GLES20.glAttachShader(program,fragment);GLES20.glLinkProgram(program)
        val status=IntArray(1);GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,status,0);check(status[0]!=0){GLES20.glGetProgramInfoLog(program)}
        GLES20.glDeleteShader(vertex);GLES20.glDeleteShader(fragment)
        val ids=IntArray(1);GLES20.glGenTextures(1,ids,0);lutTexture=ids[0];uploadLut()
    }
    private fun uploadLut() {
        makeCurrent(parking);GLES20.glActiveTexture(GLES20.GL_TEXTURE1);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,lutTexture);parameters(GLES20.GL_TEXTURE_2D)
        val bytes=lut.atlas();val buffer=ByteBuffer.allocateDirect(bytes.size).put(bytes).apply { position(0) }
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,lut.atlasSide,lut.atlasSide,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,buffer)
        check(GLES20.glGetError()==GLES20.GL_NO_ERROR){"Cannot upload LUT"}
    }
    private fun shader(type:Int,source:String):Int {
        val shader=GLES20.glCreateShader(type);GLES20.glShaderSource(shader,source);GLES20.glCompileShader(shader)
        val status=IntArray(1);GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,status,0);check(status[0]!=0){GLES20.glGetShaderInfoLog(shader)};return shader
    }
    private fun parameters(target:Int) { GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(target,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE) }
    private fun makeCurrent(surface:EGLSurface) { check(EGL14.eglMakeCurrent(display,surface,surface,context)){"Cannot activate LUT renderer"} }
    private fun fail(error:Throwable) { if(!failed&&!closed){failed=true;onFailure(error)} }
    fun close() { executor.execute { if(!closed){closed=true;makeCurrentIfReady();outputs.forEach { EGL14.eglDestroySurface(display,it.egl);it.output.close() };outputs.clear();finishIfReleased()} } }
    private fun makeCurrentIfReady(){if(display!=EGL14.EGL_NO_DISPLAY&&parking!=EGL14.EGL_NO_SURFACE)makeCurrent(parking)}
    private fun finishIfReleased() {
        if(!closed||inputs.isNotEmpty())return
        if(display!=EGL14.EGL_NO_DISPLAY){makeCurrentIfReady();if(program!=0)GLES20.glDeleteProgram(program);if(lutTexture!=0)GLES20.glDeleteTextures(1,intArrayOf(lutTexture),0);EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);EGL14.eglDestroySurface(display,parking);EGL14.eglDestroyContext(display,context);EGL14.eglTerminate(display);display=EGL14.EGL_NO_DISPLAY}
        thread.quitSafely()
    }
    companion object {
        private const val FRAGMENT="""
            #extension GL_OES_EGL_image_external : require
            precision highp float;
            uniform samplerExternalOES uCamera;uniform sampler2D uLut;
            uniform float uSize;uniform float uTiles;uniform float uAtlas;uniform float uStrength;
            varying vec2 vUv;
            vec3 slice(vec3 c,float blue){vec2 tile=vec2(mod(blue,uTiles),floor(blue/uTiles));vec2 uv=(tile*uSize+vec2(.5)+c.rg*(uSize-1.))/uAtlas;return texture2D(uLut,uv).rgb;}
            void main(){vec4 source=texture2D(uCamera,vUv);vec3 c=clamp(source.rgb,0.,1.);float b=c.b*(uSize-1.);float lo=floor(b);vec3 graded=mix(slice(c,lo),slice(c,min(lo+1.,uSize-1.)),fract(b));gl_FragColor=vec4(mix(source.rgb,graded,uStrength),source.a);}
        """
    }
}
