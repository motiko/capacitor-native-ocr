package dev.motiko.nativeocr

import com.getcapacitor.Logger

class NativeOcr {

    fun echo(value: String): String {
        Logger.info("Echo", value)

        return value
    }
}
