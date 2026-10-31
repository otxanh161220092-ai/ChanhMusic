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
import androidx.compose.ui.graphics.GraphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/** Holds the recorded app content behind floating bars, plus where that content sits in the window. */
class GlassBackdrop(val source: GraphicsLayer) {
    var origin: Offset = Offset.Zero
}

private const val GlassSkSl = """
uniform shader content;
uniform float2 size;
half4 main(float2 coord) {
    float2 uv = coord / size - 0.5;
    float r = length(uv);
    float bulge = (1.0 - smoothstep(0.0, 0.5, r)) * 0.06;
    half4 c = content.eval(coord - uv * size * bulge);
    float lum = dot(c.rgb, float3(0.299, 0.587, 0.114));
    float t = smoothstep(0.35, 0.6, lum);
    float3 target = mix(float3(0.08), float3(1.0), t);
    return half4(mix(c.rgb, target, 0.8), 1.0);
}
"""

fun isGlassSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/** Records the content it wraps into the backdrop layer, then draws it as usual. */
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
 * Draws the part of the captured backdrop under this element, blurred, refracted and tinted
 * white over light areas or dark over dark areas. Icons and text are drawn on top.
 * Falls back to the plain content on Android below 13.
 */
fun Modifier.glassSurface(backdrop: GlassBackdrop?, layer: GraphicsLayer): Modifier =
    if (backdrop == null || !isGlassSupported()) {
        this
    } else {
        composed {
            val origin = remember { OriginHolder() }
            this
                .onGloballyPositioned { origin.value = it.positionInRoot() }
                .drawWithContent {
                    drawGlass(backdrop, layer, origin.value)
                    drawContent()
                }
        }
    }

private class OriginHolder {
    var value: Offset = Offset.Zero
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun DrawScope.drawGlass(backdrop: GlassBackdrop, layer: GraphicsLayer, origin: Offset) {
    val w = size.width
    val h = size.height
    val shader = RuntimeShader(GlassSkSl)
    shader.setFloatUniform("size", w, h)
    val blurred = RenderEffect.createBlurEffect(10f, 10f, Shader.TileMode.CLAMP)
    val effect = RenderEffect.createRuntimeShaderEffect(shader, "content")
    layer.renderEffect = RenderEffect.createChainEffect(effect, blurred).asComposeRenderEffect()
    val delta = origin - backdrop.origin
    layer.record(IntSize(w.roundToInt(), h.roundToInt())) {
        translate(-delta.x, -delta.y) {
            drawLayer(backdrop.source)
        }
    }
    drawLayer(layer)
}
