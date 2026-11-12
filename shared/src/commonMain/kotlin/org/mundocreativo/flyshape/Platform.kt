package org.mundocreativo.flyshape

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform