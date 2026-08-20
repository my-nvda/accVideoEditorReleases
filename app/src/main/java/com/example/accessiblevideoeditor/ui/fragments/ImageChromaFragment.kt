package com.example.accessiblevideoeditor.ui.fragments

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.accessiblevideoeditor.R
import com.example.accessiblevideoeditor.databinding.FragmentImageChromaBinding
import com.example.accessiblevideoeditor.ui.AppStrings
import com.example.accessiblevideoeditor.ui.ProcessingManager
import com.example.accessiblevideoeditor.utils.FileUtils
import com.example.accessiblevideoeditor.media.MediaUtils
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CancellationException

class ImageChromaFragment : Fragment() {

    private var _binding: FragmentImageChromaBinding? = null
    private val binding get() = _binding!!

    private var srcImageUri: Uri? = null
    private var customBgUri: Uri? = null

    private val bgModes = arrayOf("green_screen", "blue_screen", "transparent", "custom_bg")

    private val selectSrcImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            srcImageUri = uri
            binding.ivSrcPreview.setImageURI(uri)
            binding.ivSrcPreview.visibility = View.VISIBLE
            updateApplyButtonState()
        }
    }

    private val selectBgImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            customBgUri = uri
            val currentContext = context ?: return@registerForActivityResult
            val fileName = uri.lastPathSegment ?: "background_image.png"
            binding.tvBgPathText.text = "${getString(R.string.label_selected_bg)}: $fileName"
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImageChromaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnSelectSrcImage.setOnClickListener {
            selectSrcImageLauncher.launch("image/*")
        }

        binding.btnSelectBgImage.setOnClickListener {
            selectBgImageLauncher.launch("image/*")
        }

        val typeLabels = arrayOf(
            getString(R.string.bg_removal_green),
            getString(R.string.bg_removal_blue),
            getString(R.string.bg_removal_transparent),
            getString(R.string.bg_removal_custom)
        )

        binding.spBgMode.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, typeLabels)

        binding.spBgMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (bgModes[position] == "custom_bg") {
                    binding.llCustomBgSection.visibility = View.VISIBLE
                } else {
                    binding.llCustomBgSection.visibility = View.GONE
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.btnApplyBgRemoval.setOnClickListener {
            if (srcImageUri == null) {
                Toast.makeText(requireContext(), getString(R.string.string_76), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val selectedMode = bgModes[binding.spBgMode.selectedItemPosition]
            if (selectedMode == "custom_bg" && customBgUri == null) {
                Toast.makeText(requireContext(), getString(R.string.label_no_bg_selected), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            processImage()
        }

        updateApplyButtonState()
    }

    private fun updateApplyButtonState() {
        binding.btnApplyBgRemoval.isEnabled = srcImageUri != null
    }

    private fun processImage() {
        val selectedMode = bgModes[binding.spBgMode.selectedItemPosition]
        val currentContext = requireContext().applicationContext

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            var success = false
            var segmenter: com.google.mlkit.vision.segmentation.Segmenter? = null
            var sourceBitmap: Bitmap? = null
            var customBgBitmap: Bitmap? = null

            try {
                withContext(Dispatchers.Main) {
                    ProcessingManager.startProcessing(getString(R.string.string_28)) // Processing...
                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnApplyBgRemoval.isEnabled = false
                }

                val srcUri = srcImageUri ?: return@launch
                val sourcePath = MediaUtils.copyUriToTempFile(currentContext, srcUri, "src_image_${System.currentTimeMillis()}.png")?.absolutePath
                    ?: return@launch

                sourceBitmap = BitmapFactory.decodeFile(sourcePath) ?: return@launch
                val mutableBitmap = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)

                val width = mutableBitmap.width
                val height = mutableBitmap.height

                // Set up custom background if requested
                var scaledBgBitmap: Bitmap? = null
                var bgPixels: IntArray? = null
                if (selectedMode == "custom_bg" && customBgUri != null) {
                    val bgPath = MediaUtils.copyUriToTempFile(currentContext, customBgUri!!, "bg_image_${System.currentTimeMillis()}.png")?.absolutePath
                    if (bgPath != null) {
                        customBgBitmap = BitmapFactory.decodeFile(bgPath)
                        if (customBgBitmap != null) {
                            scaledBgBitmap = Bitmap.createScaledBitmap(customBgBitmap, width, height, true)
                            bgPixels = IntArray(width * height)
                            scaledBgBitmap.getPixels(bgPixels, 0, width, 0, 0, width, height)
                        }
                    }
                }

                // Run Segmentation
                val segmenterOptions = SelfieSegmenterOptions.Builder()
                    .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                    .enableRawSizeMask()
                    .build()
                segmenter = Segmentation.getClient(segmenterOptions)

                val inputImage = InputImage.fromBitmap(mutableBitmap, 0)
                val task = segmenter.process(inputImage)
                val mask = Tasks.await(task) as com.google.mlkit.vision.segmentation.SegmentationMask

                val pixels = IntArray(width * height)
                mutableBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

                val maskBuffer = mask.buffer
                maskBuffer.rewind()

                val greenColor = 0xFF00FF00.toInt()
                val blueColor = 0xFF0000FF.toInt()

                for (i in pixels.indices) {
                    val confidence = maskBuffer.float
                    if (confidence <= 0.5f) {
                        when (selectedMode) {
                            "transparent" -> pixels[i] = 0x00000000
                            "blue_screen" -> pixels[i] = blueColor
                            "custom_bg" -> {
                                if (bgPixels != null) {
                                    pixels[i] = bgPixels[i]
                                } else {
                                    pixels[i] = greenColor
                                }
                            }
                            else -> pixels[i] = greenColor // green_screen
                        }
                    }
                }

                mutableBitmap.setPixels(pixels, 0, width, 0, 0, width, height)

                val outputExt = if (selectedMode == "transparent") "png" else "jpg"
                val mimeType = if (selectedMode == "transparent") "image/png" else "image/jpeg"
                val compressFormat = if (selectedMode == "transparent") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG

                val outputPath = currentContext.cacheDir.absolutePath + "/bg_removed_${System.currentTimeMillis()}.$outputExt"
                FileOutputStream(outputPath).use { out ->
                    mutableBitmap.compress(compressFormat, 100, out)
                    out.flush()
                }

                val savedUri = FileUtils.saveToGallery(currentContext, File(outputPath), mimeType)
                if (savedUri != null) success = true

                // Clean up temp files
                try { File(sourcePath).delete() } catch (_: Exception) {}
                try { File(outputPath).delete() } catch (_: Exception) {}

                mutableBitmap.recycle()
                scaledBgBitmap?.recycle()

                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(currentContext, AppStrings.get(currentContext, R.string.string_182), Toast.LENGTH_LONG).show() // Saved successfully
                    } else {
                        Toast.makeText(currentContext, AppStrings.get(currentContext, R.string.string_183), Toast.LENGTH_LONG).show() // Failed to save
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(currentContext, getString(R.string.string_73, e.message ?: ""), Toast.LENGTH_SHORT).show()
                }
            } finally {
                segmenter?.close()
                sourceBitmap?.recycle()
                customBgBitmap?.recycle()
                withContext(NonCancellable) {
                    withContext(Dispatchers.Main) {
                        ProcessingManager.stopProcessing()
                        _binding?.let {
                            it.progressBar.visibility = View.GONE
                            it.btnApplyBgRemoval.isEnabled = true
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
