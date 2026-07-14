package com.aiagents.app.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface HuggingFaceApiService {
    @GET("api/models")
    suspend fun searchModels(
        @Query("search") search: String,
        @Query("sort") sort: String = "downloads",
        @Query("direction") direction: Int = -1,
        @Query("limit") limit: Int = 20,
        @Query("full") full: Boolean = true,
        @Header("Authorization") authorization: String? = null
    ): Response<List<HFModelInfo>>

    @GET
    suspend fun getModelsPage(
        @Url pageUrl: String,
        @Header("Authorization") authorization: String? = null
    ): Response<List<HFModelInfo>>

    @GET("api/models/{repoId}")
    suspend fun getModelInfo(
        @Path("repoId", encoded = true) repoId: String,
        @Header("Authorization") authorization: String? = null
    ): Response<HFModelInfo>

    @GET("api/models/{repoId}/tree/{revision}")
    suspend fun getModelFiles(
        @Path("repoId", encoded = true) repoId: String,
        @Path("revision") revision: String = "main",
        @Query("recursive") recursive: Boolean = true,
        @Query("expand") expand: Boolean = false,
        @Header("Authorization") authorization: String? = null
    ): Response<List<HFFileInfo>>

    @GET
    suspend fun getModelFilesPage(
        @Url pageUrl: String,
        @Header("Authorization") authorization: String? = null
    ): Response<List<HFFileInfo>>
}

data class HFModelInfo(
    @SerializedName("id") val id: String,
    @SerializedName("author") val author: String? = null,
    @SerializedName("tags") val tags: List<String> = emptyList(),
    @SerializedName("downloads") val downloads: Long = 0,
    @SerializedName("likes") val likes: Int = 0,
    @SerializedName("gated") val gated: Any? = null,
    @SerializedName("private") val isPrivate: Boolean = false,
    @SerializedName("sha") val sha: String? = null,
    @SerializedName("lastModified") val lastModified: String? = null,
    @SerializedName("library_name") val libraryName: String? = null,
    @SerializedName("siblings") val siblings: List<HFSiblingInfo> = emptyList()
) {
    // The Hub returns either false, true, or a string such as "auto" here.
    val isGated: Boolean get() = gated != null && gated != false
}

data class HFSiblingInfo(
    @SerializedName("rfilename") val fileName: String = ""
)

data class HFFileInfo(
    @SerializedName("path") val fileName: String = "",
    @SerializedName("size") val size: Long = 0,
    @SerializedName("type") val type: String = "",
    @SerializedName("lfs") val lfs: HFLfsInfo? = null
) {
    val resolvedSize: Long get() = size.takeIf { it > 0 } ?: lfs?.size ?: 0
}

data class HFLfsInfo(
    @SerializedName("oid") val oid: String? = null,
    @SerializedName("size") val size: Long = 0
)
