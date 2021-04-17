package com.techprd.httpd.flutter_httpd.storage

import android.content.Context
import javax.inject.Inject

// @Inject tells Dagger how to provide instances of this type
class SharedPreferencesStorage @Inject constructor(context: Context) : Storage {

    private val storageName = "TechPrdFileTransfer"
    private val sharedPreferences = context.getSharedPreferences(storageName, Context.MODE_PRIVATE)

    override fun setString(key: String, value: String) {
        with(sharedPreferences.edit()) {
            putString(key, value)
            apply()
            commit()
        }
    }

    override fun getString(key: String): String {
        return sharedPreferences.getString(key, "")!!
    }

}
