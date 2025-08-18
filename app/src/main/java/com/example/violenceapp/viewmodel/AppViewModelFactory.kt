package com.example.violenceapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.violenceapp.data.SharedPreferencesManager

class AppViewModelFactory(private val prefsManager: SharedPreferencesManager) :
        ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
            return AppViewModel(prefsManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
