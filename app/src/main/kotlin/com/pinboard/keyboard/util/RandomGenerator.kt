package com.pinboard.keyboard.util

import java.security.SecureRandom

object RandomGenerator {

    private const val CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    private val random = SecureRandom()

    fun generate(length: Int): String {
        val safeLength = length.coerceIn(6, 20)
        val sb = StringBuilder(safeLength)
        repeat(safeLength) {
            sb.append(CHARS[random.nextInt(CHARS.length)])
        }
        return sb.toString()
    }
}
