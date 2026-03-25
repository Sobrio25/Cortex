package com.aiagents.app.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface HuggingFaceApiService {
    @GET("api/models")
    suspend fun searchModels(
        @Query("author") author: String,
        @Query("limit") limit: Int = 50
    ): List<HFModelInfo>

    @GET("api/models/{repoId}/tree/main")
    suspend fun getModelFiles(
        @Path("repoId", encoded = true) repoId: String
    ): List<HFFileInfo>
}

data class HFModelInfo(
    @SerializedName("id") val id: String,
    @SerializedName("tags") val tags: List<String> = emptyList(),
    @SerializedName("downloads") val downloads: Int = 0,
    @SerializedName("gated") val gated: Any? = null // can be false or a string like "auto"
) {
    val isGated: Boolean get() = gated != null && gated != false
}

data class HFFileInfo(
    @SerializedName("path") val filename: String = "",
    @SerializedName("size") val size: Long = 0,
    @SerializedName("type") val type: String = ""
)
