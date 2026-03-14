package com.purecomet.saveinplaceeditor

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import coil.compose.AsyncImage
import coil.imageLoader
import coil.memory.MemoryCache
import com.purecomet.saveinplaceeditor.ui.theme.SaveInPlaceEditorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(coil.annotation.ExperimentalCoilApi::class)
private fun invalidateCoilCache(context: Context, id: Long) {
    val uri = MediaStoreEditor.uriForId(id)
    val key = uri.toString()
    context.imageLoader.memoryCache?.remove(MemoryCache.Key(key))
    context.imageLoader.diskCache?.remove(key)
}

class MainActivity : ComponentActivity() {

    private val pendingMediaId = mutableStateOf<Long?>(null)
    private val pendingUsePending = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            SaveInPlaceEditorTheme {
                MainScreen(
                    pendingMediaId = pendingMediaId.value,
                    usePending = pendingUsePending.value,
                    onIdHandled = { pendingMediaId.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val id = intent?.getLongExtra("media_store_id", -1L)?.takeIf { it >= 0 } ?: return
        val skipPending = intent.getBooleanExtra("skip_pending", false)
        pendingUsePending.value = !skipPending
        pendingMediaId.value = id
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(pendingMediaId: Long?, usePending: Boolean = true, onIdHandled: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var images by remember { mutableStateOf<List<MediaImage>>(emptyList()) }
    var isRefreshing by remember { mutableStateOf(false) }
    var permissionGranted by remember { mutableStateOf(false) }
    var canManageMedia by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var pendingWriteId by remember { mutableStateOf<Long?>(null) }
    var selectedImageId by remember { mutableStateOf<Long?>(null) }

    val readPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
    }

    // Launched after user approves write request dialog — retries the overwrite
    val writeRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val id = pendingWriteId ?: return@rememberLauncherForActivityResult
        pendingWriteId = null
        if (result.resultCode == Activity.RESULT_OK) {
            scope.launch {
                statusMessage = "Writing ID=$id…"
                val writeResult = withContext(Dispatchers.IO) {
                    MediaStoreEditor.overwrite(context, id, TestCardGenerator.generate(), usePending)
                }
                writeResult.fold(
                    onSuccess = {
                        val now = java.time.LocalDateTime.now()
                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        statusMessage = "Written: ID=$id at $now"
                        invalidateCoilCache(context, id)
                        images = withContext(Dispatchers.IO) { MediaStoreRepository.queryImages(context) }
                    },
                    onFailure = { e -> statusMessage = "Error: ${e.message}" }
                )
            }
        } else {
            statusMessage = "Write permission denied for ID=$id"
        }
    }

    fun refreshManageMedia() {
        canManageMedia = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaStore.canManageMedia(context)
        } else {
            true
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(readPermission)
        refreshManageMedia()
    }

    LaunchedEffect(permissionGranted) {
        if (permissionGranted) {
            images = withContext(Dispatchers.IO) { MediaStoreRepository.queryImages(context) }
        }
    }

    // Handle ADB-triggered overwrite
    LaunchedEffect(pendingMediaId) {
        val id = pendingMediaId ?: return@LaunchedEffect
        // NOTE: onIdHandled() must NOT be called before the IO work — changing pendingMediaId
        // here would change the LaunchedEffect key, cancelling this coroutine mid-flight.
        Log.d("MainActivity", "pendingMediaId=$id, canManageMedia=$canManageMedia")
        statusMessage = "Writing ID=$id…"
        val result = withContext(Dispatchers.IO) {
            MediaStoreEditor.overwrite(context, id, TestCardGenerator.generate(), usePending)
        }
        onIdHandled()
        Log.d("MainActivity", "overwrite result: $result")
        result.fold(
            onSuccess = {
                val now = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                statusMessage = "Written: ID=$id at $now"
                invalidateCoilCache(context, id)
                images = withContext(Dispatchers.IO) { MediaStoreRepository.queryImages(context) }
            },
            onFailure = { e ->
                Log.e("MainActivity", "overwrite failed: ${e.javaClass.name}: ${e.message}", e)
                val cause = if (e is RecoverableSecurityException) e else e.cause
                Log.d("MainActivity", "cause: ${cause?.javaClass?.name}")
                if (cause is RecoverableSecurityException) {
                    statusMessage = "Requesting write access for ID=$id…"
                    val uri = MediaStoreEditor.uriForId(id)
                    val req = MediaStore.createWriteRequest(context.contentResolver, listOf(uri))
                    pendingWriteId = id
                    Log.d("MainActivity", "launching createWriteRequest")
                    writeRequestLauncher.launch(
                        IntentSenderRequest.Builder(req.intentSender).build()
                    )
                } else {
                    statusMessage = "Error: ${e.message}"
                }
            }
        )
    }

    val detailIndex = selectedImageId?.let { id -> images.indexOfFirst { it.id == id }.takeIf { it >= 0 } }

    if (detailIndex != null) {
        BackHandler { selectedImageId = null }
        ImageDetailScreen(images = images, initialIndex = detailIndex, onBack = { selectedImageId = null })
    } else {

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Save-In-Place Editor") })
        },
        bottomBar = {
            if (statusMessage.isNotEmpty()) {
                Text(
                    text = "Status: $statusMessage",
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

            // MANAGE_MEDIA banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (canManageMedia) Color(0xFF2E7D32) else MaterialTheme.colorScheme.errorContainer
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (canManageMedia) {
                    Text(
                        text = "MANAGE_MEDIA permission granted",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                } else {
                    Text(
                        text = "Grant MANAGE_MEDIA for silent overwrites",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA,
                                Uri.parse("package:${context.packageName}"))
                        } else {
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}"))
                        }
                        context.startActivity(intent)
                    }) {
                        Text("Grant")
                    }
                }
            }

            if (!permissionGranted) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Storage permission required")
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { permissionLauncher.launch(readPermission) }) {
                            Text("Grant Permission")
                        }
                    }
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        scope.launch {
                            isRefreshing = true
                            try {
                                images = withContext(Dispatchers.IO) { MediaStoreRepository.queryImages(context) }
                            } finally {
                                isRefreshing = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(images, key = { _, image -> image.id }) { _, image ->
                            ImageGridCell(image) { selectedImageId = image.id }
                        }
                    }
                }
            }
        }
    }
    } // end else (detail not shown)
}

@Composable
fun ImageDetailScreen(images: List<MediaImage>, initialIndex: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(initialPage = initialIndex) { images.size }
    val currentImage = images[pagerState.currentPage]

    // Live generation value — starts from cached, re-queries fresh on each page change
    var liveGeneration by remember(currentImage.id) { mutableStateOf(currentImage.generationModified) }
    LaunchedEffect(currentImage.id) {
        liveGeneration = MediaStoreRepository.queryGenerationModified(context, currentImage.id)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            AsyncImage(
                model = images[page].uri,
                contentDescription = images[page].displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(8.dp)
                .background(Color(0xAA000000), shape = CircleShape)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }

        Text(
            text = "generation_modified: $liveGeneration",
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(16.dp)
                .background(Color(0xAA000000))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun ImageGridCell(image: MediaImage, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = image.uri,
            contentDescription = image.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Text(
            text = "${image.id}",
            color = Color.White,
            fontSize = 10.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(2.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                background = Color(0x88000000)
            )
        )
    }
}
