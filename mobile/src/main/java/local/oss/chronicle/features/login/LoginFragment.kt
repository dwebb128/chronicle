package local.oss.chronicle.features.login

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import local.oss.chronicle.R
import local.oss.chronicle.application.ChronicleApplication
import local.oss.chronicle.data.local.PrefsRepo
import local.oss.chronicle.databinding.OnboardingLoginBinding
import local.oss.chronicle.features.auth.PlexAuthState
import timber.log.Timber
import javax.inject.Inject

class LoginFragment : Fragment() {
    companion object {
        @JvmStatic
        fun newInstance() = LoginFragment()

        const val TAG: String = "Login tag"
    }

    @Inject
    lateinit var prefsRepo: PrefsRepo

    @Inject
    lateinit var viewModelFactory: LoginViewModel.Factory

    private lateinit var loginViewModel: LoginViewModel

    private var _binding: OnboardingLoginBinding? = null
    private val binding get() = _binding!!

    override fun onAttach(context: Context) {
        ((activity as Activity).application as ChronicleApplication)
            .appComponent
            .inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        loginViewModel =
            ViewModelProvider(
                this,
                viewModelFactory,
            ).get(LoginViewModel::class.java)

        _binding = OnboardingLoginBinding.inflate(inflater, container, false)

        loginViewModel.isLoading.observe(viewLifecycleOwner) { isLoading: Boolean ->
            if (isLoading) {
                binding.loading.visibility = View.VISIBLE
            } else {
                binding.loading.visibility = View.GONE
            }
        }

        binding.oauthLogin.setOnClickListener {
            startLinkCodeAuth()
        }

        loginViewModel.errorEvent.observe(viewLifecycleOwner) { errorEvent ->
            val error = errorEvent.getContentIfNotHandled()
            if (error != null) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                Timber.e("Login error: $error")
            }
        }

        return binding.root
    }

    /**
     * Starts the plex.tv/link short-code sign-in through the shared PlexAuthCoordinator.
     */
    private fun startLinkCodeAuth() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Start auth and get state flow
                val authStateFlow = loginViewModel.startChromeCustomTabsAuth()

                // Observe auth state changes
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    authStateFlow.collect { state ->
                        handleAuthState(state)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error starting Chrome Custom Tabs auth")
                Toast.makeText(
                    requireContext(),
                    "Failed to start authentication: ${e.message}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    /**
     * Handles PlexAuthState transitions and updates UI accordingly.
     */
    private fun handleAuthState(state: PlexAuthState) {
        Timber.d("Auth state changed: $state")

        when (state) {
            is PlexAuthState.Idle -> {
                // Initial state, no action needed
                binding.loading.visibility = View.GONE
            }

            is PlexAuthState.CreatingPin -> {
                // Show loading indicator while creating PIN
                binding.loading.visibility = View.VISIBLE
            }

            is PlexAuthState.WaitingForUser -> {
                // The code is live: show it and keep it on screen while we poll.
                binding.loading.visibility = View.GONE
                showLinkCode(state.pinCode)
            }

            is PlexAuthState.Polling -> {
                // Still waiting on the user to enter the code elsewhere, so leave it visible.
                binding.loading.visibility = View.VISIBLE
            }

            is PlexAuthState.Success -> {
                // Authentication successful
                Timber.i("OAuth authentication successful")
                binding.loading.visibility = View.GONE
                Toast.makeText(
                    requireContext(),
                    getString(R.string.login_succeeded),
                    Toast.LENGTH_SHORT,
                ).show()
                // Navigator in MainActivity will handle navigation to server/library selection
            }

            is PlexAuthState.Error -> {
                // Authentication failed
                Timber.e(state.throwable, "OAuth authentication error: ${state.message}")
                binding.loading.visibility = View.GONE
                Toast.makeText(
                    requireContext(),
                    "Login failed: ${state.message}",
                    Toast.LENGTH_LONG,
                ).show()
                hideLinkCode()
                loginViewModel.resetAuth()
            }

            is PlexAuthState.Timeout -> {
                // Authentication timed out
                Timber.w("OAuth authentication timed out")
                binding.loading.visibility = View.GONE
                Toast.makeText(
                    requireContext(),
                    "Login timed out. Please try again.",
                    Toast.LENGTH_LONG,
                ).show()
                hideLinkCode()
                loginViewModel.resetAuth()
            }

            is PlexAuthState.Cancelled -> {
                // User cancelled authentication
                Timber.i("OAuth authentication cancelled: ${state.reason}")
                binding.loading.visibility = View.GONE
                Toast.makeText(
                    requireContext(),
                    "Login cancelled",
                    Toast.LENGTH_SHORT,
                ).show()
                hideLinkCode()
                loginViewModel.resetAuth()
            }
        }
    }

    /**
     * Shows the plex.tv/link code. A phone has a browser of its own, but the code flow is the
     * one the watch uses and works the same here, so both apps share a single login path.
     */
    private fun showLinkCode(code: String) {
        Timber.i("Showing plex.tv/link code")
        binding.oauthLogin.visibility = View.GONE
        binding.linkInstructions.visibility = View.VISIBLE
        binding.linkCode.visibility = View.VISIBLE
        binding.linkCode.text = code
    }

    private fun hideLinkCode() {
        binding.linkInstructions.visibility = View.GONE
        binding.linkCode.visibility = View.GONE
        binding.oauthLogin.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun onResume() {
        Timber.i("RESUMING LoginFragment")
        // No need to call checkForAccess - PlexAuthCoordinator handles polling automatically
        super.onResume()
    }
}
