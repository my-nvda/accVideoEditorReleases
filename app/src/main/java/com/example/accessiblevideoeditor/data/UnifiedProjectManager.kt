package com.example.accessiblevideoeditor.data

import android.content.Context
import java.io.File
import java.util.UUID

object UnifiedProjectManager {

    private fun getProjectsDir(context: Context): File {
        val dir = File(context.filesDir, "projects")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    @Synchronized
    fun saveProject(context: Context, project: UnifiedProjectModel) {
        try {
            project.updatedAt = System.currentTimeMillis()
            val dir = getProjectsDir(context)
            val file = File(dir, "${project.id}.json")
            file.writeText(project.toJsonObject().toString(), Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun getProject(context: Context, projectId: String): UnifiedProjectModel? {
        return try {
            val dir = getProjectsDir(context)
            val file = File(dir, "$projectId.json")
            if (file.exists()) {
                val jsonStr = file.readText(Charsets.UTF_8)
                UnifiedProjectModel.fromJsonString(jsonStr)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    @Synchronized
    fun getAllProjects(context: Context): List<UnifiedProjectModel> {
        val list = mutableListOf<UnifiedProjectModel>()
        try {
            val dir = getProjectsDir(context)
            val files = dir.listFiles { _, name -> name.endsWith(".json") }
            if (files != null) {
                for (file in files) {
                    try {
                        val jsonStr = file.readText(Charsets.UTF_8)
                        list.add(UnifiedProjectModel.fromJsonString(jsonStr))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedByDescending { it.updatedAt }
    }

    @Synchronized
    fun deleteProject(context: Context, projectId: String): Boolean {
        return try {
            val dir = getProjectsDir(context)
            val file = File(dir, "$projectId.json")
            if (file.exists()) {
                file.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @Synchronized
    fun duplicateProject(context: Context, project: UnifiedProjectModel): UnifiedProjectModel? {
        return try {
            val copyName = "${project.name} (Copy)"
            val duplicate = project.copy(
                id = UUID.randomUUID().toString(),
                name = copyName,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                textOverlays = project.textOverlays.map { it.copy(id = UUID.randomUUID().toString()) }.toMutableList()
            )
            saveProject(context, duplicate)
            duplicate
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
