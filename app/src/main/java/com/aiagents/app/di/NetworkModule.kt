package com.aiagents.app.di

import android.content.Context
import com.aiagents.app.data.local.LocalModelRepository
import com.aiagents.app.data.local.SecurePreferences
import com.aiagents.app.data.remote.AIClientFactory
import com.aiagents.app.data.remote.OpenRouterClient
import com.aiagents.app.data.remote.GoogleAIClient
import com.aiagents.app.data.remote.OpenAIClient
import com.aiagents.app.data.remote.OllamaClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.aiagents.app.data.remote.HuggingFaceApiService
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .callTimeout(600, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideHuggingFaceApiService(): HuggingFaceApiService {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl("https://huggingface.co/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(HuggingFaceApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideAIClientFactory(
        okHttpClient: OkHttpClient,
        localModelRepository: LocalModelRepository,
        @ApplicationContext context: Context,
        securePreferences: SecurePreferences
    ): AIClientFactory {
        return AIClientFactory(okHttpClient, localModelRepository, context, securePreferences)
    }
}
