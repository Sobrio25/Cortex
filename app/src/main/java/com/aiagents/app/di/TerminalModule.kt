package com.aiagents.app.di

import com.aiagents.app.data.local.AgentDao
import com.aiagents.app.data.local.CommandPermissionDao
import com.aiagents.app.data.repository.FileRepository
import com.aiagents.app.data.terminal.AgentCreatorToolHandler
import com.aiagents.app.data.terminal.AgentSelectionToolHandler
import com.aiagents.app.data.terminal.BraveSearchToolHandler
import com.aiagents.app.data.terminal.DuckDuckGoSearchToolHandler
import com.aiagents.app.data.terminal.GoogleMapsToolHandler
import com.aiagents.app.data.terminal.SerpAPIToolHandler
import com.aiagents.app.data.terminal.CanvaToolHandler
import com.aiagents.app.data.terminal.FinanceToolHandler
import com.aiagents.app.data.terminal.PresentationToolHandler
import com.aiagents.app.data.terminal.PubMedToolHandler
import com.aiagents.app.data.terminal.AcademicSearchToolHandler
import com.aiagents.app.data.terminal.WeatherToolHandler
import com.aiagents.app.data.terminal.ImageGenerationToolHandler
import com.aiagents.app.data.terminal.CommandPermissionManager
import com.aiagents.app.data.local.FinanceDao
import com.aiagents.app.data.terminal.FileToolHandler
import com.aiagents.app.data.terminal.ShellExecutor
import com.aiagents.app.data.terminal.ToolHandler
import com.aiagents.app.data.google.discovery.GoogleDiscoveryService
import com.aiagents.app.data.google.discovery.GoogleApiExecutor
import com.aiagents.app.data.terminal.GoogleWorkspaceToolHandler
import com.aiagents.app.data.auth.GoogleWorkspaceOAuthManager
import com.aiagents.app.data.local.SecurePreferences
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TerminalModule {

    @Provides
    @Singleton
    fun provideShellExecutor(): ShellExecutor = ShellExecutor()

    @Provides
    @Singleton
    fun provideCommandPermissionManager(
        commandPermissionDao: CommandPermissionDao,
        shellExecutor: ShellExecutor
    ): CommandPermissionManager = CommandPermissionManager(commandPermissionDao, shellExecutor)

    @Provides
    @Singleton
    fun provideToolHandler(
        shellExecutor: ShellExecutor,
        commandPermissionManager: CommandPermissionManager
    ): ToolHandler = ToolHandler(shellExecutor, commandPermissionManager)

    @Provides
    @Singleton
    fun provideFileToolHandler(
        fileRepository: FileRepository
    ): FileToolHandler = FileToolHandler(fileRepository)

    @Provides
    @Singleton
    fun provideAgentSelectionToolHandler(
        agentDao: AgentDao
    ): AgentSelectionToolHandler = AgentSelectionToolHandler(agentDao)

    @Provides
    @Singleton
    fun provideAgentCreatorToolHandler(
        agentDao: AgentDao
    ): AgentCreatorToolHandler = AgentCreatorToolHandler(agentDao)

    @Provides
    @Singleton
    fun provideDuckDuckGoSearchToolHandler(
        okHttpClient: OkHttpClient
    ): DuckDuckGoSearchToolHandler = DuckDuckGoSearchToolHandler(okHttpClient)

    @Provides
    @Singleton
    fun provideBraveSearchToolHandler(
        okHttpClient: OkHttpClient
    ): BraveSearchToolHandler = BraveSearchToolHandler(okHttpClient)

    @Provides
    @Singleton
    fun provideGoogleMapsToolHandler(
        okHttpClient: OkHttpClient
    ): GoogleMapsToolHandler = GoogleMapsToolHandler(okHttpClient)

    @Provides
    @Singleton
    fun provideSerpAPIToolHandler(
        okHttpClient: OkHttpClient
    ): SerpAPIToolHandler = SerpAPIToolHandler(okHttpClient)

    @Provides
    @Singleton
    fun provideCanvaToolHandler(
        okHttpClient: OkHttpClient
    ): CanvaToolHandler = CanvaToolHandler(okHttpClient)

    @Provides
    @Singleton
    fun providePubMedToolHandler(
        okHttpClient: OkHttpClient
    ): PubMedToolHandler = PubMedToolHandler(okHttpClient)

    @Provides
    @Singleton
    fun providePresentationToolHandler(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient
    ): PresentationToolHandler = PresentationToolHandler(context, okHttpClient)

    @Provides
    @Singleton
    fun provideFinanceToolHandler(
        @ApplicationContext context: Context,
        financeDao: FinanceDao
    ): FinanceToolHandler = FinanceToolHandler(context, financeDao)

    @Provides
    @Singleton
    fun provideAcademicSearchToolHandler(
        okHttpClient: OkHttpClient
    ): AcademicSearchToolHandler = AcademicSearchToolHandler(okHttpClient)

    @Provides
    @Singleton
    fun provideWeatherToolHandler(
        okHttpClient: OkHttpClient
    ): WeatherToolHandler = WeatherToolHandler(okHttpClient)

    @Provides
    @Singleton
    fun provideImageGenerationToolHandler(
        okHttpClient: OkHttpClient
    ): ImageGenerationToolHandler = ImageGenerationToolHandler(okHttpClient)

    @Provides
    @Singleton
    fun provideGoogleDiscoveryService(
        okHttpClient: OkHttpClient
    ): GoogleDiscoveryService = GoogleDiscoveryService(okHttpClient)

    @Provides
    @Singleton
    fun provideGoogleApiExecutor(
        okHttpClient: OkHttpClient
    ): GoogleApiExecutor = GoogleApiExecutor(okHttpClient)

    @Provides
    @Singleton
    fun provideGoogleWorkspaceToolHandler(
        discoveryService: GoogleDiscoveryService,
        apiExecutor: GoogleApiExecutor
    ): GoogleWorkspaceToolHandler = GoogleWorkspaceToolHandler(discoveryService, apiExecutor)

    @Provides
    @Singleton
    fun provideGoogleWorkspaceOAuthManager(
        okHttpClient: OkHttpClient,
        securePreferences: SecurePreferences
    ): GoogleWorkspaceOAuthManager = GoogleWorkspaceOAuthManager(okHttpClient, securePreferences)
}
