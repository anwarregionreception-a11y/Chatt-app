@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.remote.Live
import com.example.domain.AuthorityEngine
import com.example.domain.Countries

private data class Rank(
    val id: String,
    val name: String,
    val detail: String,
    val isGroup: Boolean = false
)

private val tabs = listOf("Coin Buyers", "Authority", "Groups", "App Review")

/** Top 10 lists, plus the app review page. */
@Composable
fun LeaderboardTab(
    onOpenGroup: (String) -> Unit,
    onOpenProfile: (String, String) -> Unit,
    reviews: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    var tab by remember { mutableIntStateOf(0) }
    Column(modifier = modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
            }
        }
        if (tab == 3) {
            reviews()
        } else {
            TopTen(tab, onOpenGroup, onOpenProfile)
        }
    }
}

@Composable
private fun TopTen(tab: Int, onOpenGroup: (String) -> Unit, onOpenProfile: (String, String) -> Unit) {
    val viewModel = LocalAppViewModel.current
    val ranks: Live<List<Rank>> = rememberLoad(tab) {
        when (tab) {
            0 -> viewModel.community.topBuyers().map { list ->
                list.map { Rank(it.uid, it.name, "${it.totalCoins} coins khareede") }
            }
            1 -> viewModel.account.topAuthority().map { list ->
                list.map {
                    Rank(
                        it.uid,
                        "${it.name} ${Countries.flag(it.country)}",
                        "Authority ${it.authority} • Level ${AuthorityEngine.getLevel(it.authority)}"
                    )
                }
            }
            else -> viewModel.groups.topGroups().map { list ->
                list.map { Rank(it.id, it.name, "${it.memberCount} members", isGroup = true) }
            }
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        liveStatus(ranks, "Abhi list khali hai")
        val list = ranks.data.orEmpty()
        items(list, key = { it.id }) { rank ->
            PersonRow(
                name = "#${list.indexOf(rank) + 1}  ${rank.name}",
                subtitle = rank.detail,
                onClick = {
                    if (rank.isGroup) onOpenGroup(rank.id) else onOpenProfile(rank.id, rank.name)
                }
            )
        }
    }
}
