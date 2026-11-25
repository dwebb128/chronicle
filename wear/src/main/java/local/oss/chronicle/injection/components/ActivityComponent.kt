package local.oss.chronicle.injection.components

import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dagger.Component
import local.oss.chronicle.application.MainActivity
import local.oss.chronicle.application.MainActivityViewModel
import local.oss.chronicle.features.bookdetails.AudiobookDetailsViewModel
import local.oss.chronicle.features.currentlyplaying.CurrentlyPlayingViewModel
import local.oss.chronicle.features.login.ChooseLibraryViewModel
import local.oss.chronicle.features.login.ChooseServerViewModel
import local.oss.chronicle.features.login.ChooseUserViewModel
import local.oss.chronicle.features.login.LoginViewModel
import local.oss.chronicle.features.player.MediaServiceConnection
import local.oss.chronicle.features.player.ProgressUpdater
import local.oss.chronicle.features.settings.SettingsViewModel
import local.oss.chronicle.injection.modules.ActivityModule
import local.oss.chronicle.injection.scopes.ActivityScope

@ActivityScope
@Component(dependencies = [AppComponent::class], modules = [ActivityModule::class])
interface ActivityComponent {
    fun progressUpdater(): ProgressUpdater

    fun localBroadcastManager(): LocalBroadcastManager

    fun mediaServiceConnection(): MediaServiceConnection

    fun mainActivityViewModelFactory(): MainActivityViewModel.Factory

    fun currentPlayingViewModelFactory(): CurrentlyPlayingViewModel.Factory

    fun audiobookDetailsViewModelFactory(): AudiobookDetailsViewModel.Factory

    fun settingsViewModelFactory(): SettingsViewModel.Factory

    fun loginViewModelFactory(): LoginViewModel.Factory

    fun chooseServerViewModelFactory(): ChooseServerViewModel.Factory

    fun chooseUserViewModelFactory(): ChooseUserViewModel.Factory

    fun chooseLibraryViewModelFactory(): ChooseLibraryViewModel.Factory

    fun inject(activity: MainActivity)
}
