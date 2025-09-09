package com.wgslfuzz.utils

inline fun <reified R> Iterable<*>.containsInstanceOf(): Boolean = this.any { it is R }
