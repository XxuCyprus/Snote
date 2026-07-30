package com.snote.app.ui.screen.search

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.snote.app.data.repository.DataRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBackClick: () -> Unit,
    onResultClick: (notebookId: String, chapterId: String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val hasSearched by viewModel.hasSearched.collectAsState()
    val activeFilters by viewModel.activeFilters.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { viewModel.updateQuery(it) },
                        placeholder = { Text("搜索笔记内容...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.clearSearch() }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = "清空")
                                }
                            }
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.search() }) {
                        Icon(Icons.Rounded.Search, contentDescription = "搜索")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        if (!hasSearched) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "输入关键词搜索笔记内容",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        } else if (results.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Rounded.SearchOff,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "没有找到相关内容",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "找到 ${results.size} 条结果",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        FilterChip(
                            selected = "content" in activeFilters,
                            onClick = { viewModel.toggleFilter("content") },
                            label = {
                                Text(
                                    text = "内容匹配",
                                    fontSize = 10.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.White,
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                selectedBorderColor = MaterialTheme.colorScheme.primary,
                                enabled = true,
                                selected = "content" in activeFilters
                            ),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.height(26.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        FilterChip(
                            selected = "title" in activeFilters,
                            onClick = { viewModel.toggleFilter("title") },
                            label = {
                                Text(
                                    text = "标题匹配",
                                    fontSize = 10.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.White,
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                selectedBorderColor = MaterialTheme.colorScheme.primary,
                                enabled = true,
                                selected = "title" in activeFilters
                            ),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.height(26.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        FilterChip(
                            selected = "file" in activeFilters,
                            onClick = { viewModel.toggleFilter("file") },
                            label = {
                                Text(
                                    text = "文件名匹配",
                                    fontSize = 10.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.White,
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                selectedBorderColor = MaterialTheme.colorScheme.primary,
                                enabled = true,
                                selected = "file" in activeFilters
                            ),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.height(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                val filteredResults = results.filter { it.matchType in activeFilters }

                itemsIndexed(
                    items = filteredResults,
                    key = { index, result -> "${result.notebookId}_${result.chapterId}_$index" }
                ) { _, result ->
                    SearchResultCard(
                        result = result,
                        onClick = { onResultClick(result.notebookId, result.chapterId) }
                    )
                }
            }
        }
    }
}

@Composable
fun SearchResultCard(
    result: DataRepository.SearchResult,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = result.notebookTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.weight(1f))
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = when (result.matchType) {
                                "title" -> "标题匹配"
                                "content" -> "内容匹配"
                                else -> "文件名匹配"
                            },
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Color.White
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.height(26.dp)
                )
            }

            if (result.ancestorPath.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = result.ancestorPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = result.chapterTitle.ifEmpty { result.notebookTitle },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            if (result.matchedText != result.chapterTitle && result.matchedText != result.notebookTitle) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = result.matchedText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
