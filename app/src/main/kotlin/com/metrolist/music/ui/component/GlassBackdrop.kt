/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Recorded app content behind floating bars, plus where that content sits in the window. */
class GlassBackdrop(val source: GraphicsLayer) {
    var origin: Offset = Offset.Zero
}

private class OriginHolder {
    var value: Offset = Offset.Zero
}

/**
 * Port of the LiquidGlass fragment shader by Sepehr Kalanaki (OverShifted/LiquidGlass, MIT):
 * a superellipse signed distance field drives the refraction, with a rim glow on the edges.
 */
private val GlassSkSl =
    """
    uniform shader content;
    uniform float2 size;

    float sdSuperellipse(float2 p, float n, float r) {
        float2 pa = abs(p);
        float num = pow(pa.x, n) + pow(pa.y, n) - pow(r, n);
        float den = n * sqrt(pow(pa.x, 2.0 * n - 2.0) + pow(pa.y, 2.0 * n - 2.0)) + 0.00001;
        return num / den;
    }

    float refractionCurve(float x) {
        return 1.0 - 2.3 * pow(5.2 * 2.718281828, -6.9 * x - 0.7);
    }

    half4 main(float2 coord) {
        float2 hs = size * 0.5;
        float2 p = (coord - hs) / hs;
        float d = sdSuperellipse(p, 4.0, 1.0);
        if (d > 0.0) {
            return content.eval(coord);
        }
        float dist = -d;
        float2 sampleP = p * pow(refractionCurve(dist), 3.0);
        half4 c = content.eval(sampleP * hs + hs);
        float glow = sin(atan(p.y, p.x) - 0.5) * 0.3 * smoothstep(0.06, 0.0, dist) + 1.0;
        return half4(c.rgb * glow, 1.0);
    }
    """.trimIndent()

fun isGlassSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/** Records the wrapped content into the backdrop layer, then draws it as usual. */
fun Modifier.glassBackdropSource(backdrop: GlassBackdrop?): Modifier =
    if (backdrop == null || !isGlassSupported()) {
        this
    } else {
        this
            .onGloballyPositioned { backdrop.origin = it.positionInRoot() }
            .drawWithContent {
                backdrop.source.record(IntSize(size.width.roundToInt(), size.height.roundToInt())) {
                    this@drawWithContent.drawContent()
                }
                drawLayer(backdrop.source)
            }
    }

/**
 * Liquid glass surface for a pill-shaped element: refracted, blurred and lightly tinted copy of the
 * backdrop under it, with icons and text drawn on top. Plain content on Android below 13.
 */
fun Modifier.glassSurface(backdrop: GlassBackdrop?, layer: GraphicsLayer): Modifier =
    if (backdrop == null || !isGlassSupported()) {
        this
    } else {
        composed {
            val origin = remember { OriginHolder() }
            val shader = remember { RuntimeShader(GlassSkSl) }
            val effect = remember(shader) {
                val blur = RenderEffect.createBlurEffect(8f, 8f, Shader.TileMode.CLAMP)
                RenderEffect.createChainEffect(
                    RenderEffect.createRuntimeShaderEffect(shader, "content"),
                    blur,
                ).asComposeRenderEffect()
            }
            this
                .onGloballyPositioned { origin.value = it.positionInRoot() }
                .drawWithContent {
                    drawGlass(backdrop, layer, origin.value, shader, effect)
                    drawContent()
                }
        }
    }

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun DrawScope.drawGlass(
    backdrop: GlassBackdrop,
    layer: GraphicsLayer,
    origin: Offset,
    shader: RuntimeShader,
    effect: androidx.compose.ui.graphics.RenderEffect,
) {
    val w = size.width
    val h = size.height
    shader.setFloatUniform("size", w, h)
    layer.renderEffect = effect
    val delta = origin - backdrop.origin
    layer.record(IntSize(w.roundToInt(), h.roundToInt())) {
        translate(-delta.x, -delta.y) {
            drawLayer(backdrop.source)
        }
    }
    drawLayer(layer)
}
