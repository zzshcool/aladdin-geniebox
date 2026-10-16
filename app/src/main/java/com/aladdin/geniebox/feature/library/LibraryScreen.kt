package com.aladdin.geniebox.feature.library

import com.aladdin.geniebox.R
import com.aladdin.geniebox.app.AppLanguage
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aladdin.geniebox.app.GenieApplication
import com.aladdin.geniebox.domain.model.Game
import com.aladdin.geniebox.domain.model.PlatformType
import com.aladdin.geniebox.ui.component.FocusableButton
import com.aladdin.geniebox.ui.component.OfflineBadge
import com.aladdin.geniebox.ui.component.PlatformBadge
import com.aladdin.geniebox.ui.component.TvCard
import com.aladdin.geniebox.ui.theme.*
import kotlinx.coroutines.launch

enum class LibraryCategory(private val titleRes: Int) {
    ALL(R.string.ui_all_games),
    NES(R.string.platform_nes),
    GB(R.string.platform_gb),
    GBA(R.string.platform_gba),
    SFC(R.string.ui_snes),
    PS1(R.string.platform_ps1),
    PSP(R.string.platform_psp),
    FAVORITES(R.string.ui_favorites),
    RECENT(R.string.ui_recently_played);

    val displayName: String get() = AppLanguage.text(titleRes)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onGameSelect: (Game) -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateWelcome: () -> Unit
) {
    val app = GenieApplication.instance
    val coroutineScope = rememberCoroutineScope()
    val isTvDevice = remember { app.settingsRepository.isTvDevice() }
    val configuration = LocalConfiguration.current
    val compactLandscape = !isTvDevice && configuration.screenWidthDp > configuration.screenHeightDp
    val gridState = rememberLazyGridState()
    val cardFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val platformCategories = remember { LibraryCategory.entries.filter { it != LibraryCategory.FAVORITES && it != LibraryCategory.RECENT } }

    BackHandler(enabled = !isTvDevice, onBack = onNavigateWelcome)

    var selectedCategory by rememberSaveable { mutableStateOf(LibraryCategory.ALL) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var focusedGameId by rememberSaveable { mutableStateOf<String?>(null) }

    var sortOrder by rememberSaveable { mutableIntStateOf(0) }
    var showSortMenu by remember { mutableStateOf(false) }
    var offlineGame by remember { mutableStateOf<Game?>(null) }

    // 資料串流
    val allGames by app.gameRepository.getAllGames().collectAsState(initial = emptyList())
    val favoriteGames by app.gameRepository.getFavoriteGames().collectAsState(initial = emptyList())
    val recentGames by app.gameRepository.getRecentGames().collectAsState(initial = emptyList())

    // 依選中分類與搜尋字串過濾
    val displayedGames = remember(allGames, favoriteGames, recentGames, selectedCategory, searchQuery, sortOrder) {
        val baseList = when (selectedCategory) {
            LibraryCategory.ALL -> allGames
            LibraryCategory.NES -> allGames.filter { it.platform == PlatformType.NES }
            LibraryCategory.GB -> allGames.filter { it.platform == PlatformType.GB }
            LibraryCategory.GBA -> allGames.filter { it.platform == PlatformType.GBA }
            LibraryCategory.SFC -> allGames.filter { it.platform == PlatformType.SFC }
            LibraryCategory.PS1 -> allGames.filter { it.platform == PlatformType.PS1 }
            LibraryCategory.PSP -> allGames.filter { it.platform == PlatformType.PSP }
            LibraryCategory.FAVORITES -> favoriteGames
            LibraryCategory.RECENT -> recentGames
        }

        val filtered = if (searchQuery.trim().isEmpty()) {
            baseList
        } else {
            baseList.filter { g ->
                com.aladdin.geniebox.data.scanner.PinyinZhuYinIndex.matches(
                    title = g.title,
                    pinyinIndex = g.pinyinIndex,
                    zhuyinIndex = g.zhuyinIndex,
                    query = searchQuery
                )
            }
        }
        when (sortOrder) {
            1 -> filtered.sortedBy { it.title.lowercase() }
            2 -> filtered.sortedByDescending { it.lastPlayedTime ?: 0L }
            else -> filtered
        }
    }


    val restoreGameId = focusedGameId?.takeIf { id -> displayedGames.any { it.id == id } } ?: displayedGames.firstOrNull()?.id
    LaunchedEffect(isTvDevice, displayedGames.isNotEmpty()) {
        if (isTvDevice && restoreGameId != null) {
            val index = displayedGames.indexOfFirst { it.id == restoreGameId }
            if (index >= 0) {
                if (gridState.layoutInfo.visibleItemsInfo.none { it.key == restoreGameId }) {
                    gridState.scrollToItem(index)
                }
                withFrameNanos { }
                runCatching { cardFocusRequesters[restoreGameId]?.requestFocus() }
            }
        }
    }

    val currentDisplayedGames by rememberUpdatedState(displayedGames)
    val currentFocusedGameId by rememberUpdatedState(focusedGameId)
    val searchFocusRequester = remember { FocusRequester() }

    // 註冊 TV 實體手把快捷鍵 (Finding 11, R2-15)
    DisposableEffect(Unit) {
        app.inputManager.uiShortcutListener = object : com.aladdin.geniebox.input.UiShortcutListener {
            override fun onToggleFavorite() {
                val targetGame = currentDisplayedGames.find { it.id == currentFocusedGameId }
                    ?: currentDisplayedGames.firstOrNull()
                if (targetGame != null) {
                    coroutineScope.launch {
                        app.gameRepository.toggleFavorite(targetGame.id, targetGame.isFavorite)
                    }
                }
            }

            override fun onFocusSearch() {
                isSearchActive = true
                coroutineScope.launch {
                    withFrameNanos { }
                    try {
                        searchFocusRequester.requestFocus()
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }

            override fun onPreviousCategory() {
                val values = platformCategories
                val prevIdx = (values.indexOf(selectedCategory).coerceAtLeast(0) - 1 + values.size) % values.size
                selectedCategory = values[prevIdx]
            }

            override fun onNextCategory() {
                val values = platformCategories
                val nextIdx = (values.indexOf(selectedCategory).coerceAtLeast(0) + 1) % values.size
                selectedCategory = values[nextIdx]
            }
        }

        onDispose {
            app.inputManager.uiShortcutListener = null
        }
    }

    val sortMenu: @Composable () -> Unit = {
    Box {
        if (isTvDevice) FocusableButton(text = AppLanguage.text(R.string.ux_sort), onClick = { showSortMenu = true })
        else IconButton(onClick = { showSortMenu = true }) {
            Icon(Icons.Default.Sort, contentDescription = AppLanguage.text(R.string.ux_sort), tint = TextSecondary)
        }
        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
            listOf(R.string.ux_default_order, R.string.ux_title_order, R.string.ux_recent_order).forEachIndexed { order, label ->
                DropdownMenuItem(text = { Text(AppLanguage.text(label)) }, onClick = { sortOrder = order; showSortMenu = false })
            }
        }
    }
    }

    Scaffold(
        containerColor = BgDark,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgDark)
                    .padding(horizontal = if (isTvDevice) 32.dp else 20.dp, vertical = if (compactLandscape) 4.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (compactLandscape) 4.dp else 10.dp)
            ) {
                if (compactLandscape) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(onClick = onNavigateWelcome) {
                            Icon(Icons.Default.ArrowBack, contentDescription = AppLanguage.text(R.string.ui_welcome), tint = TextSecondary)
                        }
                        if (isSearchActive || searchQuery.isNotEmpty()) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(AppLanguage.text(R.string.ui_search_games), fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(56.dp).focusRequester(searchFocusRequester)
                        )
                        IconButton(onClick = { searchQuery = ""; isSearchActive = false }) {
                            Icon(Icons.Default.Close, contentDescription = AppLanguage.text(R.string.polish_close_search), tint = TextSecondary)
                        }
                        } else {
                            Text(AppLanguage.text(when (selectedCategory) {
                                LibraryCategory.FAVORITES -> R.string.ui_favorites
                                LibraryCategory.RECENT -> R.string.ui_recently_played
                                else -> R.string.ui_library
                            }) + " · " + displayedGames.size,
                                modifier = Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold,
                                color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            IconButton(onClick = {
                                isSearchActive = true
                                coroutineScope.launch { withFrameNanos { }; runCatching { searchFocusRequester.requestFocus() } }
                            }) {
                                Icon(Icons.Default.Search, contentDescription = AppLanguage.text(R.string.ui_search_games), tint = TextSecondary)
                            }
                        }
                        sortMenu()
                    }
                } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Image(painterResource(R.drawable.ic_launcher), contentDescription = null, modifier = Modifier.size(48.dp))
                    Column {
                        Text(
                            text = when (selectedCategory) {
                                LibraryCategory.FAVORITES -> AppLanguage.text(R.string.ui_favorites)
                                LibraryCategory.RECENT -> AppLanguage.text(R.string.ui_recently_played)
                                else -> AppLanguage.text(R.string.ui_library)
                            },
                            fontSize = if (isTvDevice) 32.sp else 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(AppLanguage.text(R.string.ux_game_count, displayedGames.size), fontSize = 12.sp, color = TextSecondary)
                    }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        sortMenu()
                        if (!isTvDevice) {
                            TextButton(onClick = onNavigateWelcome) {
                                Text(AppLanguage.text(R.string.ui_welcome))
                            }
                        }
                        if (isTvDevice) FocusableButton(text = AppLanguage.text(R.string.ui_settings), onClick = onNavigateSettings)
                    }
                }
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(AppLanguage.text(R.string.ui_search_your_games), color = TextTertiary) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = AppLanguage.text(R.string.polish_clear_search))
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AladdinGold,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = CardDark,
                        unfocusedContainerColor = CardDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = AladdinGold
                    )
                )
                }
                if (selectedCategory != LibraryCategory.FAVORITES && selectedCategory != LibraryCategory.RECENT) ScrollableTabRow(
                    selectedTabIndex = platformCategories.indexOf(selectedCategory).coerceAtLeast(0),
                    containerColor = BgDark,
                    contentColor = AladdinGold,
                    edgePadding = 0.dp,
                    divider = {}
                ) {
                    platformCategories.forEach { category ->
                        val isSelected = category == selectedCategory
                        Tab(
                            selected = isSelected,
                            onClick = { selectedCategory = category },
                            text = {
                                Text(category.displayName,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) TextPrimary else TextSecondary)
                            }
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (isTvDevice) {
                Surface(color = CardDark) {
                    Text(
                        text = AppLanguage.text(R.string.ui_a_play_y_favorite_l_r_change_platform),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 14.dp),
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            } else if (!compactLandscape) {
                NavigationBar(containerColor = BgDark, tonalElevation = 0.dp) {
                    NavigationBarItem(
                        selected = selectedCategory != LibraryCategory.FAVORITES && selectedCategory != LibraryCategory.RECENT,
                        onClick = { selectedCategory = LibraryCategory.ALL },
                        icon = { Icon(Icons.Default.Apps, contentDescription = null) },
                        label = { Text(AppLanguage.text(R.string.ui_library)) }
                    )
                    NavigationBarItem(
                        selected = selectedCategory == LibraryCategory.RECENT,
                        onClick = { selectedCategory = LibraryCategory.RECENT },
                        icon = { Icon(Icons.Default.History, contentDescription = null) },
                        label = { Text(AppLanguage.text(R.string.ui_recent)) }
                    )
                    NavigationBarItem(
                        selected = selectedCategory == LibraryCategory.FAVORITES,
                        onClick = { selectedCategory = LibraryCategory.FAVORITES },
                        icon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                        label = { Text(AppLanguage.text(R.string.ui_favorites_34)) }
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = onNavigateSettings,
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text(AppLanguage.text(R.string.ui_settings)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (compactLandscape) {
                Column(
                    modifier = Modifier.width(56.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(onClick = { selectedCategory = LibraryCategory.ALL },
                        modifier = Modifier.background(if (selectedCategory != LibraryCategory.RECENT && selectedCategory != LibraryCategory.FAVORITES)
                            AladdinGold.copy(alpha = 0.14f) else Color.Transparent, RoundedCornerShape(12.dp))) {
                        Icon(Icons.Default.Apps, contentDescription = AppLanguage.text(R.string.ui_library),
                            tint = if (selectedCategory != LibraryCategory.RECENT && selectedCategory != LibraryCategory.FAVORITES) AladdinGold else TextSecondary)
                    }
                    IconButton(onClick = { selectedCategory = LibraryCategory.RECENT },
                        modifier = Modifier.background(if (selectedCategory == LibraryCategory.RECENT)
                            AladdinGold.copy(alpha = 0.14f) else Color.Transparent, RoundedCornerShape(12.dp))) {
                        Icon(Icons.Default.History, contentDescription = AppLanguage.text(R.string.ui_recently_played),
                            tint = if (selectedCategory == LibraryCategory.RECENT) AladdinGold else TextSecondary)
                    }
                    IconButton(onClick = { selectedCategory = LibraryCategory.FAVORITES },
                        modifier = Modifier.background(if (selectedCategory == LibraryCategory.FAVORITES)
                            AladdinGold.copy(alpha = 0.14f) else Color.Transparent, RoundedCornerShape(12.dp))) {
                        Icon(Icons.Default.Favorite, contentDescription = AppLanguage.text(R.string.ui_favorites),
                            tint = if (selectedCategory == LibraryCategory.FAVORITES) AladdinGold else TextSecondary)
                    }
                    IconButton(onClick = onNavigateSettings) {
                        Icon(Icons.Default.Settings, contentDescription = AppLanguage.text(R.string.ui_settings_and_game_folders), tint = TextSecondary)
                    }
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = if (isTvDevice) 32.dp else if (compactLandscape) 12.dp else 20.dp)
            ) {
                if (displayedGames.isEmpty()) {
                    // 空狀態展示
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = when {
                                    searchQuery.isNotEmpty() -> AppLanguage.text(R.string.ui_no_results_for_value, searchQuery)
                                    selectedCategory == LibraryCategory.FAVORITES -> AppLanguage.text(R.string.ui_keep_your_favorites_here)
                                    selectedCategory == LibraryCategory.RECENT -> AppLanguage.text(R.string.ui_your_next_game_starts_here)
                                    else -> AppLanguage.text(R.string.ui_start_your_collection_with_a_folder)
                                },
                                fontSize = 18.sp,
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = when {
                                    searchQuery.isNotEmpty() -> AppLanguage.text(R.string.ui_try_another_name_or_search_by_pinyin)
                                    selectedCategory == LibraryCategory.FAVORITES -> AppLanguage.text(R.string.ux_favorites_hint)
                                    selectedCategory == LibraryCategory.RECENT -> AppLanguage.text(R.string.ux_recent_hint)
                                    allGames.isNotEmpty() -> AppLanguage.text(R.string.ux_platform_empty_hint)
                                    else -> AppLanguage.text(R.string.ui_add_a_game_folder_from_your_phone_usb)
                                },
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontSize = 14.sp,
                                color = TextTertiary
                            )
                            FocusableButton(
                                text = if (allGames.isNotEmpty()) AppLanguage.text(R.string.ux_browse_all) else AppLanguage.text(R.string.ui_add_game_folder),
                                isPrimary = true,
                                onClick = {
                                    if (allGames.isNotEmpty()) { selectedCategory = LibraryCategory.ALL; searchQuery = "" }
                                    else onNavigateSettings()
                                }
                            )
                        }
                    }
                } else {
                    // 海報牆網格
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                    val posterHeight = if (compactLandscape) maxHeight.coerceIn(128.dp, 220.dp) else 260.dp
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = if (isTvDevice) 190.dp else 148.dp),
                        contentPadding = PaddingValues(vertical = if (compactLandscape) 4.dp else 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(displayedGames, key = { it.id }) { game ->
                            GamePosterCard(
                                game = game,
                                cardHeight = posterHeight,
                                focusRequester = if (isTvDevice) cardFocusRequesters.getOrPut(game.id) { FocusRequester() } else null,
                                onPlay = {
                                    focusedGameId = game.id
                                    if (game.isAvailable) onGameSelect(game) else offlineGame = game
                                },
                                onToggleFavorite = {
                                    coroutineScope.launch {
                                        app.gameRepository.toggleFavorite(game.id, game.isFavorite)
                                    }
                                },
                                onFocused = {
                                    focusedGameId = game.id
                                }
                            )
                        }
                    }
                    }
                }
            }
        }
    }
    }
    offlineGame?.let { game ->
        AlertDialog(
            onDismissRequest = { offlineGame = null },
            title = { Text(AppLanguage.text(R.string.ui_source_offline)) },
            text = { Text(AppLanguage.text(R.string.ux_offline_game_hint, game.title)) },
            confirmButton = {
                TextButton(onClick = { offlineGame = null; onNavigateSettings() }) {
                    Text(AppLanguage.text(R.string.ui_manage_game_folders))
                }
            },
            dismissButton = {
                TextButton(onClick = { offlineGame = null }) { Text(AppLanguage.text(R.string.ux_cancel)) }
            }
        )
    }
}

