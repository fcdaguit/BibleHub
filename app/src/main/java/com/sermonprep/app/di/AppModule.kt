package com.sermonprep.app.di

import android.content.Context
import androidx.room.Room
import com.sermonprep.app.data.db.AppDatabase
import com.sermonprep.app.data.db.BibleDao
import com.sermonprep.app.data.db.MarkDao
import com.sermonprep.app.data.db.SermonDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun db(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "sermonprep.db").build()

    @Provides fun bibleDao(db: AppDatabase): BibleDao = db.bibleDao()
    @Provides fun sermonDao(db: AppDatabase): SermonDao = db.sermonDao()
    @Provides fun markDao(db: AppDatabase): MarkDao = db.markDao()
}
