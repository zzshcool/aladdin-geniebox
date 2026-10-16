package com.aladdin.geniebox.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.History
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

enum class LibraryCategory(val displayName: String) {
    ALL("全部遊戲"),
    NES("NES / FC"),
    GB("GB / GBC"),
    GBA("GBA"),
    SFC("超任 SFC"),
    PS1("PS1"),
    PSP("PSP"),
    FAVORITES("我的收藏"),
    RECENT("最近遊玩")
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
    val posterHeight = if (compactLandscape) (configuration.screenHeightDp - 144).coerceIn(128, 220).dp else 260.dp

    BackHandler(enabled = !isTvDevice, onBack = onNavigateWelcome)

    var selectedCategory by remember { mutableStateOf(LibraryCategory.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var focusedGameId by remember { mutableStateOf<String?>(null) }

    // 資料串流
    val allGames by app.gameRepository.getAllGames().collectAsState(initial = emptyList())
    val favoriteGames by app.gameRepository.getFavoriteGames().collectAsState(initial = emptyList())
    val recentGames by app.gameRepository.getRecentGames().collectAsState(initial = emptyList())

    // 依選中分類與搜尋字串過濾
    val displayedGames = remember(allGames, favoriteGames, recentGames, selectedCategory, searchQuery) {
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

        if (searchQuery.trim().isEmpty()) {
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
                try {
                    searchFocusRequester.requestFocus()
                } catch (e: Exception) {
                    // ignore
                }
            }

            override fun onPreviousCategory() {
                val values = LibraryCategory.entries
                val prevIdx = if (selectedCategory.ordinal > 0) selectedCategory.ordinal - 1 else values.size - 1
                selectedCategory = values[prevIdx]
            }

            override fun onNextCategory() {
                val values = LibraryCategory.entries
                val nextIdx = (selectedCategory.ordinal + 1) % values.size
                selectedCategory = values[nextIdx]
            }
        }

        onDispose {
            app.inputManager.uiShortcutListener = null
        }
    }

    Scaffold(
        containerColor = BgDark,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgDark)
                    .padding(horizontal = if (isTvDevice) 32.dp else 20.dp, vertical = if (compactLandscape) 4.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(if (compactLandscape) 4.dp else 16.dp)
            ) {
                if (compactLandscape) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("遊戲庫", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("搜尋遊戲", fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(56.dp).focusRequester(searchFocusRequester)
                        )
                        TextButton(onClick = onNavigateWelcome) { Text("起始頁") }
                    }
                } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("GENIEBOX", fontSize = 12.sp, color = TextSecondary,
                            fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
                        Text(
                            text = when (selectedCategory) {
                                LibraryCategory.FAVORITES -> "我的收藏"
                                LibraryCategory.RECENT -> "最近遊玩"
                                else -> "遊戲庫"
                            },
                            fontSize = if (isTvDevice) 32.sp else 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!isTvDevice) {
                            TextButton(onClick = onNavigateWelcome) {
                                Text("起始頁")
                            }
                        }
                        IconButton(onClick = onNavigateSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "設定與遊戲資料夾", tint = TextSecondary)
                        }
                    }
                }
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("搜尋你的遊戲", color = TextTertiary) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
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
                ScrollableTabRow(
                    selectedTabIndex = selectedCategory.ordinal,
                    containerColor = BgDark,
                    contentColor = AladdinGold,
                    edgePadding = 0.dp,
                    divider = {}
                ) {
                    LibraryCategory.entries.forEach { category ->
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
                        text = "A  開始遊戲     Y  收藏     L / R  切換平台",
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
                        label = { Text("遊戲庫") }
                    )
                    NavigationBarItem(
                        selected = selectedCategory == LibraryCategory.RECENT,
                        onClick = { selectedCategory = LibraryCategory.RECENT },
                        icon = { Icon(Icons.Default.History, contentDescription = null) },
                        label = { Text("最近") }
                    )
                    NavigationBarItem(
                        selected = selectedCategory == LibraryCategory.FAVORITES,
                        onClick = { selectedCategory = LibraryCategory.FAVORITES },
                        icon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                        label = { Text("收藏") }
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = onNavigateSettings,
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("設定") }
                    )
                }
            }
        }
    ) { innerPadding ->
        Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (compactLandscape) {
                Column(
                    modifier = Modifier.width(56.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(onClick = { selectedCategory = LibraryCategory.ALL }) {
                        Icon(Icons.Default.Apps, contentDescription = "遊戲庫",
                            tint = if (selectedCategory != LibraryCategory.RECENT && selectedCategory != LibraryCategory.FAVORITES) AladdinGold else TextSecondary)
                    }
                    IconButton(onClick = { selectedCategory = LibraryCategory.RECENT }) {
                        Icon(Icons.Default.History, contentDescription = "最近遊玩",
                            tint = if (selectedCategory == LibraryCategory.RECENT) AladdinGold else TextSecondary)
                    }
                    IconButton(onClick = { selectedCategory = LibraryCategory.FAVORITES }) {
                        Icon(Icons.Default.Favorite, contentDescription = "我的收藏",
                            tint = if (selectedCategory == LibraryCategory.FAVORITES) AladdinGold else TextSecondary)
                    }
                    IconButton(onClick = onNavigateSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "設定與遊戲資料夾", tint = TextSecondary)
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
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = when {
                                    searchQuery.isNotEmpty() -> "沒有找到「$searchQuery」"
                                    selectedCategory == LibraryCategory.FAVORITES -> "把喜歡的遊戲留在這裡"
                                    selectedCategory == LibraryCategory.RECENT -> "下一場遊戲，從這裡開始"
                                    else -> "你的收藏，從一個資料夾開始"
                                },
                                fontSize = 18.sp,
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (searchQuery.isNotEmpty()) "換個名稱，或試試拼音搜尋。" else "加入手機、USB 或 SD 卡裡的遊戲資料夾。",
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontSize = 14.sp,
                                color = TextTertiary
                            )
                            FocusableButton(
                                text = if (searchQuery.isNotEmpty()) "管理遊戲資料夾" else "加入遊戲資料夾",
                                isPrimary = true,
                                onClick = onNavigateSettings
                            )
                        }
                    }
                } else {
                    // 海報牆網格
                    LazyVerticalGrid(
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
                                onPlay = { onGameSelect(game) },
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

@Composable
fun GamePosterCard(
    game: Game,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onFocused: () -> Unit = {},
    cardHeight: Dp = 260.dp
) {
    TvCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight)
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
                    contentScale = ContentScale.Crop
                )
            } else {
                // 無封面時之平台佔位圖
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = game.platform.id.uppercase(),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AladdinGold.copy(alpha = 0.25f)
                    )
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
                    .size(32.dp)
            ) {
                Icon(
                    imageVector = if (game.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "收藏",
                    tint = if (game.isFavorite) AccentRed else TextSecondary.copy(alpha = 0.7f),
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
