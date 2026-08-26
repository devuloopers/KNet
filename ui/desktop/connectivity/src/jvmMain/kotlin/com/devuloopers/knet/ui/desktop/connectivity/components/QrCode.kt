package com.devuloopers.knet.ui.desktop.connectivity.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlin.math.min

internal class QrCodeMatrix(
    val width: Int,
    val height: Int,
    private val modules: BooleanArray,
) {
    init {
        require(width > 0 && height > 0)
        require(modules.size == width * height)
    }

    fun isDark(x: Int, y: Int): Boolean = modules[y * width + x]
}

internal fun encodeQrCode(value: String): QrCodeMatrix {
    require(value.isNotBlank()) { "QR value must not be blank." }
    val bits = QRCodeWriter().encode(
        value,
        BarcodeFormat.QR_CODE,
        MINIMUM_QR_MODULES,
        MINIMUM_QR_MODULES,
        mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to QUIET_ZONE_MODULES,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        ),
    )
    return QrCodeMatrix(
        width = bits.width,
        height = bits.height,
        modules = BooleanArray(bits.width * bits.height) { index ->
            bits[index % bits.width, index / bits.width]
        },
    )
}

/** Renders any validated KNet QR payload with stable module sizing and a scan-safe quiet zone. */
@Composable
internal fun KNetQrCode(
    value: String,
    modifier: Modifier = Modifier,
) {
    val matrix = remember(value) { encodeQrCode(value) }
    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .background(Color.White)
            .padding(8.dp),
    ) {
        val moduleSize = min(size.width / matrix.width, size.height / matrix.height)
        val renderedWidth = moduleSize * matrix.width
        val renderedHeight = moduleSize * matrix.height
        val origin = Offset(
            x = (size.width - renderedWidth) / 2f,
            y = (size.height - renderedHeight) / 2f,
        )
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix.isDark(x, y)) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(origin.x + x * moduleSize, origin.y + y * moduleSize),
                        size = Size(moduleSize, moduleSize),
                    )
                }
            }
        }
    }
}

private const val MINIMUM_QR_MODULES: Int = 33
private const val QUIET_ZONE_MODULES: Int = 2
