/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.filament.textured

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.os.Trace
import android.util.Log
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import android.view.animation.LinearInterpolator

import com.google.android.filament.*
import com.google.android.filament.android.UiHelper

import java.nio.ByteBuffer
import java.nio.channels.Channels
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : Activity() {
    // Make sure to initialize Filament first
    // This loads the JNI library needed by most API calls
    companion object {
        init {
            Filament.init()
        }
    }

    // The View we want to render into
    private lateinit var surfaceView: SurfaceView
    // UiHelper is provided by Filament to manage SurfaceView and SurfaceTexture
    private lateinit var uiHelper: UiHelper
    // Choreographer is used to schedule new frames
    private lateinit var choreographer: Choreographer

    // Engine creates and destroys Filament resources
    // Each engine must be accessed from a single thread of your choosing
    // Resources cannot be shared across engines
    private lateinit var engine: Engine
    // A renderer instance is tied to a single surface (SurfaceView, TextureView, etc.)
    private lateinit var renderer: Renderer
    // A scene holds all the renderable, lights, etc. to be drawn
    private lateinit var scene: Scene
    // A view defines a viewport, a scene and a camera for rendering
    private lateinit var view: View
    // Should be pretty obvious :)
    private lateinit var camera: Camera

    private lateinit var material: Material
    private lateinit var materialInstance: MaterialInstance

    private lateinit var baseColor: Texture
    private lateinit var normal: Texture
    private lateinit var aoRoughnessMetallic: Texture

    private lateinit var mesh: Mesh
    private lateinit var ibl: Ibl

    // Filament entity representing a renderable object
    @Entity
    private var light = 0

    private var spotLights: MutableList<Int> = mutableListOf()

    // A swap chain is Filament's representation of a surface
    private var swapChain: SwapChain? = null

    // Performs the rendering and schedules new frames
    private val frameScheduler = FrameCallback()

    private val animator = ValueAnimator.ofFloat(0.0f, (2.0 * PI).toFloat())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        surfaceView = SurfaceView(this)
        setContentView(surfaceView)

        choreographer = Choreographer.getInstance()

        setupSurfaceView()
        setupFilament()
        setupView()
        setupScene()
    }

    private fun setScaleFactor(scale: Float) {
        uiHelper.setDesiredSize((1080 * scale).toInt(), (1840 * scale).toInt())

    }

    private fun setupSurfaceView() {
        uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
        uiHelper.renderCallback = SurfaceCallback()

        // NOTE: To choose a specific rendering resolution, add the following line:
        setScaleFactor(0.85f)

        uiHelper.attachTo(surfaceView)
    }

    private fun setupFilament() {
        engine = Engine.create()
        renderer = engine.createRenderer()
        scene = engine.createScene()
        view = engine.createView()
        camera = engine.createCamera()
    }

    private fun setupView() {
        // Clear the background to middle-grey
        // Setting up a clear color is useful for debugging but usually
        // unnecessary when using a skybox
        view.setClearColor(0.035f, 0.035f, 0.035f, 1.0f)

        // NOTE: Try to disable post-processing (tone-mapping, etc.) to see the difference
        // view.isPostProcessingEnabled = false

        // Tell the view which camera we want to use
        view.camera = camera

        // Tell the view which scene we want to render
        view.scene = scene

        // Enable dynamic resolution with a default target frame rate of 60fps
        // val options = View.DynamicResolutionOptions()
        // options.enabled = true

        // view.dynamicResolutionOptions = options
    }

    private fun addOrbiter(materials: Map<String, MaterialInstance>, alpha: Float) {
        val angle = (alpha * 2 * PI).toFloat()
        val orbiterMesh = loadMesh(assets, "models/shader_ball.filamesh", materials, engine)
        val orbiterScale = 1.0f
        val orbitRadius = 50.0f
        engine.transformManager.setTransform(engine.transformManager.getInstance(orbiterMesh.renderable),
                floatArrayOf(
                        orbiterScale, 0.0f, 0.0f, 0.0f,
                        0.0f, orbiterScale, 0.0f, 0.0f,
                        0.0f, 0.0f, orbiterScale, 0.0f,
                        orbitRadius * sin(angle), -1.2f, orbitRadius * cos(angle), 1.0f
                ))
        scene.addEntity(orbiterMesh.renderable)
    }

    private fun setupScene() {
        loadMaterial()
        setupMaterial()
        loadImageBasedLight()

        scene.skybox = ibl.skybox
        scene.indirectLight = ibl.indirectLight

        // This map can contain named materials that will map to the material names
        // loaded from the filamesh file. The material called "DefaultMaterial" is
        // applied when no named material can be found
        val materials = mapOf("DefaultMaterial" to materialInstance)

        // Load the mesh in the filamesh format (see filamesh tool)
        mesh = loadMesh(assets, "models/shader_ball.filamesh", materials, engine)

        val numOrbiting = 100
        for (i in 0 until numOrbiting) {
            addOrbiter(materials, i / numOrbiting.toFloat())
        }

        // Move the mesh down
        // Filament uses column-major matrices
        engine.transformManager.setTransform(engine.transformManager.getInstance(mesh.renderable),
                floatArrayOf(
                        1.0f, 0.0f, 0.0f, 0.0f,
                        0.0f, 1.0f, 0.0f, 0.0f,
                        0.0f, 0.0f, 1.0f, 0.0f,
                        0.0f, -1.2f, 0.0f, 1.0f
                ))

        // Add the entity to the scene to render it
        scene.addEntity(mesh.renderable)

        // We now need a light, let's create a directional light
        light = EntityManager.get().create()

        // Create a color from a temperature (D65)
        val (r, g, b) = Colors.cct(6_500.0f)
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(r, g, b)
                // Intensity of the sun in lux on a clear day
                .intensity(110_000.0f)
                // The direction is normalized on our behalf
                .direction(-0.753f, -1.0f, 0.890f)
                .castLight(true)
                .castShadows(true)
                .build(engine, light)

        // Add the entity to the scene to light it
        scene.addEntity(light)

        // Add some spot lights to increase CPU work
        val numLights = 0
        val lightDistance = 5.0f
        for (i in 0 until numLights) {
            val alpha = i / numLights.toFloat()
            val angle = (alpha * 2 * PI).toFloat()
            val thisSpotLight = EntityManager.get().create()
            val hsvColor = Color.HSVToColor(listOf(alpha * 360.0f, 1.0f, 1.0f).toFloatArray())
            LightManager.Builder(LightManager.Type.POINT)
                    .color(Color.red(hsvColor) / 255.0f, Color.green(hsvColor) / 255.0f, Color.blue(hsvColor) / 255.0f)
                    .intensity(100_000.0f)
                    //.position(0.0f * sin(angle), 0.0f * cos(angle), 3.0f)
                    .position(lightDistance * sin(angle), 1.0f, lightDistance * cos(angle))
                    .castShadows(true)
                    .castLight(true)
                    .build(engine, thisSpotLight)

            scene.addEntity(thisSpotLight)
            spotLights.add(thisSpotLight)
        }

        // Set the exposure on the camera, this exposure follows the sunny f/16 rule
        // Since we've defined a light that has the same intensity as the sun, it
        // guarantees a proper exposure
        camera.setExposure(16.0f, 1.0f / 125.0f, 100.0f)

        startAnimation()
    }

    private fun loadMaterial() {
        readUncompressedAsset("materials/textured_pbr.filamat").let {
            material = Material.Builder().payload(it, it.remaining()).build(engine)
        }
    }

    private fun setupMaterial() {
        // Create an instance of the material to set different parameters on it
        materialInstance = material.createInstance()

        // Note that the textures are stored in drawable-nodpi to prevent the system
        // from automatically resizing them based on the display's density
        baseColor = loadTexture(engine, resources, R.drawable.floor_basecolor, TextureType.COLOR)
        normal = loadTexture(engine, resources, R.drawable.floor_normal, TextureType.NORMAL)
        aoRoughnessMetallic = loadTexture(engine, resources,
                R.drawable.floor_ao_roughness_metallic, TextureType.DATA)

        // A texture sampler does not need to be kept around or destroyed
        val sampler = TextureSampler()
        sampler.anisotropy = 8.0f

        materialInstance.setParameter("baseColor", baseColor, sampler)
        materialInstance.setParameter("normal", normal, sampler)
        materialInstance.setParameter("aoRoughnessMetallic", aoRoughnessMetallic, sampler)
    }

    private fun loadImageBasedLight() {
        ibl = loadIbl(assets, "envs/venetian_crossroads_2k", engine)
        ibl.indirectLight.intensity = 40_000.0f
    }

    private fun startAnimation() {
        // Animate the triangle
        animator.interpolator = LinearInterpolator()
        animator.duration = 18_000
        animator.repeatMode = ValueAnimator.RESTART
        animator.repeatCount = ValueAnimator.INFINITE
        animator.addUpdateListener { a ->
            val v = (a.animatedValue as Float)
            camera.lookAt(cos(v) * 14.5, 1.5, sin(v) * 14.5, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0)
        }
        animator.start()
    }

    override fun onResume() {
        super.onResume()
        choreographer.postFrameCallback(frameScheduler)
        animator.start()
    }

    override fun onPause() {
        super.onPause()
        choreographer.removeFrameCallback(frameScheduler)
        animator.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()

        // Stop the animation and any pending frame
        choreographer.removeFrameCallback(frameScheduler)
        animator.cancel();

        // Always detach the surface before destroying the engine
        uiHelper.detach()

        // This ensures that all the commands we've sent to Filament have
        // been processed before we attempt to destroy anything
        engine.flushAndWait()

        // Cleanup all resources
        destroyMesh(engine, mesh)
        destroyIbl(engine, ibl)
        engine.destroyTexture(baseColor)
        engine.destroyTexture(normal)
        engine.destroyTexture(aoRoughnessMetallic)
        engine.destroyEntity(light)
        engine.destroyRenderer(renderer)
        engine.destroyMaterialInstance(materialInstance)
        engine.destroyMaterial(material)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCamera(camera)

        // Engine.destroyEntity() destroys Filament related resources only
        // (components), not the entity itself
        val entityManager = EntityManager.get()
        entityManager.destroy(light)

        // Destroying the engine will free up any resource you may have forgotten
        // to destroy, but it's recommended to do the cleanup properly
        engine.destroy()
    }

    var severity = 0;

    var framesRendered = 0
    var lastSampleTimeMs = SystemClock.elapsedRealtime()
    var secondsElapsed = 0

    fun busyLoopFor(iterations: Long) {
        Trace.beginSection("Busy Loop")
        var dummy = 1.5
        for (i in 0..iterations) {
            if (dummy < 2.0) {
                dummy *= 3.0
            } else {
                dummy /= 2.0
            }
            if (dummy == 1.2345) {
                Log.v("Filament", "Hit the jackpot!");
            }
        }
        Trace.endSection()
    }

    var secondsSinceLastScaleChange = 0
    var currentScaleFactor = 0.85f

    inner class FrameCallback : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            // Schedule the next frame
            choreographer.postFrameCallback(this)

            // This check guarantees that we have a swap chain
            if (uiHelper.isReadyToRender) {
                // If beginFrame() returns false you should skip the frame
                // This means you are sending frames too quickly to the GPU
                if (renderer.beginFrame(swapChain!!)) {
                    busyLoopFor(300000)

                    val now = SystemClock.elapsedRealtime()
                    if (now - lastSampleTimeMs >= 1000) {
                        // Log.i("Filament", "Rendering at $framesRendered fps")
                        lastSampleTimeMs = now
                        secondsElapsed += 1

                        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                        val powerManagerClass = PowerManager::class.java
                        val getHeadroomForLoad = powerManagerClass.getMethod("getThermalHeadroom", Integer.TYPE)
                        val headroom = getHeadroomForLoad(powerManager, 10) as Float

                        if (secondsSinceLastScaleChange > 5) {
                            if (headroom >= 0.99f) {
                                currentScaleFactor -= 0.05f
                                setScaleFactor(currentScaleFactor)
                                secondsSinceLastScaleChange = 0
                            } else if (headroom <= 0.98f && currentScaleFactor <= 0.85) {
                                currentScaleFactor += 0.05f
                                setScaleFactor(currentScaleFactor)
                                secondsSinceLastScaleChange = 0
                            }
                        }

                        Log.i("Filament", "$secondsElapsed $framesRendered $headroom $currentScaleFactor")

                        framesRendered = 0
                    }

                    renderer.render(view)
                    renderer.endFrame()
                    framesRendered += 1

                    busyLoopFor(400000)
                }
            }
        }
    }

    inner class SurfaceCallback : UiHelper.RendererCallback {
        override fun onNativeWindowChanged(surface: Surface) {
            swapChain?.let { engine.destroySwapChain(it) }
            swapChain = engine.createSwapChain(surface)
        }

        override fun onDetachedFromSurface() {
            swapChain?.let {
                engine.destroySwapChain(it)
                // Required to ensure we don't return before Filament is done executing the
                // destroySwapChain command, otherwise Android might destroy the Surface
                // too early
                engine.flushAndWait()
                swapChain = null
            }
        }

        override fun onResized(width: Int, height: Int) {
            val aspect = width.toDouble() / height.toDouble()
            camera.setProjection(45.0, aspect, 0.1, 100.0, Camera.Fov.VERTICAL)

            view.viewport = Viewport(0, 0, width, height)
        }
    }

    private fun readUncompressedAsset(assetName: String): ByteBuffer {
        assets.openFd(assetName).use { fd ->
            val input = fd.createInputStream()
            val dst = ByteBuffer.allocate(fd.length.toInt())

            val src = Channels.newChannel(input)
            src.read(dst)
            src.close()

            return dst.apply { rewind() }
        }
    }
}
