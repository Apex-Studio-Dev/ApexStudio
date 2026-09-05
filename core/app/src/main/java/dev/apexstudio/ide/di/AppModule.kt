package dev.apexstudio.ide.di

import dev.apexstudio.ide.git.core.GitCredentialsManager
//import dev.apexstudio.ide.roomData.recentproject.RecentProjectRoomDatabase
import dev.apexstudio.ide.viewmodel.CloneRepositoryViewModel
import dev.apexstudio.ide.viewmodel.GitBottomSheetViewModel
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import org.koin.core.module.dsl.viewModel
import dev.apexstudio.ide.roomData.recentproject.RecentProjectRoomDatabase
import dev.apexstudio.ide.viewmodel.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidApplication

val coreModule =
	module {
		 
		viewModel {
            GitBottomSheetViewModel(get())
		}
        viewModel { MainViewModel(get()) }
        viewModel { CloneRepositoryViewModel(get()) }


        single<CoroutineScope> {
            CoroutineScope(SupervisorJob() + Dispatchers.IO)
        }

        single {
            RecentProjectRoomDatabase.getDatabase(androidApplication(), get())
        }

        single {
           get<RecentProjectRoomDatabase>().recentProjectDao()
        }

        single { GitCredentialsManager(get()) }

	}