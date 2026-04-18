package com.vtbvita.widget.ui.effects

import android.graphics.*
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import androidx.compose.runtime.withFrameMillis
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.PI

// ── Параметры из плейграунда ──────────────────────────────────────────────────
//
// Фон: равномерный #1e368e с обеих сторон
// Три полосы, все #00aaff:
//   b1 pos=0.00  pow   add     — мягкое левое свечение, медленная волна
//   b2 pos=0.80  pow   add     — широкое правое гало, почти статично
//   b3 pos=0.84  cosine screen — острый блик поверх b2, очень медленно
// Гамма 0.60 (brightens midtones)
// Shimmer = 0

// ── AGSL Shader (API 33+) ─────────────────────────────────────────────────────

@RequiresApi(33)
private const val AURORA_AGSL = """
uniform float2 iResolution;
uniform float  iTime;
uniform float  iAmplitude;

// pow-форма: мягкий колокол
float bandPow(float dist, float sigma, float sharpness) {
    float d = abs(dist) / sigma;
    if (d >= 1.0) return 0.0;
    return pow(1.0 - d, sharpness);
}

// cosine-форма: гладкий cosine
float bandCos(float dist, float sigma) {
    float d = abs(dist) / sigma;
    if (d >= 1.0) return 0.0;
    return 0.5 + 0.5 * cos(d * 3.14159265);
}

// Трёхгармонная волна: основная + две обертоны
float bandWave(float uvY, float t,
               float amp, float freq, float speed, float phase,
               float h2, float h3) {
    return sin(uvY * freq        + t * speed        + phase) * amp
         + sin(uvY * freq * 1.83 + t * speed * 1.5  + phase) * h2
         + sin(uvY * freq * 3.0  + t * speed * 2.0  + phase) * h3;
}

// Screen blend: 1-(1-a)(1-b)
half3 blendScreen(half3 base, half3 lyr) {
    return 1.0 - (1.0 - base) * (1.0 - lyr);
}

half4 main(float2 fragCoord) {
    float2 uv  = fragCoord / iResolution;
    // Амплитуда голоса усиливает волны (1.0 в покое → до ~3.5 при громкой речи)
    float  liveAmp = 1.0 + (iAmplitude - 0.08) * 3.5;
    float  t       = iTime;

    // Фон: равномерный тёмно-синий
    half3 col = half3(0.118, 0.212, 0.557);

    // ── Полоса 1: левый край, мягкое свечение, pow, add ──────────────────────
    float c1 = 0.00 + bandWave(uv.y, t,
        0.02 * liveAmp, 1.5, 0.30, 5.20, 0.030, 0.000);
    float bv1 = (bandPow(uv.x - c1, 0.09, 0.75) * 1.35
               + bandPow(uv.x - c1, 0.17, 1.00) * 0.55) * 0.45 * 0.46;
    col += half3(0.000, 0.667, 1.000) * half(bv1);

    // ── Полоса 2: правое широкое гало, pow, add ───────────────────────────────
    float c2 = 0.80 + bandWave(uv.y, t,
        0.00 * liveAmp, 0.5, 0.30, 0.00, 0.050, 0.030);
    float bv2 = (bandPow(uv.x - c2, 0.14, 1.00) * 1.35
               + bandPow(uv.x - c2, 0.33, 1.00) * 0.55) * 0.25 * 1.00;
    col += half3(0.000, 0.667, 1.000) * half(bv2);

    // ── Полоса 3: острый блик, cosine, screen ─────────────────────────────────
    float c3 = 0.84 + bandWave(uv.y, t,
        0.01 * liveAmp, 0.5, 0.10, 3.20, 0.055, 0.000);
    float bv3 = (bandCos(uv.x - c3, 0.07) * 1.35
               + bandCos(uv.x - c3, 0.18) * 0.55) * 0.25 * 1.00;
    col = blendScreen(col, half3(0.000, 0.667, 1.000) * half(bv3));

    // Гамма 0.60 → pow(col, 1/0.60) = pow(col, 1.667)
    col = pow(clamp(col, 0.0, 1.0), half3(1.6667));

    return half4(col, 1.0);
}
"""

// ── Canvas Renderer (все API) ─────────────────────────────────────────────────

object AuroraRenderer {

    private const val CW = 80
    private const val CH = 32

    private fun bandPow(dist: Float, sigma: Float, sharpness: Float): Float {
        val d = abs(dist) / sigma
        if (d >= 1f) return 0f
        return (1f - d).pow(sharpness)
    }

    private fun bandCos(dist: Float, sigma: Float): Float {
        val d = abs(dist) / sigma
        if (d >= 1f) return 0f
        return 0.5f + 0.5f * cos(d * PI.toFloat())
    }

    private fun bandWave(
        uvY: Float, t: Float,
        amp: Float, freq: Float, speed: Float, phase: Float,
        h2: Float, h3: Float
    ): Float =
        sin(uvY * freq        + t * speed        + phase) * amp +
        sin(uvY * freq * 1.83f + t * speed * 1.5f + phase) * h2 +
        sin(uvY * freq * 3.0f  + t * speed * 2.0f + phase) * h3

