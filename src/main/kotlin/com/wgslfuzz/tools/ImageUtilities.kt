package com.wgslfuzz.tools

import java.io.File
import javax.imageio.ImageIO

fun identicalImages(file1: File, file2: File): Boolean {
    val img1 = ImageIO.read(file1)
    val img2 = ImageIO.read(file2)

    if (img1.width != img2.width || img1.height != img2.height) {
        return false
    }

    for (y in 0 until img1.height) {
        for (x in 0 until img1.width) {
            if (img1.getRGB(x, y) != img2.getRGB(x, y)) {
                return false
            }
        }
    }
    return true
}
