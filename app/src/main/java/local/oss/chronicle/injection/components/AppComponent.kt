package local.oss.chronicle.injection.components

import com.facebook.imagepipeline.core.ImagePipelineConfig
import dagger.Component
import local.oss.chronicle.application.ChronicleApplication
import local.oss.chronicle.application.ChronicleBillingManager
import local.oss.chronicle.features.login.ChooseLibraryFragment
import local.oss.chronicle.features.login.ChooseServerFragment
import local.oss.chronicle.features.login.ChooseUserFragment
import local.oss.chronicle.features.login.LoginFragment
import local.oss.chronicle.features.login.PlexOAuthDialogFragment
import local.oss.chronicle.injection.modules.AppModule
import javax.inject.Singleton

@Singleton
@Component(modules = [AppModule::class])
interface AppComponent : CoreComponent {
    fun frescoConfig(): ImagePipelineConfig

    //    fun plexMediaSource(): PlexMediaSource
    fun billingManager(): ChronicleBillingManager

    // Inject
    fun inject(chronicleApplication: ChronicleApplication)

    fun inject(loginFragment: LoginFragment)

    fun inject(plexOAuthDialogFragment: PlexOAuthDialogFragment)

    fun inject(chooseLibraryFragment: ChooseLibraryFragment)

    fun inject(chooseUserFragment: ChooseUserFragment)

    fun inject(chooseServerActivity: ChooseServerFragment)
}
