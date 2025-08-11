/*
 * Copyright 2025 The wgsl-fuzz Project Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wgslfuzz.utils

import java.io.File
import javax.imageio.ImageIO

interface CompareImages {
    fun equivalentImages(
        file1: File,
        file2: File,
    ): Boolean
}

object IdenticalImageCompare : CompareImages {
    override fun equivalentImages(
        file1: File,
        file2: File,
    ): Boolean {
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
}

class MSEImageCompare(
    private val thresholdMSE: Double,
) : CompareImages {
    init {
        require(thresholdMSE > 0) { "thresholdMSE must be bigger than 0" }
    }

    override fun equivalentImages(
        file1: File,
        file2: File,
    ): Boolean {
        val img1 = ImageIO.read(file1)
        val img2 = ImageIO.read(file2)

        if (img1.width != img2.width || img1.height != img2.height) {
            return false
        }

        val numberPixelValues = 4.0 * img1.height.toDouble() * img1.width.toDouble()

        var mse: Double = 0.0
        for (y in 0 until img1.height) {
            for (x in 0 until img1.width) {
                val img1Pixel = img1.getRGB(x, y)
                val img2Pixel = img2.getRGB(x, y)
                mse += (img1Pixel.red() - img2Pixel.red()).squared() / numberPixelValues
                mse += (img1Pixel.green() - img2Pixel.green()).squared() / numberPixelValues
                mse += (img1Pixel.blue() - img2Pixel.blue()).squared() / numberPixelValues
                mse += (img1Pixel.alpha() - img2Pixel.alpha()).squared() / numberPixelValues

                if (mse > thresholdMSE) {
                    return false
                }
            }
        }

        println("mse: $mse")
        return mse < thresholdMSE
    }
}

private fun Int.squared(): Int = this * this

private fun Int.blue(): Int = this and 0xff

private fun Int.green(): Int = (this and 0xff00) shr 8

private fun Int.red(): Int = (this and 0xff0000) shr 16

private fun Int.alpha(): Int = this ushr 24
