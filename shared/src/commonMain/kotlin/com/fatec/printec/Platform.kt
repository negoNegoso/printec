package com.fatec.printec

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform