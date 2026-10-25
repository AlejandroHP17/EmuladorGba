package pelkidev.com.mx.emuladorgba.domain

fun isGbaFileName(fileName: String): Boolean =
    fileName.trim().endsWith(".gba", ignoreCase = true)
