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
import kotlin.math.roundToInt

/** Recorded app content behind floating bars, plus where that content sits in the window. */
class GlassBackdrop(val source: GraphicsLayer) {
    var origin: Offset = Offset.Zero
}

private class OriginHolder {
    var value: Offset = Offset.Zero
}

private val GlassSkSl =
    """
    uniform shader content;
    uniform float2 size;
    half4 main(float2 coord) {
        float2 uv = coord / size - 0.5;
        float r = length(uv * float2(1.0, size.y / size.x));
        float bulge = (1.0 - smoothstep(0.0, 0.5, r)) * 0.06;
        half4 c = content.eval(coord - uv * size * bulge);
        float rim = smoothstep(0.32, 0.5, r) * 0.35;
        float3 glass = mix(c.rgb, float3(1.0), 0.12 + rim);
        return half4(glass, 1.0);
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
 * Glass surface: blurred, refracted and lightly tinted copy of the backdrop under this element.
 * Icons and text are drawn on top. Plain content on Android below 13.
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

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun DrawScope.drawGlass(backdrop: GlassBackdrop, layer: GraphicsLayer, origin: Offset) {
    val w = size.width
    val h = size.height
    val shader = RuntimeShader(GlassSkSl)
    shader.setFloatUniform("size", w, h)
    val blur = RenderEffect.createBlurEffect(10f, 10f, Shader.TileMode.CLAMP)
    val refract = RenderEffect.createRuntimeShaderEffect(shader, "content")
    layer.renderEffect = RenderEffect.createChainEffect(refract, blur).asComposeRenderEffect()
    val delta = origin - backdrop.origin
    layer.record(IntSize(w.roundToInt(), h.roundToInt())) {
        translate(-delta.x, -delta.y) {
            drawLayer(backdrop.source)
        }
    }
    drawLayer(layer)
}
