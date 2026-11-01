package io.github.thisisartman.collectibles.debug

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LogModule {
    @Provides
    @Singleton
    fun fileLog(@ApplicationContext context: Context): FileLog = FileLog(File(context.filesDir, "logs"))
}