    // Screen blend: 1-(1-a)(1-b)  per component
    private fun screenR(base: Float, layer: Float) = 1f - (1f - base) * (1f - layer)

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    fun draw(canvas: Canvas, w: Float, h: Float, timeSec: Float, amplitude: Float) {
        val liveAmp = 1f + (amplitude - 0.08f) * 3.5f
        val pixels  = IntArray(CW * CH)

        for (py in 0 until CH) {
            val uvY = py.toFloat() / CH

            // Per-band wave offsets for this row
            val w1 = bandWave(uvY, timeSec, 0.02f * liveAmp, 1.5f, 0.30f, 5.20f, 0.030f, 0.000f)
            val w2 = bandWave(uvY, timeSec, 0.00f * liveAmp, 0.5f, 0.30f, 0.00f, 0.050f, 0.030f)
            val w3 = bandWave(uvY, timeSec, 0.01f * liveAmp, 0.5f, 0.10f, 3.20f, 0.055f, 0.000f)

            val c1 = 0.00f + w1
            val c2 = 0.80f + w2
            val c3 = 0.84f + w3

            for (px in 0 until CW) {
                val uvX = px.toFloat() / CW

                // Фон: равномерный #1e368e
                var r = 0.118f
                var g = 0.212f
                var b = 0.557f

                // Полоса 1: add
                val bv1 = (bandPow(uvX - c1, 0.09f, 0.75f) * 1.35f +
                           bandPow(uvX - c1, 0.17f, 1.00f) * 0.55f) * 0.45f * 0.46f
                r += 0.000f * bv1; g += 0.667f * bv1; b += 1.000f * bv1

                // Полоса 2: add
                val bv2 = (bandPow(uvX - c2, 0.14f, 1.00f) * 1.35f +
                           bandPow(uvX - c2, 0.33f, 1.00f) * 0.55f) * 0.25f * 1.00f
                r += 0.000f * bv2; g += 0.667f * bv2; b += 1.000f * bv2

                // Полоса 3: screen
                val bv3 = (bandCos(uvX - c3, 0.07f) * 1.35f +
                           bandCos(uvX - c3, 0.18f) * 0.55f) * 0.25f * 1.00f
                val l3r = 0.000f * bv3; val l3g = 0.667f * bv3; val l3b = 1.000f * bv3
                r = screenR(r, l3r); g = screenR(g, l3g); b = screenR(b, l3b)

                // Гамма 0.60 → pow(v, 1/0.6)
                val gamma = 1f / 0.60f
                r = r.coerceIn(0f, 1f).pow(gamma)
                g = g.coerceIn(0f, 1f).pow(gamma)
                b = b.coerceIn(0f, 1f).pow(gamma)

                val ri = (r * 255).toInt()
                val gi = (g * 255).toInt()
                val bi = (b * 255).toInt()
                pixels[py * CW + px] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
            }
        }

        val bm = Bitmap.createBitmap(pixels, CW, CH, Bitmap.Config.ARGB_8888)
        canvas.drawBitmap(bm, null, RectF(0f, 0f, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
        bm.recycle()
    }

    fun createBitmap(
        widthPx: Int, heightPx: Int,
        timeSec: Float, amplitude: Float,
        cornerRadiusPx: Float
    ): Bitmap {
        val bm     = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bm)
        val path   = Path().apply {
            addRoundRect(
                RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat()),
                cornerRadiusPx, cornerRadiusPx, Path.Direction.CW
            )
        }
        canvas.clipPath(path)
        draw(canvas, widthPx.toFloat(), heightPx.toFloat(), timeSec, amplitude)
        return bm
    }
}

// ── Compose Modifier ─────────────────────────────────────────────────────────

@Composable
fun Modifier.auroraBackground(
    isRecording: Boolean,
    amplitude: Float,
    cornerRadius: Dp = 32.dp
): Modifier {
    var timeSec by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            var startMs = 0L
            withFrameMillis { startMs = it }
            while (isActive) {
                withFrameMillis { ms ->
                    timeSec = (ms - startMs) / 1000f
                }
            }
        }
    }

    val t   = timeSec
    val amp = if (isRecording) amplitude else 0.08f

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        auroraAgsl(t, amp, cornerRadius)
    } else {
        this
            .clip(RoundedCornerShape(cornerRadius))
            .drawBehind {
                drawIntoCanvas { c ->
                    AuroraRenderer.draw(c.nativeCanvas, size.width, size.height, t, amp)
                }
            }
    }
}

@RequiresApi(33)
@Composable
private fun Modifier.auroraAgsl(timeSec: Float, amplitude: Float, cornerRadius: Dp): Modifier {
    val shader = remember { RuntimeShader(AURORA_AGSL) }
    return this
        .clip(RoundedCornerShape(cornerRadius))
        .drawBehind {
            shader.setFloatUniform("iResolution", size.width, size.height)
            shader.setFloatUniform("iTime",      timeSec)
            shader.setFloatUniform("iAmplitude", amplitude)
            drawIntoCanvas { c ->
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
                c.nativeCanvas.drawRect(0f, 0f, size.width, size.height, p)
            }
        }
}
