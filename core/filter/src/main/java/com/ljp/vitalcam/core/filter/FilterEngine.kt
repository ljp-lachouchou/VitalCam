package com.ljp.vitalcam.core.filter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.random.Random

/** 滤镜引擎：将 FilterTemplate 参数应用到 Bitmap，返回新 Bitmap */
@Singleton
class FilterEngine @Inject constructor() {

    /** 应用滤镜，返回新 Bitmap（不修改原图） */
    fun apply(source: Bitmap, template: FilterTemplate): Bitmap {
        if (isIdentity(template)) return source.copy(Bitmap.Config.ARGB_8888, false)

        var bitmap = source

        // 1. 几何矫正（旋转 + 裁切黑边）
        if (template.autoLevelDegrees != 0f) {
            bitmap = applyRotation(bitmap, template.autoLevelDegrees)
        }

        // 2. 颜色调整（单次 ColorMatrix 合成）
        val colorMatrix = buildColorMatrix(template)
        bitmap = applyColorMatrix(bitmap, colorMatrix)

        // 3. 暗角
        if (template.vignette > 0f) {
            bitmap = applyVignette(bitmap, template.vignette)
        }

        // 4. 颗粒
        if (template.grain > 0f) {
            bitmap = applyGrain(bitmap, template.grain)
        }

        return bitmap
    }

    private fun isIdentity(t: FilterTemplate): Boolean =
        t.brightness == 0f && t.contrast == 0f && t.saturation == 0f &&
                t.warmth == 0f && t.tint == 0f && t.vignette == 0f &&
                t.sharpen == 0f && t.grain == 0f && t.autoLevelDegrees == 0f

    // ── 颜色处理 ──

    /** 合成 brightness/contrast/saturation/warmth/tint 为单个 ColorMatrix */
    private fun buildColorMatrix(template: FilterTemplate): ColorMatrix {
        val result = ColorMatrix()

        // 饱和度：-1.0→setSaturation(0), 0→1.0, +1.0→2.0
        val satMatrix = ColorMatrix()
        satMatrix.setSaturation(1f + template.saturation)
        result.postConcat(satMatrix)

        // 对比度：缩放 + 偏移
        if (template.contrast != 0f) {
            val scale = 1f + template.contrast
            val offset = 128f * (1f - scale)
            val contrastMatrix = ColorMatrix(
                floatArrayOf(
                    scale, 0f, 0f, 0f, offset,
                    0f, scale, 0f, 0f, offset,
                    0f, 0f, scale, 0f, offset,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            result.postConcat(contrastMatrix)
        }

        // 亮度：RGB 通道平移
        if (template.brightness != 0f) {
            val shift = template.brightness * 128f
            val brightnessMatrix = ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, shift,
                    0f, 1f, 0f, 0f, shift,
                    0f, 0f, 1f, 0f, shift,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            result.postConcat(brightnessMatrix)
        }

        // 色温：正值暖色（R↑B↓），负值冷色（R↓B↑）
        if (template.warmth != 0f) {
            val rShift = template.warmth * 20f
            val bShift = -template.warmth * 20f
            val warmthMatrix = ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, rShift,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, bShift,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            result.postConcat(warmthMatrix)
        }

        // 色调：正值品红（G↓R/B↑），负值绿（G↑R/B↓）
        if (template.tint != 0f) {
            val gShift = -template.tint * 15f
            val rbShift = template.tint * 7f
            val tintMatrix = ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, rbShift,
                    0f, 1f, 0f, 0f, gShift,
                    0f, 0f, 1f, 0f, rbShift,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            result.postConcat(tintMatrix)
        }

        return result
    }

    private fun applyColorMatrix(source: Bitmap, matrix: ColorMatrix): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }

    // ── 几何处理 ──

    /** 旋转并裁切到内接矩形以去除黑边 */
    private fun applyRotation(source: Bitmap, degrees: Float): Bitmap {
        val radians = Math.toRadians(abs(degrees).toDouble())
        val cosA = cos(radians).toFloat()
        // 内接矩形宽高 = 原始尺寸 * cosA（简化近似，小角度足够精确）
        val cropW = (source.width * cosA).toInt()
        val cropH = (source.height * cosA).toInt()
        if (cropW <= 0 || cropH <= 0) return source

        val matrix = Matrix().apply {
            postRotate(degrees, source.width / 2f, source.height / 2f)
        }
        val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)

        // 居中裁切
        val x = (rotated.width - cropW) / 2
        val y = (rotated.height - cropH) / 2
        val safeX = x.coerceIn(0, rotated.width - 1)
        val safeY = y.coerceIn(0, rotated.height - 1)
        val safeW = cropW.coerceAtMost(rotated.width - safeX)
        val safeH = cropH.coerceAtMost(rotated.height - safeY)

        return Bitmap.createBitmap(rotated, safeX, safeY, safeW, safeH)
    }

    // ── 效果处理 ──

    /** 暗角效果：径向渐变从透明到黑色叠加 */
    private fun applyVignette(source: Bitmap, intensity: Float): Bitmap {
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val cx = output.width / 2f
        val cy = output.height / 2f
        val radius = min(cx, cy) * 1.3f

        val alpha = (intensity * 200).toInt().coerceIn(0, 255)
        val gradient = RadialGradient(
            cx, cy, radius,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(alpha, 0, 0, 0)),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        val paint = Paint().apply { shader = gradient }
        canvas.drawRect(0f, 0f, output.width.toFloat(), output.height.toFloat(), paint)
        return output
    }

    /** 胶片颗粒：随机噪点叠加 */
    private fun applyGrain(source: Bitmap, intensity: Float): Bitmap {
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val w = output.width
        val h = output.height
        val alpha = (intensity * 60).toInt().coerceIn(0, 255)
        // 稀疏采样避免逐像素性能问题
        val step = if (w > 2000) 3 else 2
        val random = Random(System.nanoTime())
        for (y in 0 until h step step) {
            for (x in 0 until w step step) {
                val noise = random.nextInt(256)
                val pixel = output.getPixel(x, y)
                val r = (Color.red(pixel) + ((noise - 128) * intensity).toInt()).coerceIn(0, 255)
                val g = (Color.green(pixel) + ((noise - 128) * intensity).toInt()).coerceIn(0, 255)
                val b = (Color.blue(pixel) + ((noise - 128) * intensity).toInt()).coerceIn(0, 255)
                output.setPixel(x, y, Color.argb(Color.alpha(pixel), r, g, b))
            }
        }
        return output
    }
}
