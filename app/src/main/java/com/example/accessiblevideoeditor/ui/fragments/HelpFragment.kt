package com.example.accessiblevideoeditor.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.accessiblevideoeditor.databinding.FragmentHelpBinding
import com.google.android.material.tabs.TabLayout

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
