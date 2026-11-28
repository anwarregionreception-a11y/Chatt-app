package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val questions = listOf(
    "Millionaire app kya hai?" to
        "Ek community chat app jahan aap friends bana kar chat kar sakte hain. Is mein groups, coins aur authority levels bhi hain.",
    "Account kaise banta hai?" to
        "Login screen par Create Account dabayen aur naam, email aur password likhein. Password mein kam az kam 8 huroof, 1 capital aur 1 number ho.",
    "Password bhool jaun to?" to
        "Forgot Password dabayen. Reset link aap ki email par aayega. Spam folder bhi dekhein.",
    "Friend kaise banayen?" to
        "Chat page par add button dabayen, poori email ya naam se search karein aur Add dabayen. Doosre user ke Accept karne ke baad chat ho sakti hai.",
    "Kya main link bhej sakta hoon?" to
        "Nahi. Chat mein website ya app ke links bhejna mana hai.",
    "Flooding kya hai?" to
        "2 second mein 4 ya zyada message bhejna. Pehli dafa warning milti hai, dobara karne par 90 second ke liye message band ho jate hain.",
    "Meri chat kahan save hoti hai?" to
        "Message pohanchne tak server par rehta hai, phir aap ke phone mein save hota hai. App ka data mitane ya phone badalne par purani chat wapas nahi aati.",
    "Online status kaise dikhta hai?" to
        "App khuli hone par woh waqfe waqfe se aakhri istemal ka waqt save karti hai. Yeh maloomat aur device ID app Owner dekh sakta hai.",
    "Coins aur Authority kya hain?" to
        "Coins app ke andar ki currency hain. Authority coins se aap ka level (1 se 16) tay hota hai."
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaqScreen(onBack: () -> Unit) {
    var openQuestion by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FAQ - Sawal Jawab") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(questions, key = { it.first }) { (question, answer) ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openQuestion = if (openQuestion == question) null else question }
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(question, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        if (openQuestion == question) {
                            Text(answer, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
