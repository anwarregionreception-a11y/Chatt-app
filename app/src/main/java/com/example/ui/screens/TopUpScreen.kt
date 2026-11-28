@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.AppConstants
import com.example.data.local.UserEntity

/** Buy coins through one of the payment methods the Owner has added. */
@Composable
fun TopUpScreen(currentUser: UserEntity, onBack: () -> Unit) {
    val viewModel = LocalAppViewModel.current
    val myUid by viewModel.currentUserId.collectAsStateWithLifecycle()
    val uid = myUid.orEmpty()
    val methods = rememberLive(uid) { viewModel.community.observePaymentMethods() }
    val orders = rememberLive(uid) { viewModel.community.observeOwnTopUps(uid) }

    var selectedId by remember { mutableStateOf("") }
    var coinsText by remember { mutableStateOf("100") }
    var reference by remember { mutableStateOf("") }
    val method = methods.data?.firstOrNull { it.id == selectedId } ?: methods.data?.firstOrNull()
    val coins = coinsText.toLongOrNull() ?: 0L

    ListPage(title = "Top-up (Coins khareedein)", onBack = onBack) {
        item {
            SectionCard {
                Text("Aap ke coins: ${currentUser.walletCoins}", style = MaterialTheme.typography.titleLarge)
                Text("1 Coin = ${AppConstants.COIN_PRICE_PKR} PKR. Coins Owner ke approve karne par milte hain.")
            }
        }
        item { Text("Payment method", style = MaterialTheme.typography.titleMedium) }
        liveStatus(methods, "Owner ne abhi koi payment method add nahi kiya")
        items(methods.data.orEmpty(), key = { it.id }) { item ->
            SectionCard {
                FilterChip(
                    selected = item.id == method?.id,
                    onClick = { selectedId = item.id },
                    label = { Text(item.name) }
                )
                SelectionContainer {
                    Column {
                        Text("Account: ${item.account}")
                        if (item.holder.isNotBlank()) Text("Naam: ${item.holder}")
                        if (item.instructions.isNotBlank()) Text(item.instructions)
                    }
                }
                if (item.minCoins > 1) Text("Kam az kam ${item.minCoins} coins")
            }
        }
        if (method != null) {
            item {
                SectionCard {
                    OutlinedTextField(
                        value = coinsText,
                        onValueChange = { text -> coinsText = text.filter { it.isDigit() }.take(9) },
                        label = { Text("Kitne coins") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("buy_coins_amount_input")
                    )
                    OutlinedTextField(
                        value = reference,
                        onValueChange = { reference = it.take(100) },
                        label = { Text("Transaction ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Raqam: ${coins * AppConstants.COIN_PRICE_PKR} PKR (${method.name})")
                    Button(
                        onClick = {
                            val ref = reference
                            val amount = coins
                            viewModel.runAction("Order bhej diya. Owner ke approve ka intezar karein.") { me ->
                                viewModel.community.submitTopUp(
                                    me, method, amount, AppConstants.COIN_PRICE_PKR, ref
                                ).onSuccess { reference = "" }
                            }
                        },
                        enabled = coins > 0 && reference.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().testTag("submit_coin_order_button")
                    ) { Text("Order bhejein") }
                }
            }
        }
        item { Text("Mere orders", style = MaterialTheme.typography.titleMedium) }
        liveStatus(orders, "Abhi koi order nahi")
        items(orders.data.orEmpty(), key = { it.id }) { order ->
            SectionCard {
                Text("${order.coins} Coins • ${order.method} • ${order.status}")
                Text(
                    "${order.amountPkr} PKR • Ref: ${order.reference} • ${formatDateTime(order.createdAt)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
