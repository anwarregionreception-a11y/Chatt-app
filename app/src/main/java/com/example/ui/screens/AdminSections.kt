@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.remote.Announcement
import com.example.data.remote.CopReport
import com.example.data.remote.Live
import com.example.data.remote.PaymentMethod
import com.example.data.remote.StaffApplication
import com.example.data.remote.StaffMember
import com.example.data.remote.TopUpOrder
import com.example.ui.RaabtaViewModel

private const val HOUR_MS = 60L * 60L * 1000L

/** Online lists used by the Owner panel; only the open tab is listened to. */
class AdminData(
    val reports: Live<List<CopReport>>,
    val staff: Live<List<StaffMember>>,
    val applications: Live<List<StaffApplication>>,
    val orders: Live<List<TopUpOrder>>,
    val methods: Live<List<PaymentMethod>>,
    val announcements: Live<List<Announcement>>
)

@Composable
fun rememberAdminData(tab: Int): AdminData {
    val viewModel = LocalAppViewModel.current
    return AdminData(
        reports = if (tab == 1) rememberLive("reports") { viewModel.community.observeReports() } else Live(),
        staff = if (tab == 2) rememberLive("staff") { viewModel.community.observeStaff() } else Live(),
        applications = if (tab == 2) {
            rememberLive("applications") { viewModel.community.observeApplications() }
        } else Live(),
        orders = if (tab == 3) rememberLive("orders") { viewModel.community.observePendingTopUps() } else Live(),
        methods = if (tab == 3) {
            rememberLive("methods") { viewModel.community.observePaymentMethods() }
        } else Live(),
        announcements = if (tab == 4) {
            rememberLive("announcements") { viewModel.community.observeAnnouncements(20) }
        } else Live()
    )
}

fun LazyListScope.reportSection(
    viewModel: RaabtaViewModel,
    data: AdminData,
    onOpenProfile: (String, String) -> Unit
) {
    liveStatus(data.reports, "Koi report nahi")
    items(data.reports.data.orEmpty(), key = { it.id }) { report ->
        SectionCard {
            Text(
                "${report.targetName}  (ID: ${report.targetUserId.ifBlank { report.targetUid }})",
                style = MaterialTheme.typography.titleMedium
            )
            Text(report.reason)
            Text(
                "Cop: ${report.copName} • ${formatDateTime(report.createdAt)}",
                style = MaterialTheme.typography.labelSmall
            )
            OutlinedButton(
                onClick = { onOpenProfile(report.targetUid, report.targetName) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Profile dekhein") }
            Text("Tamam chat se ban:", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("24 ghante" to 24L, "7 din" to 168L, "Hamesha" to 876_000L).forEach { (label, hours) ->
                    FilledTonalButton(onClick = {
                        viewModel.runAction("${report.targetName} ban ho gaya") { me ->
                            viewModel.community.ownerBan(report.targetUid, me, hours * HOUR_MS)
                        }
                    }) { Text(label) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    viewModel.runAction("Ban hata diya") { viewModel.community.unban(report.targetUid) }
                }) { Text("Unban") }
                TextButton(onClick = {
                    viewModel.runAction("Report band kar di") { viewModel.community.deleteReport(report.id) }
                }) { Text("Report band karein") }
            }
        }
    }
}

fun LazyListScope.staffSection(
    viewModel: RaabtaViewModel,
    data: AdminData,
    onOpenProfile: (String, String) -> Unit
) {
    item {
        Text(
            "Kisi ko appoint karne ke liye App Users, group ya search se us ki profile kholein, " +
                "ya neeche application approve karein.",
            style = MaterialTheme.typography.bodySmall
        )
    }
    item { Text("Applications", style = MaterialTheme.typography.titleMedium) }
    liveStatus(data.applications, "Koi application nahi")
    items(data.applications.data.orEmpty(), key = { "apply_${it.uid}" }) { form ->
        SectionCard {
            Text("${form.name} → ${form.role.title}", style = MaterialTheme.typography.titleSmall)
            if (form.userId.isNotBlank()) Text("ID: ${form.userId}")
            if (form.note.isNotBlank()) Text(form.note)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    viewModel.runAction("${form.name} appoint ho gaya") {
                        viewModel.community.appoint(form.uid, form.name, form.role)
                    }
                }) { Text("Appoint") }
                TextButton(onClick = {
                    viewModel.runAction("Application reject") {
                        viewModel.community.rejectApplication(form.uid)
                    }
                }) { Text("Reject") }
                TextButton(onClick = { onOpenProfile(form.uid, form.name) }) { Text("Profile") }
            }
        }
    }
    item { Text("Mojooda Staff", style = MaterialTheme.typography.titleMedium) }
    liveStatus(data.staff, "Abhi koi staff nahi")
    items(data.staff.data.orEmpty(), key = { "staff_${it.uid}" }) { person ->
        PersonRow(person.name, person.role.title, onClick = { onOpenProfile(person.uid, person.name) }) {
            TextButton(onClick = {
                viewModel.runAction("Staff se hata diya") { viewModel.community.dismiss(person.uid) }
            }) { Text("Hatayein") }
        }
    }
}

