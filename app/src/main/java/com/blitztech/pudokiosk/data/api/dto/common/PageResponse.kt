package com.blitztech.pudokiosk.data.api.dto.common

import com.blitztech.pudokiosk.data.api.dto.location.CityDto
import com.blitztech.pudokiosk.data.api.dto.location.SuburbDto
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Concrete wrappers for Spring Boot Page<T> responses.
 * Backend returns: {"content":[...], "totalElements":..., "totalPages":..., ...}
 * Moshi codegen requires concrete types — no generics allowed with @JsonClass.
 */

@JsonClass(generateAdapter = true)
data class PageCity(
    @Json(name = "content") val content: List<CityDto> = emptyList(),
    @Json(name = "totalElements") val totalElements: Long = 0,
    @Json(name = "totalPages") val totalPages: Int = 0,
    @Json(name = "last") val last: Boolean = true
)

@JsonClass(generateAdapter = true)
data class PageSuburb(
    @Json(name = "content") val content: List<SuburbDto> = emptyList(),
    @Json(name = "totalElements") val totalElements: Long = 0,
    @Json(name = "totalPages") val totalPages: Int = 0,
    @Json(name = "last") val last: Boolean = true
)

@JsonClass(generateAdapter = true)
data class PageContents(
    @Json(name = "content") val content: List<ContentsDto> = emptyList(),
    @Json(name = "totalElements") val totalElements: Long = 0,
    @Json(name = "totalPages") val totalPages: Int = 0,
    @Json(name = "last") val last: Boolean = true
)

@JsonClass(generateAdapter = true)
data class ContentsDto(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "status") val status: String? = null
)
