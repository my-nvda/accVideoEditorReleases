package com.example.accessiblevideoeditor.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.PopupMenu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.accessiblevideoeditor.R
import com.example.accessiblevideoeditor.data.UnifiedProjectModel
import com.example.accessiblevideoeditor.data.UnifiedProjectManager
import com.example.accessiblevideoeditor.databinding.FragmentProjectsDashboardBinding
import com.example.accessiblevideoeditor.databinding.ItemProjectBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProjectsDashboardFragment : Fragment() {

    private var _binding: FragmentProjectsDashboardBinding? = null
    private val binding get() = _binding!!
    private val projectsList = mutableListOf<UnifiedProjectModel>()
    private lateinit var adapter: ProjectsAdapter

    private val selectVideoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val currentContext = context ?: return@registerForActivityResult
        if (uri != null) {
            val name = getString(R.string.btn_new_project) + " " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            val project = UnifiedProjectModel(name = name, videoPath = uri.toString())
            UnifiedProjectManager.saveProject(currentContext, project)
            
            val bundle = Bundle().apply {
                putString("projectId", project.id)
            }
            try {
                findNavController().navigate(R.id.action_projectsDashboardFragment_to_unifiedWorkspaceFragment, bundle)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProjectsDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.topAppBar.setNavigationOnClickListener {
            try { findNavController().navigateUp() } catch (_: Exception) {}
        }

        binding.btnNewProject.setOnClickListener {
            selectVideoLauncher.launch("video/*")
        }

        adapter = ProjectsAdapter()
        binding.rvProjects.layoutManager = LinearLayoutManager(context)
        binding.rvProjects.adapter = adapter

        loadProjects()
    }

    private fun loadProjects() {
        val currentContext = context ?: return
        projectsList.clear()
        projectsList.addAll(UnifiedProjectManager.getAllProjects(currentContext))

        if (projectsList.isEmpty()) {
            binding.tvEmptyState.visibility = View.VISIBLE
            binding.rvProjects.visibility = View.GONE
        } else {
            binding.tvEmptyState.visibility = View.GONE
            binding.rvProjects.visibility = View.VISIBLE
        }
        adapter.notifyDataSetChanged()
    }

    private fun showRenameDialog(project: UnifiedProjectModel) {
        val currentContext = context ?: return
        val builder = AlertDialog.Builder(currentContext)
        builder.setTitle(getString(R.string.dialog_rename_title))

        val input = EditText(currentContext)
        input.setText(project.name)
        input.setSelection(project.name.length)
        builder.setView(input)

        builder.setPositiveButton(getString(R.string.menu_rename)) { dialog, _ ->
            val newName = input.text.toString().trim()
            if (newName.isNotEmpty()) {
                project.name = newName
                UnifiedProjectManager.saveProject(currentContext, project)
                loadProjects()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton(getString(R.string.btn_later)) { dialog, _ ->
            dialog.cancel()
        }
        builder.show()
    }

    private fun showDeleteDialog(project: UnifiedProjectModel) {
        val currentContext = context ?: return
        AlertDialog.Builder(currentContext)
            .setTitle(getString(R.string.dialog_delete_title))
            .setMessage(getString(R.string.dialog_delete_message))
            .setPositiveButton(getString(R.string.menu_delete)) { dialog, _ ->
                UnifiedProjectManager.deleteProject(currentContext, project.id)
                loadProjects()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.btn_later)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private inner class ProjectsAdapter : RecyclerView.Adapter<ProjectsAdapter.ProjectViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProjectViewHolder {
            val binding = ItemProjectBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ProjectViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ProjectViewHolder, position: Int) {
            val project = projectsList[position]
            holder.bind(project)
        }

        override fun getItemCount(): Int = projectsList.size

        inner class ProjectViewHolder(private val itemBinding: ItemProjectBinding) : RecyclerView.ViewHolder(itemBinding.root) {

            fun bind(project: UnifiedProjectModel) {
                itemBinding.tvProjectName.text = project.name
                
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                itemBinding.tvProjectDate.text = sdf.format(Date(project.updatedAt))
                
                // Show video path last segment as details
                val uriStr = project.videoPath
                val lastSeg = android.net.Uri.parse(uriStr).lastPathSegment ?: uriStr
                itemBinding.tvProjectDetails.text = lastSeg
                itemBinding.ivThumbnail.setImageBitmap(null)

                val projectId = project.id
                itemBinding.root.tag = projectId

                // Try fetching duration and thumbnail asynchronously
                val ctx = itemBinding.root.context
                var retriever: android.media.MediaMetadataRetriever? = null
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        retriever = android.media.MediaMetadataRetriever()
                        retriever.setDataSource(ctx, android.net.Uri.parse(project.videoPath))
                        val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                        val durationMs = durationStr?.toLongOrNull() ?: 0L
                        val seconds = (durationMs / 1000) % 60
                        val minutes = (durationMs / (1000 * 60)) % 60
                        val durFormatted = String.format("%02d:%02d", minutes, seconds)
                        
                        val frame = retriever.frameAtTime
                        
                        withContext(Dispatchers.Main) {
                            if (itemBinding.root.tag == projectId) {
                                itemBinding.tvProjectDetails.text = "$lastSeg • $durFormatted"
                                if (frame != null) {
                                    itemBinding.ivThumbnail.setImageBitmap(frame)
                                }
                            } else {
                                frame?.recycle()
                            }
                        }
                    } catch (_: Exception) {
                    } finally {
                        try {
                            retriever?.release()
                        } catch (_: Exception) {}
                    }
                }

                itemBinding.root.setOnClickListener {
                    val bundle = Bundle().apply {
                        putString("projectId", project.id)
                    }
                    try {
                        findNavController().navigate(R.id.action_projectsDashboardFragment_to_unifiedWorkspaceFragment, bundle)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                itemBinding.btnProjectMenu.setOnClickListener { view ->
                    val popup = PopupMenu(view.context, view)
                    popup.menu.add(0, 1, 0, getString(R.string.menu_rename))
                    popup.menu.add(0, 2, 0, getString(R.string.menu_duplicate))
                    popup.menu.add(0, 3, 0, getString(R.string.menu_delete))
                    
                    popup.setOnMenuItemClickListener { menuItem ->
                        when (menuItem.itemId) {
                            1 -> {
                                showRenameDialog(project)
                                true
                            }
                            2 -> {
                                UnifiedProjectManager.duplicateProject(view.context, project)
                                loadProjects()
                                true
                            }
                            3 -> {
                                showDeleteDialog(project)
                                true
                            }
                            else -> false
                        }
                    }
                    popup.show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
