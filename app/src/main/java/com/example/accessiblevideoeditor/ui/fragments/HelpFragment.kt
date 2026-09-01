package com.example.accessiblevideoeditor.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.accessiblevideoeditor.R
import com.example.accessiblevideoeditor.databinding.FragmentHelpBinding
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HelpFragment : Fragment() {

    private var _binding: FragmentHelpBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHelpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.tvVersionInfo.text = "الإصدار الحالي: ${com.example.accessiblevideoeditor.BuildConfig.VERSION_NAME}"

        fetchLatestReleaseNotes()
        fetchDynamicGuide()

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        binding.svGuide.visibility = View.VISIBLE
                        binding.svAbout.visibility = View.GONE
                        binding.svReleaseNotes.visibility = View.GONE
                    }
                    1 -> {
                        binding.svGuide.visibility = View.GONE
                        binding.svAbout.visibility = View.VISIBLE
                        binding.svReleaseNotes.visibility = View.GONE
                    }
                    2 -> {
                        binding.svGuide.visibility = View.GONE
                        binding.svAbout.visibility = View.GONE
                        binding.svReleaseNotes.visibility = View.VISIBLE
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun fetchLatestReleaseNotes() {
        val currentContext = context ?: return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            val updateInfo = withContext(Dispatchers.IO) {
                try {
                    val url = java.net.URL("https://raw.githubusercontent.com/my-nvda/accVideoEditorReleases/main/update.json?t=${System.currentTimeMillis()}")
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.useCaches = false
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 8000
                    connection.readTimeout = 8000
                    if (connection.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                        val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                        val json = org.json.JSONObject(jsonStr)
                        com.example.accessiblevideoeditor.updater.AppUpdater.UpdateInfo(
                            versionCode = json.getInt("versionCode"),
                            versionName = json.getString("versionName"),
                            downloadUrl = json.getString("downloadUrl"),
                            releaseNotes = json.optString("releaseNotes", "").replace("\\n", "\n")
                        )
                    } else null
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

            if (updateInfo != null && _binding != null) {
                val cardView = com.google.android.material.card.MaterialCardView(currentContext).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 0, 0, 32)
                    }
                    cardElevation = 4f
                }
                val textView = android.widget.TextView(currentContext).apply {
                    setPadding(32, 32, 32, 32)
                    text = "أحدث إصدار متوفر: ${updateInfo.versionName}\n\nملاحظات الإصدار الجديد:\n${updateInfo.releaseNotes}"
                    setTextIsSelectable(true)
                }
                cardView.addView(textView)
                binding.llReleaseNotesContainer.addView(cardView, 0)
            }
        }
    }

    private fun fetchDynamicGuide() {
        val currentContext = context ?: return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            val guideMarkdown = withContext(Dispatchers.IO) {
                try {
                    val url = java.net.URL("https://raw.githubusercontent.com/my-nvda/accVideoEditorReleases/main/guide.md?t=${System.currentTimeMillis()}")
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.useCaches = false
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 8000
                    connection.readTimeout = 8000
                    if (connection.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                        connection.inputStream.bufferedReader().use { it.readText() }
                    } else null
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

            if (guideMarkdown != null && _binding != null) {
                val html = renderMarkdownToHtml(guideMarkdown)
                val spanned = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY)
                } else {
                    @Suppress("DEPRECATION")
                    android.text.Html.fromHtml(html)
                }
                
                binding.tvDynamicGuide.text = spanned
                binding.tvDynamicGuide.visibility = View.VISIBLE
                
                // Hide local fallback TextViews
                for (i in 0 until binding.llGuideContainer.childCount) {
                    val child = binding.llGuideContainer.getChildAt(i)
                    if (child.id != R.id.tvDynamicGuide) {
                        child.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun renderMarkdownToHtml(markdown: String): String {
        var html = markdown
        // Headers
        html = html.replace(Regex("(?m)^### (.*)$"), "<h3>$1</h3>")
        html = html.replace(Regex("(?m)^## (.*)$"), "<h2>$1</h2>")
        html = html.replace(Regex("(?m)^# (.*)$"), "<h1>$1</h1>")
        // Bold
        html = html.replace(Regex("\\*\\*(.*?)\\*\\*"), "<b>$1</b>")
        // Italic
        html = html.replace(Regex("\\*(.*?)\\*"), "<i>$1</i>")
        // Line breaks
        html = html.replace("\n", "<br/>")
        return html
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
