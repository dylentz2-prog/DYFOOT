package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.MobileMoneyProvider
import com.example.data.model.OFFICIAL_MERCHANT_NUMBER
import com.example.data.model.PaymentStatus
import com.example.data.model.VipTier
import com.example.ui.components.SubScreenTopBar
import com.example.ui.i18n.dyfootStrings
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.InfoCyan
import com.example.ui.theme.InfoCyanContainer
import com.example.ui.theme.LiveRed
import com.example.ui.theme.LiveRedContainer
import com.example.ui.theme.MidnightBlack
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.MidnightCard
import com.example.ui.theme.MidnightCardAlt
import com.example.ui.theme.NeonLime
import com.example.ui.theme.NeonLimeContainer
import com.example.ui.theme.SlateText
import com.example.ui.theme.VipAmber
import com.example.ui.theme.VipAmberContainer
import com.example.ui.theme.WonEmerald
import com.example.ui.theme.WonEmeraldContainer
import com.example.viewmodel.DyfootViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun VipMembershipScreen(
    viewModel: DyfootViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val language by viewModel.appLanguage.collectAsStateWithLifecycle()
    val copy = remember(language) { dyfootStrings(language) }
    val membership by viewModel.membership.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val payments by viewModel.myPayments.collectAsStateWithLifecycle()
    val pendingPush by viewModel.pendingVipPush.collectAsStateWithLifecycle()

    var selectedTier by remember { mutableStateOf(VipTier.WEEKLY) }
    var selectedProvider by remember { mutableStateOf(MobileMoneyProvider.M_PESA) }
    var payerPhone by remember { mutableStateOf("") }
    var transactionRef by remember { mutableStateOf("") }

    val plans = remember(settings) {
        listOf(
            VipTier.DAILY to settings.vipDailyTzs,
            VipTier.WEEKLY to settings.vipWeeklyTzs,
            VipTier.MONTHLY to settings.vipMonthlyTzs,
            VipTier.ANNUAL to settings.vipAnnualTzs
        )
    }
    val selectedAmount = plans.firstOrNull { it.first == selectedTier }?.second ?: selectedTier.defaultTzs
    val hasGatewayKey = viewModel.hasMobileMoneyGatewayKey()
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }
    val merchantNum = settings.merchantNumber.ifBlank { OFFICIAL_MERCHANT_NUMBER }

    fun triggerPushSubscription() {
        viewModel.submitMobileMoneyPayment(
            tier = selectedTier,
            amountTzs = selectedAmount,
            provider = selectedProvider,
            payerPhone = payerPhone,
            transactionRef = transactionRef,
            onPushPromptReady = { ussd ->
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(ussd))))
                }
            }
        )
        transactionRef = ""
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        triggerPushSubscription()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBlack)
            .navigationBarsPadding()
    ) {
        SubScreenTopBar(title = "VIP Packages (Tanzania)", onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, VipAmber.copy(alpha = 0.6f)),
                    colors = CardDefaults.cardColors(containerColor = MidnightCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_vip_crown_banner),
                            contentDescription = "VIP Membership Banner",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(155.dp),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(155.dp)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(MidnightBlack.copy(alpha = 0.2f), MidnightBlack.copy(alpha = 0.94f))
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = VipAmber)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (membership?.isCurrentlyActive() == true) "VIP ACTIVE" else "DYFOOT VIP CLUB",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = VipAmber
                                )
                            }
                            if (membership?.isCurrentlyActive() == true) {
                                val exp = membership?.expiresAt?.toDate()?.let { dateFmt.format(it) } ?: ""
                                Text(
                                    text = "${membership?.planName} • Expires $exp",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NeonLime
                                )
                            } else {
                                Text(
                                    text = "From 1,000 TSH/day (ROCK) to 80,000 TSH/year (LEGEND). Unlock protected VIP slips & codes.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CrispWhite
                                )
                            }
                        }
                    }
                }
            }

            // VIP Perks Strip
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MidnightCard,
                    border = BorderStroke(1.dp, MidnightBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Bolt, contentDescription = null, tint = NeonLime, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("All Packages Unlock Full VIP Privileges", style = MaterialTheme.typography.titleSmall, color = CrispWhite)
                        }
                        listOf(
                            "Server-protected VIP betting slips & bookmaker booking codes",
                            "Correct score predictions & deep tactical breakdown",
                            "Combo Slip Builder support for multi-match accumulators"
                        ).forEach { perk ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = NeonLime, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(perk, style = MaterialTheme.typography.bodySmall, color = SlateText)
                            }
                        }
                    }
                }
            }

            // Gateway & Security Disclosure
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = InfoCyanContainer.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, InfoCyan.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Security, contentDescription = null, tint = InfoCyan, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (hasGatewayKey) "Mobile Money Gateway Configured" else "Manual / Pending Verification Flow",
                                style = MaterialTheme.typography.titleSmall,
                                color = CrispWhite
                            )
                            Text(
                                text = if (hasGatewayKey) {
                                    "Payments submitted are queued for merchant webhook verification against $merchantNum."
                                } else {
                                    "Automated STK push/webhook requires MOBILE_MONEY_MERCHANT_KEY in Secrets + server webhook. " +
                                        "Send payment to $merchantNum and submit your Transaction ID below; VIP activates after Owner verification."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateText
                            )
                        }
                    }
                }
            }

            // Step 1: 4-Package Selector (ROCK, STAR, PRO, LEGEND)
            item {
                Text("1. Choose Your VIP Package", style = MaterialTheme.typography.titleMedium, color = CrispWhite)
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    plans.forEach { (tier, tzs) ->
                        val selected = selectedTier == tier
                        val isLegend = tier == VipTier.ANNUAL
                        Card(
                            onClick = { selectedTier = tier },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    selected -> NeonLimeContainer
                                    isLegend -> VipAmberContainer.copy(alpha = 0.25f)
                                    else -> MidnightCard
                                }
                            ),
                            border = BorderStroke(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = when {
                                    selected -> NeonLime
                                    isLegend -> VipAmber.copy(alpha = 0.6f)
                                    else -> MidnightBorder
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("vip_plan_${tier.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = tier.label,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (selected) NeonLime else CrispWhite
                                        )
                                        if (tier.badgeText.isNotBlank()) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                color = if (isLegend) VipAmber else NeonLime,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = tier.badgeText,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MidnightBlack,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = tier.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SlateText
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${"%,d".format(tzs)} TSH",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (selected) NeonLime else VipAmber,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = when (tier) {
                                            VipTier.DAILY -> "per day"
                                            VipTier.WEEKLY -> "per week"
                                            VipTier.MONTHLY -> "per month"
                                            VipTier.ANNUAL -> "per year"
                                            else -> "${tier.durationDays}d"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SlateText
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Step 2: Official Merchant Number, Provider & USSD Dialer
            item {
                Text("2. Select Mobile Money Provider (Tanzania)", style = MaterialTheme.typography.titleMedium, color = CrispWhite)
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MidnightCardAlt),
                    border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Official DYFOOT Receiving Number", style = MaterialTheme.typography.labelSmall, color = SlateText)
                                Text(
                                    text = merchantNum,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = NeonLime
                                )
                                Text(selectedProvider.ussdHint, style = MaterialTheme.typography.bodySmall, color = CrispWhite)
                            }
                            IconButton(onClick = {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cb.setPrimaryClip(ClipData.newPlainText("DYFOOT Merchant", merchantNum))
                                Toast.makeText(context, "Copied $merchantNum", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy number", tint = NeonLime)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                val ussd = when (selectedProvider) {
                                    MobileMoneyProvider.M_PESA -> "*150*00#"
                                    MobileMoneyProvider.TIGO_PESA -> "*150*01#"
                                    MobileMoneyProvider.AIRTEL_MONEY -> "*150*60#"
                                    MobileMoneyProvider.HALOPESA -> "*150*88#"
                                }
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(ussd))))
                                }.onFailure {
                                    Toast.makeText(context, "Dial $ussd to pay $merchantNum", Toast.LENGTH_SHORT).show()
                                }
                            },
                            border = BorderStroke(1.dp, NeonLime),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Call, contentDescription = null, tint = NeonLime, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open ${selectedProvider.displayName} Dialer", color = NeonLime, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MobileMoneyProvider.entries.chunked(2).forEach { rowProviders ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowProviders.forEach { prov ->
                                val selected = selectedProvider == prov
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) NeonLimeContainer else MidnightCard,
                                    border = BorderStroke(1.dp, if (selected) NeonLime else MidnightBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { selectedProvider = prov }
                                ) {
                                    Text(
                                        text = prov.displayName,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (selected) NeonLime else CrispWhite,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Step 3: Enter Phone & Trigger Push Notification to Verify PIN (Auto-loads VIP on verification)
            item {
                Text("3. Enter Number & Send Push Verification", style = MaterialTheme.typography.titleMedium, color = CrispWhite)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = payerPhone,
                    onValueChange = { payerPhone = it },
                    label = { Text("Your Mobile Number (e.g. 07XXXXXXXX)") },
                    leadingIcon = { Icon(Icons.Filled.PhoneAndroid, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonLime,
                        unfocusedBorderColor = MidnightBorder,
                        focusedTextColor = CrispWhite,
                        unfocusedTextColor = CrispWhite,
                        focusedLabelColor = NeonLime,
                        unfocusedLabelColor = SlateText
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vip_payer_phone_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = transactionRef,
                    onValueChange = { transactionRef = it },
                    label = { Text("Provider Receipt Ref (Optional — auto-generated on Push)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonLime,
                        unfocusedBorderColor = MidnightBorder,
                        focusedTextColor = CrispWhite,
                        unfocusedTextColor = CrispWhite,
                        focusedLabelColor = NeonLime,
                        unfocusedLabelColor = SlateText
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vip_tx_ref_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = NeonLime, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Security notice: Enter your mobile-money PIN ONLY in your carrier's SIM/USSD prompt — never inside an app form.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateText
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            triggerPushSubscription()
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = MidnightBlack),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("vip_submit_payment_btn")
                ) {
                    Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${copy.pushVerifyBtn} • ${"%,d".format(selectedAmount)} TSH",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Live Auto-Activation Waiting Card when Push Notification has been dispatched
            if (pendingPush != null && membership?.isCurrentlyActive() != true) {
                item {
                    val pending = pendingPush!!
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = NeonLimeContainer),
                        border = BorderStroke(1.5.dp, NeonLime),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("vip_pending_push_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = NeonLime, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = copy.pushWaitingTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = NeonLime
                                )
                            }
                            Text(
                                text = "${pending.tier.label} (${"%,d".format(pending.amountTzs)} TSH) • Phone: ${pending.payerPhone} • Ref: ${pending.requestRef}\n${copy.pushWaitingBody}",
                                style = MaterialTheme.typography.bodySmall,
                                color = CrispWhite
                            )
                            LinearProgressIndicator(
                                color = NeonLime,
                                trackColor = MidnightCard,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Button(
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(pending.ussdCode)))
                                        )
                                    }.onFailure {
                                        Toast.makeText(context, "Dial ${pending.ussdCode} to confirm payment to $merchantNum", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = MidnightBlack),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Enter PIN via ${pending.provider.displayName} (${pending.ussdCode})", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Payment History
            item {
                Text("Payment History", style = MaterialTheme.typography.titleMedium, color = CrispWhite)
            }

            if (payments.isEmpty()) {
                item {
                    Text(
                        text = "No payments submitted yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateText
                    )
                }
            } else {
                items(payments, key = { it.id }) { p ->
                    val status = PaymentStatus.safeFrom(p.status)
                    val (bg, fg) = when (status) {
                        PaymentStatus.VERIFIED_PAID -> WonEmeraldContainer to WonEmerald
                        PaymentStatus.FAILED, PaymentStatus.REFUNDED -> LiveRedContainer to LiveRed
                        PaymentStatus.PENDING_VERIFICATION -> VipAmberContainer to VipAmber
                    }
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MidnightCard),
                        border = BorderStroke(1.dp, MidnightBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(p.planName, style = MaterialTheme.typography.titleSmall, color = CrispWhite)
                                Text(
                                    text = "${p.provider} • ${"%,d".format(p.amountTzs)} ${p.currency} • Ref: ${p.transactionRef}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateText
                                )
                            }
                            Surface(color = bg, shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    text = status.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = fg,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