@Composable
fun GamePosterCard(
    game: Game,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onFocused: () -> Unit = {},
    cardHeight: Dp = 260.dp,
    focusRequester: FocusRequester? = null
) {
    var coverReady by remember(game.coverUri) { mutableStateOf(false) }
    TvCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { state ->
                if (state.isFocused) {
                    onFocused()
                }
            },
        onClick = onPlay
    ) { isFocused ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(CardDarkFocused)
        ) {
            if (game.coverUri != null) {
                AsyncImage(
                    model = game.coverUri,
                    contentDescription = game.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    onSuccess = { coverReady = true },
                    onError = { coverReady = false }
                )
            }
            if (!coverReady) {
                // 無封面或封面讀取失敗時，仍顯示可辨識的遊戲名稱。
                Column(
                    modifier = Modifier.fillMaxSize()
                        .background(Brush.verticalGradient(listOf(CardDarkFocused, AladdinGold.copy(alpha = 0.14f))))
                        .padding(horizontal = 14.dp, vertical = if (cardHeight < 200.dp) 12.dp else 34.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    if (cardHeight >= 200.dp) Text(
                        text = game.platform.id.uppercase(),
                        fontSize = 11.sp, letterSpacing = 2.sp,
                        fontWeight = FontWeight.SemiBold, color = AladdinGold
                    )
                    if (cardHeight >= 200.dp) Spacer(Modifier.height(8.dp))
                    Text(game.title, fontSize = if (cardHeight < 200.dp) 14.sp else 18.sp, fontWeight = FontWeight.SemiBold,
                        color = TextPrimary, maxLines = if (cardHeight < 200.dp) 2 else 3, overflow = TextOverflow.Ellipsis)
                }
            }

            // 平台徽章與離線狀態
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp)
                    .align(Alignment.TopStart),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PlatformBadge(platform = game.platform)
                if (!game.isAvailable) {
                    OfflineBadge()
                }
            }

            // 收藏圖標
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.55f), androidx.compose.foundation.shape.CircleShape)
            ) {
                Icon(
                    imageVector = if (game.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = AppLanguage.text(if (game.isFavorite) R.string.ux_remove_favorite else R.string.ux_add_favorite, game.title),
                    tint = if (game.isFavorite) AccentRed else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = game.title,
            fontSize = 14.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            color = if (isFocused) AladdinGoldLight else TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
