package asteria.top.client.render

import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import org.lwjgl.opengl.*

object ShaderManager {
    private var programId = 0
    private var vao = 0
    private var vbo = 0
    private var captureTexture = 0
    private var captureWidth = 0
    private var captureHeight = 0
    private var initialized = false

    private fun loadShaderSource(path: String): String {
        val resourceLocation = Identifier.fromNamespaceAndPath("asteria", path)
        val resource = Minecraft.getInstance().resourceManager.getResource(resourceLocation)
        if (!resource.isPresent) {
            throw RuntimeException("Shader resource not found: $path")
        }
        return resource.get().open().bufferedReader().use { it.readText() }
    }

    private fun compileShader(source: String, type: Int): Int {
        val shaderId = GL20.glCreateShader(type)
        GL20.glShaderSource(shaderId, source)
        GL20.glCompileShader(shaderId)
        
        val compiled = GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS)
        if (compiled == GL11.GL_FALSE) {
            val log = GL20.glGetShaderInfoLog(shaderId, 1024)
            GL20.glDeleteShader(shaderId)
            throw RuntimeException("Failed to compile shader ($type): $log")
        }
        return shaderId
    }

    fun init() {
        if (initialized) return
        try {
            val vshSource = loadShaderSource("shaders/program/blur.vsh")
            val fshSource = loadShaderSource("shaders/program/blur.fsh")
            
            val vsh = compileShader(vshSource, GL20.GL_VERTEX_SHADER)
            val fsh = compileShader(fshSource, GL20.GL_FRAGMENT_SHADER)
            
            programId = GL20.glCreateProgram()
            GL20.glAttachShader(programId, vsh)
            GL20.glAttachShader(programId, fsh)
            
            GL20.glBindAttribLocation(programId, 0, "Position")
            
            GL20.glLinkProgram(programId)
            
            val linked = GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS)
            if (linked == GL11.GL_FALSE) {
                val log = GL20.glGetProgramInfoLog(programId, 1024)
                throw RuntimeException("Failed to link shader program: $log")
            }
            
            GL20.glDeleteShader(vsh)
            GL20.glDeleteShader(fsh)
            
            vao = GL30.glGenVertexArrays()
            GL30.glBindVertexArray(vao)
            
            vbo = GL15.glGenBuffers()
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo)
            
            val vertices = floatArrayOf(
                -1f, -1f,
                 1f, -1f,
                -1f,  1f,
                -1f,  1f,
                 1f, -1f,
                 1f,  1f
            )
            val floatBuffer = org.lwjgl.BufferUtils.createFloatBuffer(vertices.size).put(vertices)
            floatBuffer.flip()
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, floatBuffer, GL15.GL_STATIC_DRAW)
            
            GL20.glEnableVertexAttribArray(0)
            GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 0, 0L)
            
            GL30.glBindVertexArray(0)
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0)

            captureTexture = GL11.glGenTextures()
            
            initialized = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun renderBlurBox(
        x: Float, y: Float, width: Float, height: Float,
        cornerRadius: Float,
        roundingRule: Int = 1,
        tintStrength: Float
    ) {
        if (!initialized) {
            init()
        }
        if (!initialized) return

        val mc = Minecraft.getInstance()
        val window = mc.window
        val guiScale = window.guiScale.toFloat()
        
        val windowWidth = window.width.toFloat()
        val windowHeight = window.height.toFloat()
        
        val boxWidthPixels = width * guiScale
        val boxHeightPixels = height * guiScale
        val boxXPixels = x * guiScale
        val boxYPixels = windowHeight - (y * guiScale) - boxHeightPixels
        
        val prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
        val prevTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
        val prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)
        val prevTextureUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)
        
        GL13.glActiveTexture(GL13.GL_TEXTURE0)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, captureTexture)
        if (captureWidth != windowWidth.toInt() || captureHeight != windowHeight.toInt()) {
            captureWidth = windowWidth.toInt()
            captureHeight = windowHeight.toInt()
            GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL11.GL_RGBA8,
                captureWidth,
                captureHeight,
                0,
                GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE,
                0L
            )
        }

        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, captureWidth, captureHeight)
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
        
        GL20.glUseProgram(programId)
        
        val uResolution = GL20.glGetUniformLocation(programId, "iResolution")
        val uChannelResolution0 = GL20.glGetUniformLocation(programId, "iChannelResolution0")
        val uBoxRect = GL20.glGetUniformLocation(programId, "uBoxRect")
        val uCornerRadius = GL20.glGetUniformLocation(programId, "uCornerRadius")
        val uRoundingRule = GL20.glGetUniformLocation(programId, "uRoundingRule")
        val uTintStrength = GL20.glGetUniformLocation(programId, "uTintStrength")
        
        GL20.glUniform2f(uResolution, windowWidth, windowHeight)
        GL20.glUniform2f(uChannelResolution0, windowWidth, windowHeight)
        GL20.glUniform4f(uBoxRect, boxXPixels, boxYPixels, boxWidthPixels, boxHeightPixels)
        GL20.glUniform1f(uCornerRadius, cornerRadius * guiScale)
        GL20.glUniform1i(uRoundingRule, roundingRule)
        GL20.glUniform1f(uTintStrength, tintStrength)
        
        GL13.glActiveTexture(GL13.GL_TEXTURE0)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, captureTexture)
        val iChannel0 = GL20.glGetUniformLocation(programId, "iChannel0")
        GL20.glUniform1i(iChannel0, 0)
        
        val blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND)
        if (!blendEnabled) {
            GL11.glEnable(GL11.GL_BLEND)
        }
        val prevSrcBlend = GL11.glGetInteger(GL11.GL_BLEND_SRC)
        val prevDstBlend = GL11.glGetInteger(GL11.GL_BLEND_DST)
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO)
        
        GL30.glBindVertexArray(vao)
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6)
        
        GL30.glBindVertexArray(prevVao)
        GL20.glUseProgram(prevProgram)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex)
        GL13.glActiveTexture(prevTextureUnit)
        
        if (!blendEnabled) {
            GL11.glDisable(GL11.GL_BLEND)
        } else {
            GL11.glBlendFunc(prevSrcBlend, prevDstBlend)
        }
    }
}
