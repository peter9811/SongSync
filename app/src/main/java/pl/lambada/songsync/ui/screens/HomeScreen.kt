package pl.lambada.songsync.ui.screens

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Parcelable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat.getString
import androidx.documentfile.provider.DocumentFile
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import pl.lambada.songsync.MainActivity
import pl.lambada.songsync.R
import pl.lambada.songsync.data.EmptyQueryException
import pl.lambada.songsync.data.MainViewModel
import pl.lambada.songsync.data.NoTrackFoundException
import pl.lambada.songsync.data.dto.Song
import pl.lambada.songsync.data.dto.SongInfo
import pl.lambada.songsync.data.ext.lowercaseWithLocale
import pl.lambada.songsync.data.ext.toLrcFile
import pl.lambada.songsync.ui.Screens
import pl.lambada.songsync.ui.components.MarqueeText
import java.io.File
import java.io.FileNotFoundException
import kotlin.math.roundToInt

/**
 * Composable function representing the home screen.
 *
 * @param viewModel The [MainViewModel] instance.
 */
@Composable
fun HomeScreen(selected: SnapshotStateList<String>, allSongs: List<Song>?,
               navController: NavHostController, viewModel: MainViewModel) {
    if (allSongs == null) {
        LoadingScreen()
    } else {
        HomeScreenLoaded(
            navController = navController,
            viewModel = viewModel,
            songs = allSongs,
            selected = selected
        )
    }
}

/**
 * Composable function representing the loading screen.
 */
@Composable
fun LoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
    }
}

@Parcelize
data class MyTextFieldValue(val text: String, val cursorStart: Int, val cursorEnd: Int) : Parcelable

