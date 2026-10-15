package com.aladdin.geniebox.feature.library

import androidx.compose.foundation.background
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
    onNavigateSettings: () -> Unit
) {
    val app = GenieApplication.instance
    val coroutineScope = rememberCoroutineScope()

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
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                // 頂部列：以可用寬度響應式分流手機直向窄版 (2 行) 與 TV / 橫向寬版 (單行)
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val isNarrow = maxWidth < 640.dp
                    if (isNarrow) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "阿拉丁 GenieBox",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AladdinGold
                                )
                                FocusableButton(
                                    text = "設定與手把",
                                    onClick = onNavigateSettings
                                )
                            }
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("搜尋遊戲 (中文/拼音/注音/英文)…", fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = "搜尋", tint = TextSecondary)
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .focusRequester(searchFocusRequester)
                                    .fillMaxWidth()
                                    .height(50.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AladdinGold,
                                    unfocusedBorderColor = DividerDark,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    cursorColor = AladdinGold
                                )
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "阿拉丁 GenieBox",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = AladdinGold
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("搜尋遊戲 (中文/拼音/注音/英文)…", fontSize = 13.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Search, contentDescription = "搜尋", tint = TextSecondary)
                                    },
                                    singleLine = true,
                                    modifier = Modifier
                                        .focusRequester(searchFocusRequester)
                                        .width(320.dp)
                                        .height(50.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AladdinGold,
                                        unfocusedBorderColor = DividerDark,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        cursorColor = AladdinGold
                                    )
                                )

                                FocusableButton(
                                    text = "設定與手把",
                                    onClick = onNavigateSettings
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 分類標籤列
                ScrollableTabRow(
                    selectedTabIndex = selectedCategory.ordinal,
                    containerColor = BgDark,
                    contentColor = AladdinGold,
                    edgePadding = 0.dp,
                    divider = {}
                ) {
                    LibraryCategory.entries.forEach { category ->
                        val isSelected = (category == selectedCategory)
                        val interactionSource = remember { MutableInteractionSource() }
                        val isFocused by interactionSource.collectIsFocusedAsState()

                        Tab(
                            selected = isSelected,
                            onClick = { selectedCategory = category },
                            interactionSource = interactionSource,
                            modifier = Modifier
                                .focusable(interactionSource = interactionSource)
                                .padding(horizontal = 4.dp, vertical = 6.dp)
                                .background(
                                    color = if (isFocused) AladdinGold.copy(alpha = 0.2f) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            text = {
                                Text(
                                    text = category.displayName,
                                    fontSize = 15.sp,
                                    fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isFocused -> AladdinGoldLight
                                        isSelected -> AladdinGold
                                        else -> TextSecondary
                                    }
                                )
                            }
                        )
                    }
                }
            }
        },
        bottomBar = {
            // TV 提示條：顯示實際控制器提示
            Surface(
                color = CardDark,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("提示：", fontSize = 12.sp, color = AladdinGold, fontWeight = FontWeight.Bold)
                    Text("【A / 確認】啟動遊戲", fontSize = 12.sp, color = TextSecondary)
                    Text("【Y】切換收藏", fontSize = 12.sp, color = TextSecondary)
                    Text("【L1 / R1】切換分類標籤", fontSize = 12.sp, color = TextSecondary)
                    Text("【返回】退出選單", fontSize = 12.sp, color = TextSecondary)
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
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
                            text = if (searchQuery.isNotEmpty()) "找不到符合「$searchQuery」的遊戲" else "尚未加入任何遊戲",
                            fontSize = 18.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "請確認已插入包含 GenieROMs/ 目錄之 USB 或 SD 卡，並至設定中完成來源授權與掃描。",
                            fontSize = 14.sp,
                            color = TextTertiary
                        )
                        FocusableButton(
                            text = "開啟設定與來源管理",
                            isPrimary = true,
                            onClick = onNavigateSettings
                        )
                    }
                }
            } else {
                // 海報牆網格
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 170.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(displayedGames, key = { it.id }) { game ->
                        GamePosterCard(
                            game = game,
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

@Composable
fun GamePosterCard(
    game: Game,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onFocused: () -> Unit = {}
) {
    TvCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
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
