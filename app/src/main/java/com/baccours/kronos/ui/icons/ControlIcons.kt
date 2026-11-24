package com.baccours.kronos.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector

@Suppress("UnusedReceiverParameter")
val Icons.Add: ImageVector get() = addIcon
private val addIcon by lazy {
    svgIcon("Add", "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z")
}

@Suppress("UnusedReceiverParameter")
val Icons.Remove: ImageVector get() = removeIcon
private val removeIcon by lazy {
    svgIcon("Remove", "M19,13H5v-2h14v2z")
}

@Suppress("UnusedReceiverParameter")
val Icons.Play: ImageVector get() = playIcon
private val playIcon by lazy {
    svgIcon("Play", "M8,6.82v10.36c0,0.79 0.87,1.27 1.54,0.84l8.14,-5.18c0.62,-0.39 0.62,-1.29 0,-1.69L9.54,5.98C8.87,5.55 8,6.03 8,6.82z")
}

@Suppress("UnusedReceiverParameter")
val Icons.Pause: ImageVector get() = pauseIcon
private val pauseIcon by lazy {
    svgIcon("Pause", "M8,19c1.1,0 2,-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2s-2,0.9 -2,2v10c0,1.1 0.9,2 2,2zM14,7v10c0,1.1 0.9,2 2,2s2,-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2s-2,0.9 -2,2z")
}

@Suppress("UnusedReceiverParameter")
val Icons.SkipNext: ImageVector get() = skipNextIcon
private val skipNextIcon by lazy {
    svgIcon("SkipNext", "M6,18l8.5,-6L6,6v12zM16,6v12h2V6h-2z")
}

@Suppress("UnusedReceiverParameter")
val Icons.Done: ImageVector get() = doneIcon
private val doneIcon by lazy {
    svgIcon("Done", "M9,16.2L5.5,12.7c-0.39,-0.39 -1.01,-0.39 -1.4,0c-0.39,0.39 -0.39,1.01 0,1.4l4.19,4.19c0.39,0.39 1.02,0.39 1.41,0L20.3,7.7c0.39,-0.39 0.39,-1.01 0,-1.4c-0.39,-0.39 -1.01,-0.39 -1.4,0L9,16.2z")
}

@Suppress("UnusedReceiverParameter")
val Icons.Close: ImageVector get() = closeIcon
private val closeIcon by lazy {
    svgIcon("Close", "M18.3,5.71c-0.39,-0.39 -1.02,-0.39 -1.41,0L12,10.59L7.11,5.7c-0.39,-0.39 -1.02,-0.39 -1.41,0c-0.39,0.39 -0.39,1.02 0,1.41L10.59,12L5.7,16.89c-0.39,0.39 -0.39,1.02 0,1.41c0.39,0.39 1.02,0.39 1.41,0L12,13.41l4.89,4.89c0.39,0.39 1.02,0.39 1.41,0c0.39,-0.39 0.39,-1.02 0,-1.41L13.41,12l4.89,-4.89c0.38,-0.38 0.38,-1.02 0,-1.4z")
}

@Suppress("UnusedReceiverParameter")
val Icons.Delete: ImageVector get() = deleteIcon
private val deleteIcon by lazy {
    svgIcon("Delete", "M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM8.46,11.88l1.41,-1.41L12,12.59l2.12,-2.12l1.41,1.41L13.41,14l2.12,2.12l-1.41,1.41L12,15.41l-2.12,2.12l-1.41,-1.41L10.59,14l-2.13,-2.12zM15.5,4l-1,-1h-5l-1,1H5v2h14V4h-3.5z")
}
