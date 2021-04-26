package com.techprd.httpd.flutter_httpd.di

import com.techprd.httpd.flutter_httpd.storage.SharedPreferencesStorage
import com.techprd.httpd.flutter_httpd.storage.Storage
import dagger.Binds
import dagger.Module

// Tells Dagger this is a Dagger module
@Module
abstract class StorageModule {

    // Makes Dagger provide SharedPreferencesStorage when a Storage type is requested
    @Binds
    abstract fun provideStorage(storage: SharedPreferencesStorage): Storage
}
