package com.cocwar.di

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import com.cocwar.CocWarApplication
import com.cocwar.data.repository.WarRepository

@Composable
inline fun <reified VM : ViewModel> warViewModel(crossinline create: (WarRepository) -> VM): VM {
    val repo = (LocalContext.current.applicationContext as CocWarApplication).repository
    return viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = create(repo) as T
        }
    )
}
