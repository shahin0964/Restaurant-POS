package com.restaurant.pos.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.restaurant.pos.data.db.ExpenseEntity
import com.restaurant.pos.data.db.OrderWithItems
import com.restaurant.pos.ui.components.BottomNavBar
import com.restaurant.pos.ui.theme.*
import com.restaurant.pos.ui.viewmodel.RestaurantViewModel
import java.text.SimpleDateFormat
import java.util.*

enum class ReportMode { DAILY, MONTHLY, YEARLY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: RestaurantViewModel,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit
) {
    val allOrders by viewModel.allOrders.collectAsState()
    val allExpenses by viewModel.allExpenses.collectAsState()
    val openingCash by viewModel.openingCash.collectAsState()

    var selectedMode by remember { mutableStateOf(ReportMode.DAILY) }
    var selectedCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var showOpeningCashDialog by remember { mutableStateOf(false) }

    // Date Bounds Calculation for Selected Mode
    val (startTime, endTime) = remember(selectedMode, selectedCalendar.timeInMillis) {
        val cal = selectedCalendar.clone() as Calendar
        when (selectedMode) {
            ReportMode.DAILY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val end = cal.timeInMillis
                Pair(start, end)
            }
            ReportMode.MONTHLY -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val end = cal.timeInMillis
                Pair(start, end)
            }
            ReportMode.YEARLY -> {
                cal.set(Calendar.MONTH, Calendar.JANUARY)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.set(Calendar.MONTH, Calendar.DECEMBER)
                cal.set(Calendar.DAY_OF_MONTH, 31)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val end = cal.timeInMillis
                Pair(start, end)
            }
        }
    }

    // Filter valid paid orders and expenses in period
    val validOrdersInPeriod = remember(allOrders, startTime, endTime) {
        allOrders.filter {
            it.order.isPaid && it.order.status != "Cancelled" && it.order.timestamp in startTime..endTime
        }
    }

    val expensesInPeriod = remember(allExpenses, startTime, endTime) {
        allExpenses.filter {
            it.timestamp in startTime..endTime
        }
    }

    val totalSales = remember(validOrdersInPeriod) {
        validOrdersInPeriod.sumOf { it.order.total }
    }

    val totalCogs = remember(validOrdersInPeriod) {
        validOrdersInPeriod.sumOf { orderWithItems ->
            orderWithItems.items.sumOf { item ->
                item.quantity * item.costPriceAtSale
            }
        }
    }

    val totalOperatingExpense = remember(expensesInPeriod) {
        expensesInPeriod.filter { it.expenseType.equals("OPERATING", ignoreCase = true) }.sumOf { it.amount }
    }

    val totalExpense = remember(expensesInPeriod) {
        expensesInPeriod.sumOf { it.amount }
    }

    val totalCashIn = remember(validOrdersInPeriod) {
        validOrdersInPeriod.filter { it.order.paymentMethod.equals("Cash", ignoreCase = true) }
            .sumOf { it.order.total }
    }

    val totalCashOut = remember(expensesInPeriod) {
        expensesInPeriod.filter { it.paymentMethod.equals("Cash", ignoreCase = true) }.sumOf { it.amount }
    }

    // Global All-Time Available Cash Balance
    val allTimePaidCashSales = remember(allOrders) {
        allOrders.filter { it.order.isPaid && it.order.status != "Cancelled" && it.order.paymentMethod.equals("Cash", ignoreCase = true) }
            .sumOf { it.order.total }
    }
    val allTimeCashExpenses = remember(allExpenses) {
        allExpenses.filter { it.paymentMethod.equals("Cash", ignoreCase = true) }.sumOf { it.amount }
    }
    val currentCash = openingCash + allTimePaidCashSales - allTimeCashExpenses

    val grossProfit = totalSales - totalCogs
    val netResult = grossProfit - totalOperatingExpense
    val netRemaining = netResult
    val loss = maxOf(0.0, -netResult)
    val profit = maxOf(0.0, netResult)

    // Formatting Strings
    val dateDisplayString = remember(selectedMode, selectedCalendar.timeInMillis) {
        val date = selectedCalendar.time
        when (selectedMode) {
            ReportMode.DAILY -> SimpleDateFormat("dd MMMM yyyy", Locale.US).format(date)
            ReportMode.MONTHLY -> SimpleDateFormat("MMMM yyyy", Locale.US).format(date)
            ReportMode.YEARLY -> SimpleDateFormat("yyyy", Locale.US).format(date)
        }
    }

    // Daily breakdowns for selected month
    val dailyBreakdowns = remember(allOrders, allExpenses, selectedCalendar.timeInMillis) {
        val list = mutableListOf<DailyBreakdownData>()
        val cal = selectedCalendar.clone() as Calendar
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        for (day in 1..maxDays) {
            val dayCal = cal.clone() as Calendar
            dayCal.set(Calendar.DAY_OF_MONTH, day)
            dayCal.set(Calendar.HOUR_OF_DAY, 0)
            dayCal.set(Calendar.MINUTE, 0)
            dayCal.set(Calendar.SECOND, 0)
            dayCal.set(Calendar.MILLISECOND, 0)
            val dayStart = dayCal.timeInMillis

            dayCal.set(Calendar.HOUR_OF_DAY, 23)
            dayCal.set(Calendar.MINUTE, 59)
            dayCal.set(Calendar.SECOND, 59)
            dayCal.set(Calendar.MILLISECOND, 999)
            val dayEnd = dayCal.timeInMillis

            val dayOrders = allOrders.filter {
                it.order.isPaid && it.order.status != "Cancelled" && it.order.timestamp in dayStart..dayEnd
            }
            val dayExpenses = allExpenses.filter {
                it.timestamp in dayStart..dayEnd
            }

            val salesSum = dayOrders.sumOf { it.order.total }
            val operatingExpenseSum = dayExpenses.filter { it.expenseType.equals("OPERATING", ignoreCase = true) }.sumOf { it.amount }
            val cogsSum = dayOrders.sumOf { order -> order.items.sumOf { it.quantity * it.costPriceAtSale } }
            val net = salesSum - cogsSum - operatingExpenseSum

            if (salesSum > 0 || operatingExpenseSum > 0 || cogsSum > 0) {
                val dayLabel = SimpleDateFormat("dd MMM (EEE)", Locale.US).format(dayCal.time)
                list.add(DailyBreakdownData(dayLabel, salesSum, operatingExpenseSum, net))
            }
        }
        list
    }

    // Monthly breakdowns for selected year
    val monthlyBreakdowns = remember(allOrders, allExpenses, selectedCalendar.timeInMillis) {
        val list = mutableListOf<DailyBreakdownData>()
        val cal = selectedCalendar.clone() as Calendar

        for (month in 0..11) {
            val mCal = cal.clone() as Calendar
            mCal.set(Calendar.MONTH, month)
            mCal.set(Calendar.DAY_OF_MONTH, 1)
            mCal.set(Calendar.HOUR_OF_DAY, 0)
            mCal.set(Calendar.MINUTE, 0)
            mCal.set(Calendar.SECOND, 0)
            mCal.set(Calendar.MILLISECOND, 0)
            val mStart = mCal.timeInMillis

            mCal.set(Calendar.DAY_OF_MONTH, mCal.getActualMaximum(Calendar.DAY_OF_MONTH))
            mCal.set(Calendar.HOUR_OF_DAY, 23)
            mCal.set(Calendar.MINUTE, 59)
            mCal.set(Calendar.SECOND, 59)
            mCal.set(Calendar.MILLISECOND, 999)
            val mEnd = mCal.timeInMillis

            val mOrders = allOrders.filter {
                it.order.isPaid && it.order.status != "Cancelled" && it.order.timestamp in mStart..mEnd
            }
            val mExpenses = allExpenses.filter {
                it.timestamp in mStart..mEnd
            }

            val salesSum = mOrders.sumOf { it.order.total }
            val operatingExpenseSum = mExpenses.filter { it.expenseType.equals("OPERATING", ignoreCase = true) }.sumOf { it.amount }
            val cogsSum = mOrders.sumOf { order -> order.items.sumOf { it.quantity * it.costPriceAtSale } }
            val net = salesSum - cogsSum - operatingExpenseSum

            val monthLabel = SimpleDateFormat("MMMM", Locale.US).format(mCal.time)
            list.add(DailyBreakdownData(monthLabel, salesSum, operatingExpenseSum, net))
        }
        list
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBackground)
                    .statusBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("reports_back_btn")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SHOP REPORT",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        bottomBar = {
            BottomNavBar(currentRoute = "reports", onNavigate = onNavigate)
        },
        containerColor = DarkBackground,
        modifier = Modifier.testTag("shop_report_screen")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            // 1. Report Mode Switcher [ Daily ] [ Monthly ] [ Yearly ]
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurface)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ReportMode.entries.forEach { mode ->
                        val isSelected = selectedMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) CurrencyGold else Color.Transparent)
                                .clickable { selectedMode = mode }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                                color = if (isSelected) Color.Black else TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // 2. Date / Calendar Selection Row
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = {
                                val cal = selectedCalendar.clone() as Calendar
                                when (selectedMode) {
                                    ReportMode.DAILY -> cal.add(Calendar.DAY_OF_MONTH, -1)
                                    ReportMode.MONTHLY -> cal.add(Calendar.MONTH, -1)
                                    ReportMode.YEARLY -> cal.add(Calendar.YEAR, -1)
                                }
                                selectedCalendar = cal
                            }
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous", tint = CurrencyGold)
                        }

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showDatePicker = true }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "Calendar",
                                tint = CurrencyGold,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = dateDisplayString,
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(
                            onClick = {
                                val cal = selectedCalendar.clone() as Calendar
                                when (selectedMode) {
                                    ReportMode.DAILY -> cal.add(Calendar.DAY_OF_MONTH, 1)
                                    ReportMode.MONTHLY -> cal.add(Calendar.MONTH, 1)
                                    ReportMode.YEARLY -> cal.add(Calendar.YEAR, 1)
                                }
                                selectedCalendar = cal
                            }
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next", tint = CurrencyGold)
                        }
                    }
                }
            }

            // 3. Financial Summary Cards (2x2 Grid)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FinancialMetricCard(
                            title = "Total Sales",
                            subtitle = "মোট বিক্রি",
                            amount = totalSales,
                            icon = Icons.Default.PointOfSale,
                            accentColor = StatusReady,
                            modifier = Modifier.weight(1f)
                        )
                        FinancialMetricCard(
                            title = "Total Expense",
                            subtitle = "মোট খরচ",
                            amount = totalExpense,
                            icon = Icons.Default.MoneyOff,
                            accentColor = Color(0xFFFF5252),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FinancialMetricCard(
                            title = "Current Cash",
                            subtitle = "ক্যাশ স্থিতি",
                            amount = currentCash,
                            icon = Icons.Default.AccountBalanceWallet,
                            accentColor = CurrencyGold,
                            modifier = Modifier.weight(1f)
                        )
                        FinancialMetricCard(
                            title = "Net Remaining",
                            subtitle = "অবশিষ্ট নিট লাভ",
                            amount = netRemaining,
                            icon = Icons.Default.Savings,
                            accentColor = if (netRemaining >= 0) StatusReady else Color(0xFFFF5252),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FinancialMetricCard(
                            title = "Loss",
                            subtitle = "মোট ক্ষতি",
                            amount = loss,
                            icon = Icons.AutoMirrored.Filled.TrendingDown,
                            accentColor = if (loss > 0) Color(0xFFFF5252) else TextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        FinancialMetricCard(
                            title = "Profit",
                            subtitle = "মোট লাভ",
                            amount = profit,
                            icon = Icons.AutoMirrored.Filled.TrendingUp,
                            accentColor = if (profit > 0) StatusReady else TextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 4. Cash Balance Section (Opening Cash + Cash In - Cash Out = Current Cash)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CurrencyGold.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccountBalanceWallet,
                                        contentDescription = "Cash Balance",
                                        tint = CurrencyGold,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Cash Balance",
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "হাতে নগদ ক্যাশ ব্যালেন্স",
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = { showOpeningCashDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CurrencyGold),
                                border = BorderStroke(1.dp, CurrencyGold.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (openingCash > 0) "Edit Cash" else "Set Cash",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        HorizontalDivider(color = BorderOutline, thickness = 1.dp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Opening Cash (প্রারম্ভিক নগদ)",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "৳ ${String.format(Locale.US, "%,.2f", openingCash)}",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Total Cash In (মোট ক্যাশ বিক্রি)",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "+ ৳ ${String.format(Locale.US, "%,.2f", totalCashIn)}",
                                color = StatusReady,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Total Cash Out (মোট ক্যাশ খরচ)",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "- ৳ ${String.format(Locale.US, "%,.2f", totalCashOut)}",
                                color = Color(0xFFFF5252),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        HorizontalDivider(color = BorderOutline, thickness = 1.dp)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkSurfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Current Cash",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "বর্তমান হাতে নগদ",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                            Text(
                                text = "৳ ${String.format(Locale.US, "%,.2f", currentCash)}",
                                color = CurrencyGold,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 5. Mode Specific Content / Breakdown
            when (selectedMode) {
                ReportMode.DAILY -> {
                    // Daily View: Add Expense Option & Transactions / Expenses Lists
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Daily Details",
                        color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Button(
                                onClick = { showAddExpenseDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = CurrencyGold),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Expense", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (validOrdersInPeriod.isEmpty() && expensesInPeriod.isEmpty()) {
                        item {
                            EmptyStateCard("No transaction found for this date.")
                        }
                    } else {
                        // Transactions Section
                        if (validOrdersInPeriod.isNotEmpty()) {
                            item(key = "hdr_sales_trans", contentType = "header") {
                                Text("Sales Transactions (${validOrdersInPeriod.size})", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                            items(
                                items = validOrdersInPeriod,
                                key = { it.order.id },
                                contentType = { "order_report_row" }
                            ) { order ->
                                OrderReportRow(order)
                            }
                        }

                        // Expenses Section
                        if (expensesInPeriod.isNotEmpty()) {
                            item(key = "hdr_expenses", contentType = "header") {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Expenses (${expensesInPeriod.size})", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                            items(
                                items = expensesInPeriod,
                                key = { it.id },
                                contentType = { "expense_report_row" }
                            ) { expense ->
                                ExpenseReportRow(expense, onDelete = { viewModel.deleteExpense(expense) })
                            }
                        }
                    }
                }

                ReportMode.MONTHLY -> {
                    // Monthly Breakdown Date-Wise
                    item {
                        Text(
                            text = "Daily Breakdown for $dateDisplayString",
                        color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (dailyBreakdowns.isEmpty()) {
                        item(key = "empty_monthly", contentType = "empty_card") {
                            EmptyStateCard("No transaction found for this month.")
                        }
                    } else {
                        items(
                            items = dailyBreakdowns,
                            key = { it.label },
                            contentType = { "breakdown_row" }
                        ) { breakdown ->
                            BreakdownRow(breakdown.label, breakdown.sales, breakdown.expense, breakdown.net)
                        }
                    }
                }

                ReportMode.YEARLY -> {
                    // Yearly Breakdown Month-Wise
                    item {
                        Text(
                            text = "Monthly Breakdown for $dateDisplayString",
                        color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (monthlyBreakdowns.all { it.sales == 0.0 && it.expense == 0.0 }) {
                        item(key = "empty_yearly", contentType = "empty_card") {
                            EmptyStateCard("No transaction found for this year.")
                        }
                    } else {
                        items(
                            items = monthlyBreakdowns,
                            key = { it.label },
                            contentType = { "breakdown_row" }
                        ) { breakdown ->
                            BreakdownRow(breakdown.label, breakdown.sales, breakdown.expense, breakdown.net)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
        }
    }

    // Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedCalendar.timeInMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val cal = Calendar.getInstance()
                            cal.timeInMillis = millis
                            selectedCalendar = cal
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK", color = CurrencyGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            colors = DatePickerDefaults.colors(
                containerColor = DarkSurface
            )
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = DarkSurface,
                    titleContentColor = TextPrimary,
                    headlineContentColor = CurrencyGold,
                    weekdayContentColor = TextSecondary,
                    subheadContentColor = TextSecondary,
                    yearContentColor = TextPrimary,
                    currentYearContentColor = CurrencyGold,
                    selectedYearContentColor = Color.Black,
                    selectedYearContainerColor = CurrencyGold,
                    dayContentColor = TextPrimary,
                    disabledDayContentColor = TextMuted,
                    selectedDayContentColor = Color.Black,
                    selectedDayContainerColor = CurrencyGold,
                    todayContentColor = CurrencyGold,
                    todayDateBorderColor = CurrencyGold
                )
            )
        }
    }

    // Add Expense Dialog
    if (showAddExpenseDialog) {
        AddExpenseDialog(
            onDismiss = { showAddExpenseDialog = false },
            onSave = { title, amount, category, note, paymentMethod, expenseType ->
                viewModel.addExpense(title, amount, category, note, paymentMethod, expenseType)
                showAddExpenseDialog = false
            }
        )
    }

    // Set / Edit Opening Cash Dialog
    if (showOpeningCashDialog) {
        SetOpeningCashDialog(
            currentOpeningCash = openingCash,
            onDismiss = { showOpeningCashDialog = false },
            onSave = { newAmount ->
                viewModel.setOpeningCash(newAmount)
                showOpeningCashDialog = false
            }
        )
    }
}

@Composable
fun FinancialMetricCard(
    title: String,
    subtitle: String,
    amount: Double,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "৳ ${String.format(Locale.US, "%,.2f", amount)}",
                color = TextPrimary,
                        fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(text = subtitle, color = TextMuted, fontSize = 10.sp)
        }
    }
}

@Composable
fun OrderReportRow(orderWithItems: OrderWithItems) {
    val order = orderWithItems.order
    val timeStr = remember(order.timestamp) {
        SimpleDateFormat("hh:mm a", Locale.US).format(Date(order.timestamp))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = order.orderNumber, color = CurrencyGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = order.orderType, color = TextSecondary, fontSize = 12.sp)
                }
                Text(text = "${order.customerName} • ${order.paymentMethod} • $timeStr", color = TextMuted, fontSize = 11.sp)
            }

            Text(
                text = "৳ ${String.format(Locale.US, "%,.2f", order.total)}",
                color = StatusReady,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ExpenseReportRow(expense: ExpenseEntity, onDelete: () -> Unit) {
    val timeStr = remember(expense.timestamp) {
        SimpleDateFormat("hh:mm a", Locale.US).format(Date(expense.timestamp))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = expense.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(text = "${expense.category} • $timeStr", color = TextMuted, fontSize = 11.sp)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "৳ ${String.format(Locale.US, "%,.2f", expense.amount)}",
                    color = Color(0xFFFF5252),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun BreakdownRow(label: String, sales: Double, expense: Double, net: Double) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = label, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Sales", color = TextMuted, fontSize = 10.sp)
                    Text("৳ ${String.format(Locale.US, "%,.0f", sales)}", color = StatusReady, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Expense", color = TextMuted, fontSize = 10.sp)
                    Text("৳ ${String.format(Locale.US, "%,.0f", expense)}", color = Color(0xFFFF5252), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Net", color = TextMuted, fontSize = 10.sp)
                    Text(
                        "৳ ${String.format(Locale.US, "%,.0f", net)}",
                        color = if (net >= 0) CurrencyGold else Color(0xFFFF5252),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyStateCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message,
                color = TextMuted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun AddExpenseDialog(
    onDismiss: () -> Unit,
    onSave: (String, Double, String, String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Supplies") }
    var note by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("Cash") }
    var expenseType by remember { mutableStateOf("OPERATING") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val categories = listOf("Supplies", "Utilities", "Salary", "Maintenance", "Rent", "Other")
    val paymentMethods = listOf("Cash", "Bank", "Card", "bKash", "Other")
    val expenseTypes = listOf("OPERATING" to "Operating Expense", "INVENTORY_PURCHASE" to "Inventory Purchase")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Expense", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Expense Title (e.g., Vegetables)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CurrencyGold,
                        unfocusedBorderColor = TextMuted,
                        focusedLabelColor = CurrencyGold,
                        unfocusedLabelColor = TextMuted,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Amount (৳)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CurrencyGold,
                        unfocusedBorderColor = TextMuted,
                        focusedLabelColor = CurrencyGold,
                        unfocusedLabelColor = TextMuted,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Expense Type", color = TextSecondary, fontSize = 12.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    expenseTypes.forEach { (typeKey, typeLabel) ->
                        FilterChip(
                            selected = expenseType == typeKey,
                            onClick = { expenseType = typeKey },
                            label = { Text(typeLabel, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CurrencyGold,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                }

                Text("Category", color = TextSecondary, fontSize = 12.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.take(3).forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CurrencyGold,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.drop(3).forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CurrencyGold,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                }

                Text("Payment Method", color = TextSecondary, fontSize = 12.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    paymentMethods.forEach { pm ->
                        FilterChip(
                            selected = paymentMethod == pm,
                            onClick = { paymentMethod = pm },
                            label = { Text(pm, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CurrencyGold,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                }

                if (errorMessage != null) {
                    Text(errorMessage!!, color = Color(0xFFFF5252), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountStr.toDoubleOrNull()
                    if (title.isBlank()) {
                        errorMessage = "Please enter an expense title."
                    } else if (amt == null || amt <= 0) {
                        errorMessage = "Please enter a valid amount."
                    } else {
                        onSave(title, amt, category, note, paymentMethod, expenseType)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CurrencyGold)
            ) {
                Text("Save Expense", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = DarkSurface
    )
}

data class DailyBreakdownData(
    val label: String,
    val sales: Double,
    val expense: Double,
    val net: Double
)

@Composable
fun SetOpeningCashDialog(
    currentOpeningCash: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var amountStr by remember {
        mutableStateOf(
            if (currentOpeningCash > 0) {
                if (currentOpeningCash % 1.0 == 0.0) {
                    currentOpeningCash.toLong().toString()
                } else {
                    String.format(Locale.US, "%.2f", currentOpeningCash)
                }
            } else ""
        )
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Opening Cash / Initial Cash",
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "দোকানে বর্তমানে হাতে থাকা প্রারম্ভিক ক্যাশ amount সেট করুন। এটি cash হিসাবের starting balance হিসেবে ব্যবহৃত হবে।",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = {
                        amountStr = it
                        errorMessage = null
                    },
                    label = { Text("Opening Cash (৳)") },
                    placeholder = { Text("e.g. 50000") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CurrencyGold,
                        unfocusedBorderColor = BorderOutline,
                        focusedLabelColor = CurrencyGold,
                        unfocusedLabelColor = TextMuted,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("opening_cash_input")
                )
                if (errorMessage != null) {
                    Text(errorMessage!!, color = Color(0xFFFF5252), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountStr.toDoubleOrNull()
                    if (amt == null || amt < 0) {
                        errorMessage = "Please enter a valid cash amount (≥ 0)."
                    } else {
                        onSave(amt)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CurrencyGold),
                modifier = Modifier.testTag("save_opening_cash_btn")
            ) {
                Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = DarkSurface
    )
}
