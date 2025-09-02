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

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

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

// Implemented using: https://medium.com/@tsdhrm/ssim-measuring-what-we-actually-see-5350c4a0c025
// Original paper:
// Zhou Wang, A. C. Bovik, H. R. Sheikh and E. P. Simoncelli, "Image quality assessment: from error visibility to
// structural similarity," in IEEE Transactions on Image Processing, vol. 13, no. 4, pp. 600-612, April 2004,
// doi: 10.1109/TIP.2003.819861.
// keywords: {Image quality;Humans;Transform coding;Visual system;Visual perception;Data mining;Layout;Quality assessment;Degradation;Indexes},
class StructuralSimilarityIndexComparison(
    private val threshold: Double,
    private val numBitsPerPixel: Int,
    private val lengthOfWindow: Int = 11,
) : CompareImages {
    override fun equivalentImages(
        file1: File,
        file2: File,
    ): Boolean {
        val img1 = ImageIO.read(file1)
        val img2 = ImageIO.read(file2)

        if (img1.width != img2.width || img1.height != img2.height) {
            return false
        }

        return ssim(img1, img2) > threshold
    }

    private fun ssim(
        xImage: BufferedImage,
        yImage: BufferedImage,
    ): Double {
        require(xImage.width == yImage.width && xImage.height == yImage.height)

        var output = 0.0
        for (i in 0 until xImage.width - lengthOfWindow) {
            for (j in 0 until xImage.height - lengthOfWindow) {
                output += localSsim(xImage, yImage, i, j)
            }
        }

        return output / ((xImage.width - lengthOfWindow) * (yImage.height - lengthOfWindow))
    }

    private fun localSsim(
        xImage: BufferedImage,
        yImage: BufferedImage,
        x: Int,
        y: Int,
    ): Double {
        val xLocalMean = weightedLocalMean(xImage, x, y)
        val yLocalMean = weightedLocalMean(yImage, x, y)

        val xLocalContrast = weightedLocalContrast(xImage, x, y, xLocalMean)
        val yLocalContrast = weightedLocalContrast(yImage, x, y, yLocalMean)

        val localStructure = weightedLocalStructure(xImage, yImage, x, y, xLocalMean, yLocalMean)

        return localLuminanceDifference(xLocalMean, yLocalMean, numBitsPerPixel) *
            localContrastDifference(xLocalContrast, yLocalContrast, numBitsPerPixel) *
            localStructureDifference(xLocalContrast, yLocalContrast, localStructure, numBitsPerPixel)
    }

    private fun localLuminanceDifference(
        xLocalMean: Double,
        yLocalMean: Double,
        numBitsPerPixel: Int,
    ): Double {
        val c1 = 0.01 * (2.0.pow(numBitsPerPixel.toDouble()) - 1.0)
        return (2.0 * xLocalMean * yLocalMean + c1) / (xLocalMean.squared() + yLocalMean.squared() + c1)
    }

    private fun localContrastDifference(
        xLocalContrast: Double,
        yLocalContrast: Double,
        numBitsPerPixel: Int,
    ): Double {
        val c2 = 0.03 * (2.0.pow(numBitsPerPixel.toDouble()) - 1.0)
        return (2 * xLocalContrast * yLocalContrast + c2) / (xLocalContrast.squared() * yLocalContrast.squared() + c2)
    }

    private fun localStructureDifference(
        xLocalContrast: Double,
        yLocalContrast: Double,
        localStructure: Double,
        numBitsPerPixel: Int,
    ): Double {
        val c3 = 0.015 * (2.0.pow(numBitsPerPixel.toDouble()) - 1.0)
        return (localStructure + c3) / (xLocalContrast * yLocalContrast + c3)
    }

    private fun weightedLocalMean(
        image: BufferedImage,
        x: Int,
        y: Int,
    ): Double {
        checkBounds(image, x, y)

        var output: Double = 0.0

        for (i in 0 until lengthOfWindow) {
            for (j in 0 until lengthOfWindow) {
                val pixel = image.getRGB(x + i, y + j)

                output += weight(i, j) * (pixel.red() + pixel.green() + pixel.blue() + pixel.alpha())
            }
        }
        return output
    }

    private fun weightedLocalContrast(
        image: BufferedImage,
        x: Int,
        y: Int,
        localMean: Double = weightedLocalMean(image, x, y),
    ): Double {
        checkBounds(image, x, y)

        var outputSquared: Double = 0.0
        for (i in 0 until lengthOfWindow) {
            for (j in 0 until lengthOfWindow) {
                val pixel = image.getRGB(x + i, y + j)

                val averagePixelValue = (pixel.red() + pixel.green() + pixel.blue() + pixel.alpha()) / 4

                outputSquared += weight(i, j) * (averagePixelValue - localMean).squared()
            }
        }
        return sqrt(outputSquared)
    }

    private fun weightedLocalStructure(
        xImage: BufferedImage,
        yImage: BufferedImage,
        x: Int,
        y: Int,
        xLocalMean: Double = weightedLocalMean(xImage, x, y),
        yLocalMean: Double = weightedLocalMean(yImage, x, y),
    ): Double {
        checkBounds(xImage, x, y)
        checkBounds(yImage, x, y)

        var output: Double = 0.0
        for (i in 0 until lengthOfWindow) {
            for (j in 0 until lengthOfWindow) {
                val xPixel = xImage.getRGB(x + i, y + j)
                val yPixel = yImage.getRGB(x + i, y + j)

                val averageXPixelValue = (xPixel.red() + xPixel.green() + xPixel.blue() + xPixel.alpha()) / 4
                val averageYPixelValue = (yPixel.red() + yPixel.green() + yPixel.blue() + yPixel.alpha()) / 4

                output += weight(i, j) * (averageXPixelValue - xLocalMean) * (averageYPixelValue - yLocalMean)
            }
        }
        return output
    }

    private fun weight(
        i: Int,
        j: Int,
    ): Double {
        val c = (lengthOfWindow + 1) / 2
        return (1 / (2 * Math.PI * (1.5).squared())) * exp(-((i - c).squared() + (j - c).squared()) / (2 * (1.5).squared()))
    }

    private fun checkBounds(
        image: BufferedImage,
        x: Int,
        y: Int,
    ) {
        require(x >= 0) { "x cannot be negative" }
        require(y >= 0) { "y cannot be negative" }
        require(x + lengthOfWindow <= image.width) { "x + lengthOfWindow is not within the image" }
        require(y + lengthOfWindow <= image.height) { "y + lengthOfWindow is not within the image" }
    }
}

private fun Int.squared(): Int = this * this

private fun Int.blue(): Int = this and 0xff

private fun Int.green(): Int = (this and 0xff00) shr 8

private fun Int.red(): Int = (this and 0xff0000) shr 16

private fun Int.alpha(): Int = this ushr 24

private fun Double.squared(): Double = this.pow(2)
