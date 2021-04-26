package com.techprd.httpd.flutter_httpd.di

import android.content.Context
import com.techprd.httpd.flutter_httpd.FileLibraryService
import com.techprd.httpd.flutter_httpd.StorageUtils
import dagger.BindsInstance
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(modules = [StorageModule::class, AppSubcomponents::class])
interface AppComponent {

    // Factory to create instances of the AppComponent
    @Component.Factory
    interface Factory {
        // With @BindsInstance, the Context passed in will be available in the graph
        fun create(@BindsInstance context: Context): AppComponent
    }

    // Types that can be retrieved from the graph
    fun fileService(): FileLibraryService
    fun storageUtils(): StorageUtils
}
