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
        "Create Account dabayen: naam, apni marzi ki User ID aur password likhein. Email optional hai. Password mein kam az kam 8 huroof, 1 capital aur 1 number ho.",
    "User ID kya hai?" to
        "Aap ki apni chuni hui pehchan. Har kisi ko dikhti hai, is se aap ko search kiya ja sakta hai aur login bhi hota hai. Ek dafa banne ke baad badli nahi ja sakti.",
    "Password bhool jaun to?" to
        "Email wale account mein Forgot Password se reset link aata hai. Baghair email wale account ka password reset nahi hota.",
    "Meri maloomat kaun dekh sakta hai?" to
        "Meri Profile mein mobile, email aur coins ko dikhane ya chhupane ka switch hai. Naam, User ID, country, authority aur registration date sab ko dikhte hain.",
    "Ek se zyada ID kaise chalayen?" to
        "Menu → ID Switch → Doosri ID add karein. Phir kisi bhi saved ID par tap karein.",
    "Group kaise banta hai?" to
        "Group page par Naya Group dabayen. 200 coins lagte hain. Public group mein koi bhi aa sakta hai; private group mein request approve hoti hai.",
    "Spin game kaise chalta hai?" to
        "Har 24 ghante mein ek spin. Inaam: 1 coin, 3 coins, 1 outline star, 3 outline stars ya baad mein koshish. Ek outline star se authority 1 barhti hai.",
    "Moderator, Cop aur Mentor kaun hain?" to
        "App ka staff, jinhein sirf Owner appoint karta hai. Menu → Support mein un ke kaam, contact aur apply karne ka tareeqa hai.",
    "Notification kaise milti hai?" to
        "Phone ki ijazat dein. Owner ke messages Chat page par (System) Official mein bhi milte hain.",
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
        "Coins app ke andar ki currency hain. Authority coins se aap ka level (1 se 16) tay hota hai. Coins Top-up, spin ya ghante mein ek ad se milte hain."
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
