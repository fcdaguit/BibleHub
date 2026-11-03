package com.sermonstudio.app.di

import android.content.Context
import androidx.room.Room
import com.sermonstudio.app.data.db.AppDatabase
import com.sermonstudio.app.data.search.HashingEmbedder
import com.sermonstudio.app.data.search.TextEmbedder
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun database(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "sermonstudio.db").build()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    /** Swap HashingEmbedder for a neural embedder here (see docs/ARCHITECTURE.md). */
    @Binds abstract fun embedder(impl: HashingEmbedder): TextEmbedder
}