fun LazyListScope.topUpSection(viewModel: RaabtaViewModel, data: AdminData) {
    item { Text("Pending orders", style = MaterialTheme.typography.titleMedium) }
    liveStatus(data.orders, "Koi pending order nahi")
    items(data.orders.data.orEmpty(), key = { "order_${it.id}" }) { order ->
        SectionCard {
            Text("${order.name}: ${order.coins} coins", style = MaterialTheme.typography.titleSmall)
            Text("${order.amountPkr} PKR • ${order.method} • Ref: ${order.reference}")
            Text(formatDateTime(order.createdAt), style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    viewModel.runAction("Approve ho gaya, coins de diye") {
                        viewModel.community.reviewTopUp(order.id, true)
                    }
                }) { Text("Approve") }
                TextButton(onClick = {
                    viewModel.runAction("Order reject") { viewModel.community.reviewTopUp(order.id, false) }
                }) { Text("Reject") }
            }
        }
    }
    item { Text("Payment methods", style = MaterialTheme.typography.titleMedium) }
    item { AddPaymentMethod() }
    liveStatus(data.methods, "Abhi koi method nahi")
    items(data.methods.data.orEmpty(), key = { "method_${it.id}" }) { method ->
        SectionCard {
            Text(method.name, style = MaterialTheme.typography.titleSmall)
            Text("${method.account} ${method.holder}")
            TextButton(onClick = {
                viewModel.runAction("Method hata diya") {
                    viewModel.community.deletePaymentMethod(method.id)
                }
            }) { Text("Delete") }
        }
    }
}

@Composable
private fun AddPaymentMethod() {
    val viewModel = LocalAppViewModel.current
    var name by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var holder by remember { mutableStateOf("") }
    var instructions by remember { mutableStateOf("") }
    var minimum by remember { mutableStateOf("1") }
    SectionCard {
        AdminField("Method (maslan JazzCash, EasyPaisa, USDT)", name) { name = it }
        AdminField("Account number / wallet address", account) { account = it }
        AdminField("Account ka naam", holder) { holder = it }
        AdminField("Hidayat (optional)", instructions) { instructions = it }
        OutlinedTextField(
            value = minimum,
            onValueChange = { text -> minimum = text.filter { it.isDigit() }.take(6) },
            label = { Text("Kam az kam coins") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                val method = PaymentMethod(
                    "", name, account, holder, instructions, minimum.toLongOrNull() ?: 1L
                )
                viewModel.runAction("Payment method add ho gaya") {
                    viewModel.community.addPaymentMethod(method).onSuccess {
                        name = ""
                        account = ""
                        holder = ""
                        instructions = ""
                    }
                }
            },
            enabled = name.isNotBlank() && account.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Add Method") }
    }
}

@Composable
private fun AdminField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth()
    )
}

fun LazyListScope.messageSection(viewModel: RaabtaViewModel, data: AdminData) {
    item { ComposeAnnouncement() }
    item {
        Text(
            "Yeh message un phones par notification ban kar aata hai jin par app khuli ya peeche chal rahi ho. " +
                "Baqi users ko app kholne par milta hai. Band app par foran notification ke liye Firebase " +
                "Console → Messaging se bhejein.",
            style = MaterialTheme.typography.bodySmall
        )
    }
    liveStatus(data.announcements, "Abhi koi message nahi")
    items(data.announcements.data.orEmpty(), key = { "note_${it.id}" }) { note ->
        SectionCard {
            Text(note.title, style = MaterialTheme.typography.titleSmall)
            Text(note.text)
            TextButton(onClick = {
                viewModel.runAction("Message delete") { viewModel.community.deleteAnnouncement(note.id) }
            }) { Text("Delete") }
        }
    }
}

@Composable
private fun ComposeAnnouncement() {
    val viewModel = LocalAppViewModel.current
    var title by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    SectionCard {
        AdminField("Title", title) { title = it.take(80) }
        AdminField("Message", text) { text = it.take(1000) }
        Button(
            onClick = {
                val a = title
                val b = text
                viewModel.runAction("Sab users ko message bhej diya") {
                    viewModel.community.announce(a, b).onSuccess {
                        title = ""
                        text = ""
                    }
                }
            },
            enabled = title.isNotBlank() && text.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Sab ko bhejein") }
    }
}
