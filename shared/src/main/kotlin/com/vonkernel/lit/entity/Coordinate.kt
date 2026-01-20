package com.vonkernel.lit.entity

/**
 * OpenSearch geo-point 형식에 맞춘 지리 좌표
 */
data class Coordinate(
    val lat: Double,  // 위도 (Latitude)
    val lon: Double   // 경도 (Longitude)
)