@OptIn(
    ExperimentalLayoutApi::class
)
@Composable
fun HomeScreenLoaded(selected: SnapshotStateList<String>, navController: NavHostController,
                     viewModel: MainViewModel, songs: List<Song>) {
    var showingSearch by rememberSaveable { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(showingSearch) }
    var query by rememberSaveable(stateSaver = Saver(
        save = { MyTextFieldValue(it.text, it.selection.start, it.selection.end) },
        restore = { TextFieldValue(it.text, TextRange(it.cursorStart, it.cursorEnd)) })
    ) { mutableStateOf(TextFieldValue()) }
    var isBatchDownload by rememberSaveable { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var filtered by remember { mutableStateOf<List<Song>?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val displaySongs = filtered ?: songs

    LaunchedEffect(Unit) {
        filtered = viewModel.filterSongs()
    }

    Column {
        if (isBatchDownload) {
            BatchDownloadLyrics(
                songs = if (selected.isEmpty()) displaySongs
                        else songs.filter { selected.contains(it.filePath) }.toList(),
                viewModel = viewModel,
                onDone = { isBatchDownload = false }
            )
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(
                    modifier = Modifier
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        )
                        .padding(vertical = 5.dp),
                ) {
                    val focusRequester = remember { FocusRequester() }
                    var willShowIme by remember { mutableStateOf(false) }
                    val showingIme = WindowInsets.isImeVisible
                    if (!showingSearch && showSearch) {
                        showingSearch = true
                    }
                    if (!showSearch && !willShowIme && showingSearch && !WindowInsets.isImeVisible && query.text.isEmpty()) {
                        // If search already is no longer "to be shown" but "currently showing", query is
                        // empty and user hides soft-keyboard, we close search bar
                        showingSearch = false
                    }
                    AnimatedContent(
                        targetState = showingSearch,
                        transitionSpec = {
                            if (targetState) {
                                (slideInVertically { height -> height } + fadeIn()).togetherWith(
                                    slideOutVertically { height -> -height } + fadeOut())
                            } else {
                                (slideInVertically { height -> -height } + fadeIn()).togetherWith(
                                    slideOutVertically { height -> height } + fadeOut())
                            }.using(
                                SizeTransform()
                            )
                        },
                        label = "",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(55.dp)
                    ) { showing ->
                        if (showing) {
                            if (willShowIme && WindowInsets.isImeVisible) {
                                willShowIme = false
                            }
                            val focusManager = LocalFocusManager.current
                            TextField(
                                value = query,
                                onValueChange = { query = it },
                                label = { Text(stringResource(id = R.string.search)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Search,
                                        contentDescription = stringResource(id = R.string.search),
                                        modifier = Modifier.clickable {
                                            showSearch = false
                                            showingSearch = false
                                        }
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = stringResource(id = R.string.clear),
                                        modifier = Modifier.clickable {
                                            query = TextFieldValue("")
                                            showSearch = false
                                            showingSearch = false
                                        }
                                    )
                                },
                                shape = ShapeDefaults.ExtraLarge,
                                colors = TextFieldDefaults.colors(
                                    focusedLabelColor = MaterialTheme.colorScheme.onSurface,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                                    .onFocusChanged {
                                        if (it.isFocused && !showingIme) {
                                            willShowIme = true
                                        }
                                    }
                                    .onGloballyPositioned {
                                        if (showSearch && !showingIme) {
                                            focusRequester.requestFocus()
                                            showSearch = false
                                        }
                                    },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    focusManager.clearFocus()
                                })
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center) {
                                Button(onClick = { isBatchDownload = true }) {
                                    Text(text = stringResource(R.string.batch_download_lyrics))
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(onClick = {
                                    showFilters = true
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.FilterAlt,
                                        contentDescription = stringResource(R.string.search),
                                    )
                                }

                                if (showFilters)
                                    FiltersDialog(
                                        viewModel = viewModel,
                                        context = context,
                                        onDismiss = { showFilters = false },
                                        onFilterChange = {
                                            scope.launch(Dispatchers.Default) {
                                                filtered = viewModel.filterSongs()
                                            }
                                        }
                                    )
                                
                                IconButton(onClick = { showSearch = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = stringResource(R.string.search),
                                    )
                                }
                            }
                        }
                    }
                }
                Divider()
            }

            items(displaySongs.size) { i ->
                val song = displaySongs[i]
                val songTitleLowercase = song.title?.lowercaseWithLocale()
                val songArtistLowercase = song.artist?.lowercaseWithLocale()
                val queryLowercase = query.text.lowercaseWithLocale()

                if (
                    songTitleLowercase?.contains(queryLowercase) == true ||
                    songArtistLowercase?.contains(queryLowercase) == true
                ) {
                    SongItem(
                        selected = selected.contains(song.filePath),
                        quickSelect = selected.size > 0,
                        onSelectionChanged = { newValue ->
                            if (newValue) {
                                song.filePath?.let { selected.add(it) }
                                showSearch = false
                                showingSearch = false
                            } else {
                                selected.remove(song.filePath)
                                if (selected.size == 0 && query.text.isNotEmpty())
                                    showingSearch = true // show again but don't focus
                            }
                        },
                        navController = navController,
                        song = song,
                        viewModel = viewModel
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiltersDialog(viewModel: MainViewModel, context: Context, onDismiss: () -> Unit, onFilterChange: () -> Unit) {
    var hideLyrics by remember { mutableStateOf(viewModel.hideLyrics) }
    val folders = viewModel.getSongFolders(context)
    var showFolders by rememberSaveable { mutableStateOf(false) }

    val sharedPreferences = LocalContext.current.getSharedPreferences(
        "pl.lambada.songsync_preferences",
        Context.MODE_PRIVATE
    )

    AlertDialog(onDismissRequest = { onDismiss() }) {
        Surface(
            modifier = Modifier
                .wrapContentWidth()
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.large,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = stringResource(R.string.filters), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(modifier = Modifier.weight(0.8f)) {
                        Text(stringResource(R.string.no_lyrics_only))
                    }
                    Switch(
                        checked = hideLyrics,
                        onCheckedChange = {
                            hideLyrics = it
                            viewModel.hideLyrics = it
                            sharedPreferences.edit().putBoolean("hide_lyrics", it).apply()
                            onFilterChange()
                        })
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(modifier = Modifier.weight(0.8f)) {
                        Text(stringResource(R.string.ignore_folders))
                    }
                    IconButton(onClick = {
                        showFolders = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row {
                    Spacer(modifier = Modifier.weight(1f))
                    Button(onClick = { onDismiss() } ) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
    }

    if(showFolders) {
        AlertDialog(onDismissRequest = { showFolders = false }) {
            Surface(
                modifier = Modifier
                    .wrapContentWidth()
                    .wrapContentHeight(),
                shape = MaterialTheme.shapes.large,
                tonalElevation = AlertDialogDefaults.TonalElevation
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = stringResource(R.string.ignore_folders), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(24.dp))
                    LazyColumn {
                        items(folders.size) {
                            val folder = folders[it]
                            var checked by remember { mutableStateOf(viewModel.blacklistedFolders.contains(folder)) }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(modifier = Modifier.weight(0.8f)) {
                                    Text(folder)
                                }
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { check ->
                                        if(check) {
                                            checked = true
                                            viewModel.blacklistedFolders.add(folder)
                                        }
                                        else {
                                            checked = false
                                            viewModel.blacklistedFolders.remove(folder)
                                        }
                                        sharedPreferences.edit().putString("blacklist", viewModel.blacklistedFolders.joinToString(",")).apply()
                                        onFilterChange()
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        item {
                            Row {
                                Spacer(modifier = Modifier.weight(1f))

                                Button(onClick = { showFolders = false } ) {
                                    Text(stringResource(R.string.close))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongItem(selected: Boolean, quickSelect: Boolean, onSelectionChanged: (Boolean) -> Unit,
             navController: NavHostController, song: Song, viewModel: MainViewModel) {
    OutlinedCard(
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .combinedClickable(onClick = {
                if (quickSelect) {
                    onSelectionChanged(!selected)
                } else {
                    viewModel.nextSong = song
                    navController.navigate(Screens.Browse.name)
                }
            }, onLongClick = {
                onSelectionChanged(!selected)
            })
    ) {
        val painter = rememberAsyncImagePainter(
            ImageRequest.Builder(LocalContext.current).data(data = song.imgUri)
                .apply {
                    placeholder(R.drawable.ic_song)
                    error(R.drawable.ic_song)
                }.build(),
            imageLoader = LocalContext.current.imageLoader
        )
        val bgColor = if (selected) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.surface
        Row(modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(bgColor)) {
            Image(
                painter = painter,
                contentDescription = stringResource(id = R.string.album_cover),
                modifier = Modifier
                    .height(72.dp)
                    .aspectRatio(1f)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.Top) {
                MarqueeText(text = song.title ?: stringResource(id = R.string.unknown), fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.contentColorFor(bgColor))
                Spacer(modifier = Modifier.weight(1f))
                MarqueeText(text = song.artist ?: stringResource(id = R.string.unknown), fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.contentColorFor(bgColor))
            }
        }
    }
}

@SuppressLint("StringFormatMatches")
@Composable
fun BatchDownloadLyrics(songs: List<Song>, viewModel: MainViewModel, onDone: () -> Unit) {
    val unknownString = stringResource(id = R.string.unknown)
    val generatedUsingString = stringResource(id = R.string.generated_using)

    var uiState by rememberSaveable { mutableStateOf(UiState.Warning) }
    var failedCount by rememberSaveable { mutableIntStateOf(0) }
    var noLyricsCount by rememberSaveable { mutableIntStateOf(0) }
    var successCount by rememberSaveable { mutableIntStateOf(0) }
    val count = successCount + failedCount + noLyricsCount
    val total = songs.size
    val isLegacyVersion = Build.VERSION.SDK_INT < Build.VERSION_CODES.R

    val context = LocalContext.current
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channelID = getString(context, R.string.batch_download_lyrics)

    val resultIntent = Intent(context, MainActivity::class.java)
    resultIntent.addCategory(Intent.CATEGORY_LAUNCHER)
    resultIntent.setAction(Intent.ACTION_MAIN)
    resultIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val pendingIntent = PendingIntent.getActivity(context, 0, resultIntent, PendingIntent.FLAG_IMMUTABLE)

    when (uiState) {
        UiState.Cancelled -> {
            notificationManager.cancelAll()
            onDone()
        }

        UiState.Warning -> {
            AlertDialog(
                title = {
                    Text(text = stringResource(id = R.string.batch_download_lyrics))
                },
                text = {
                    Column {
                        Text(text = pluralStringResource(R.plurals.this_will_download_lyrics_for_all_songs, songs.size, songs.size))
                    }
                },
                onDismissRequest = {
                    uiState = UiState.Cancelled
                },
                confirmButton = {
                    Button(
                        onClick = {
                            uiState = if (isLegacyVersion) {
                                UiState.LegacyPrompt
                            } else {
                                UiState.Pending
                            }
                        }
                    ) {
                        Text(text = stringResource(R.string.yes))
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { uiState = UiState.Cancelled }) {
                        Text(text = stringResource(R.string.no))
                    }
                }
            )
        }

        UiState.LegacyPrompt -> {
            AlertDialog(
                title = {
                    Text(text = stringResource(id = R.string.batch_download_lyrics))
                },
                text = {
                    Column {
                        Text(text = stringResource(R.string.set_sd_path_warn))
                    }
                },
                onDismissRequest = {
                    uiState = UiState.Cancelled
                },
                confirmButton = {
                    Button(
                        onClick = {
                            uiState = UiState.Pending
                        }
                    ) {
                        Text(text = stringResource(R.string.ok))
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            uiState = UiState.Cancelled
                        }
                    ) {
                        Text(text = stringResource(R.string.cancel))
                    }
                }
            )
        }

        UiState.Pending -> {
            val percentage = if (total != 0) {
                (count.toFloat() / total.toFloat() * 100).roundToInt()
            } else {
                0 // In other cases = 0
            }

            val notificationBuilder = NotificationCompat.Builder(context, channelID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.downloading_lyrics))
                .setContentText(context.getString(R.string.progress, count, total, percentage))
                .setProgress(100, percentage, false)
                .setTimeoutAfter(2000)
                .setContentIntent(pendingIntent)

            notificationManager.notify(1, notificationBuilder.build())

            AlertDialog(
                title = {
                    Text(text = stringResource(id = R.string.batch_download_lyrics))
                },
                text = {
                    Column {
                        Text(text = stringResource(R.string.downloading_lyrics))
                        MarqueeText(
                            stringResource(
                                R.string.song,
                                songs.getOrNull((count) % total.coerceAtLeast(1))?.title ?: unknownString,
                            )
                        )
                        Text(text = stringResource(R.string.progress, count, total, percentage))
                        Text(text = stringResource(R.string.success_failed, successCount, noLyricsCount, failedCount))
                        Text(text = stringResource(R.string.please_do_not_close_the_app_this_may_take_a_while))
                    }
                },
                onDismissRequest = {
                    /*
                       it's easy to accidentally dismiss the dialog, and since it's a long running task
                       we don't want to accidentally cancel it, so we don't allow dismissing the dialog
                       user can cancel the task by pressing the cancel button
                     */
                },
                confirmButton = {
                    // no button but compose cries when I don't use confirmButton
                },
                dismissButton = {
                    OutlinedButton(onClick = { uiState = UiState.Cancelled }) {
                        Text(text = stringResource(R.string.cancel))
                    }
                }
            )

            var notFoundInARow by rememberSaveable { mutableIntStateOf(0) }
            var downloadJob by remember { mutableStateOf<Job?>(null) }

            LaunchedEffect(Unit) {
                downloadJob = launch(Dispatchers.IO) {
                    for (i in count until songs.size) {
                        val song = songs[i]
                        if (uiState == UiState.Cancelled) {
                            downloadJob?.cancel()
                            return@launch
                        }

                        if (count >= total) {
                            uiState = UiState.Done
                            downloadJob?.cancel()
                            return@launch
                        }
                        val file = song.filePath.toLrcFile()
                        val query = SongInfo(song.title, song.artist)
                        var queryResult: SongInfo? = null
                        try {
                            queryResult = viewModel.getSongInfo(query)
                        } catch (e: Exception) {
                            when (e) {
                                is FileNotFoundException -> {
                                    notFoundInARow++
                                    failedCount++
                                    if (notFoundInARow >= 5) {
                                        uiState = UiState.RateLimited
                                        return@launch
                                    }
                                    continue
                                }
                                is NoTrackFoundException, is EmptyQueryException -> {
                                    // not increasing notFoundInARow because that is for rate limit
                                    failedCount++
                                }
                                else -> throw e
                            }
                        }
                        notFoundInARow = 0
                        if (queryResult != null) {
                            val lyricsResult: String
                            try {
                                lyricsResult = viewModel.getSyncedLyrics(queryResult.songLink!!)!!
                            } catch (e: Exception) {
                                when (e) {
                                    is NullPointerException, is FileNotFoundException -> {
                                        noLyricsCount++
                                        continue
                                    }

                                    else -> throw e
                                }
                            }
                            val lrc =
                                "[ti:${queryResult.songName}]\n" +
                                        "[ar:${queryResult.artistName}]\n" +
                                        "[by:$generatedUsingString]\n" +
                                        lyricsResult
                            try {
                                file?.writeText(lrc)
                            } catch (e: FileNotFoundException) {
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && !song.filePath!!.contains("/storage/emulated/0")) {
                                    val sd = context.externalCacheDirs[1].absolutePath.substring(0, context.externalCacheDirs[1].absolutePath.indexOf("/Android/data"))
                                    val path = file?.absolutePath?.substringAfter(sd)?.split("/")?.dropLast(1)
                                    var sdCardFiles = DocumentFile.fromTreeUri(
                                        context,
                                        Uri.parse(viewModel.sdCardPath)
                                    )
                                    for (element in path!!) {
                                        for (sdCardFile in sdCardFiles!!.listFiles()) {
                                            if (sdCardFile.name == element) {
                                                sdCardFiles = sdCardFile
                                            }
                                        }
                                    }
                                    sdCardFiles?.listFiles()?.forEach {
                                        if(it.name == file.name) {
                                            it.delete()
                                            return@forEach
                                        }
                                    }
                                    sdCardFiles?.createFile(
                                        "text/lrc",
                                        file.name
                                    )?.let {
                                        context.contentResolver.openOutputStream(it.uri)?.write(lrc.toByteArray())
                                    }
                                } else {
                                    throw e
                                }
                            }
                            successCount++
                        }
                    }
                    uiState = UiState.Done
                }
            }
        }

        UiState.Done -> {
            notificationManager.cancelAll()

            val notificationBuilder = NotificationCompat.Builder(context, channelID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.download_complete))
                .setContentText(context.getString(R.string.success_failed, successCount, noLyricsCount, failedCount))
                .setContentIntent(pendingIntent)

            notificationManager.notify(2, notificationBuilder.build())

            AlertDialog(
                title = {
                    Text(text = stringResource(id = R.string.batch_download_lyrics))
                },
                text = {
                    Column {
                        Text(text = stringResource(R.string.download_complete))
                        Text(text = stringResource(R.string.success, successCount))
                        Text(text = stringResource(R.string.no_lyrics, noLyricsCount))
                        Text(text = stringResource(R.string.failed, failedCount))
                    }
                },
                onDismissRequest = {
                    uiState = UiState.Cancelled
                },
                confirmButton = {
                    Button(onClick = { uiState = UiState.Cancelled }) {
                        Text(text = stringResource(id = R.string.ok))
                    }
                }
            )
        }

        UiState.RateLimited -> {
            notificationManager.cancelAll()

            AlertDialog(
                title = {
                    Text(text = stringResource(id = R.string.batch_download_lyrics))
                },
                text = {
                    Column {
                        Text(text = stringResource(R.string.spotify_api_rate_limit_reached))
                        Text(text = stringResource(R.string.please_try_again_later))
                        Text(text = stringResource(R.string.change_api_strategy))
                    }
                },
                onDismissRequest = {
                    uiState = UiState.Cancelled
                },
                confirmButton = {
                    Button(onClick = { uiState = UiState.Cancelled }) {
                        Text(text = stringResource(id = R.string.ok))
                    }
                }
            )
        }
    }
}

enum class UiState {
    Warning, LegacyPrompt, Pending, Done, RateLimited, Cancelled
}