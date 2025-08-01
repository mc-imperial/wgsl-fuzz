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

package com.wgslfuzz.tools

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
