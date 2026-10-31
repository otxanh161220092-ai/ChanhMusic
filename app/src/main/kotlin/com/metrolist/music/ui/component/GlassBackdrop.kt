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
 * Single-pass port of the rounded-rect glass from iyinchao/liquid-glass-studio (MIT):
 * SDF edge depth, Snell-law refraction offset along the SDF normal, per-channel dispersion,
 * Fresnel rim and a glare term. Samples the blurred backdrop.
 */
private val GlassSkSl =
    """
    uniform shader content;
    uniform float2 size;
    uniform float radius;
    uniform float thickness;
    uniform float refFactor;
    uniform float refDistance;
    uniform float dispersion;
    uniform float tintAlpha;

    float sdRound(float2 p) {
        float2 b = size * 0.5 - float2(radius);
        float2 q = abs(p - size * 0.5) - b;
        return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
    }

    half4 main(float2 coord) {
        float d = sdRound(coord);
        float depth = -d;
        if (depth <= 0.0) {
            return content.eval(coord);
        }
        float e = 1.0;
        float2 g = float2(
            sdRound(coord + float2(e, 0.0)) - sdRound(coord - float2(e, 0.0)),
            sdRound(coord + float2(0.0, e)) - sdRound(coord - float2(0.0, e))
        );
        float2 n = normalize(g + 1e-6);

        float t = clamp(depth / thickness, 0.0, 1.0);
        float x = clamp(1.0 - depth / thickness, -1.0, 1.0);
        float thI = asin(x * x);
        float thT = asin(clamp(sin(thI) / refFactor, -1.0, 1.0));
        float edge = -tan(thT - thI);
        if (depth >= thickness) {
            edge = 0.0;
        }
        float2 off = -n * edge * refDistance;

        float r = content.eval(coord + off * (1.0 + dispersion)).r;
        float gg = content.eval(coord + off).g;
        float b = content.eval(coord + off * (1.0 - dispersion)).b;
        float3 col = float3(r, gg, b);

        float fresnel = pow(1.0 - t, 3.0);
        col = mix(col, float3(1.0), tintAlpha + 0.35 * fresnel);

        float glare = clamp(0.5 + 0.5 * dot(n, normalize(float2(-0.6, -0.8))), 0.0, 1.0) * (1.0 - t);
        col = mix(col, float3(1.0), 0.3 * glare * glare);
        return half4(col, 1.0);
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
    shader.setFloatUniform("radius", h / 2f)
    shader.setFloatUniform("thickness", 24.dp.toPx())
    shader.setFloatUniform("refFactor", 1.5f)
    shader.setFloatUniform("refDistance", 14.dp.toPx())
    shader.setFloatUniform("dispersion", 0.02f)
    shader.setFloatUniform("tintAlpha", 0.12f)
    layer.renderEffect = effect
    val delta = origin - backdrop.origin
    layer.record(IntSize(w.roundToInt(), h.roundToInt())) {
        translate(-delta.x, -delta.y) {
            drawLayer(backdrop.source)
        }
    }
    drawLayer(layer)
}
