package com.aiagents.app.di

import com.aiagents.app.data.terminal.IntentWhatsAppHandoffBackend
import com.aiagents.app.data.terminal.WhatsAppHandoffBackend
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AssistantActionModule {
    @Binds
    @Singleton
    abstract fun bindWhatsAppHandoffBackend(
        implementation: IntentWhatsAppHandoffBackend
    ): WhatsAppHandoffBackend
}
