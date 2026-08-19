package com.restaurant.pos.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.content.res.Configuration
import java.util.Locale
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.restaurant.pos.ui.screens.*
import com.restaurant.pos.ui.viewmodel.RestaurantViewModel

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val DASHBOARD = "dashboard"
    const val ORDERS = "orders"
    const val NEW_ORDER = "new_order"
    const val CATEGORY = "category"
    const val ADD_ITEM_CART = "add_item_cart"
    const val ORDER_SUMMARY = "order_summary"
    const val KITCHEN = "kitchen"
    const val ORDER_DETAILS = "order_details"
    const val REPORTS = "reports"
    const val MORE = "more"
    const val NOTIFICATIONS = "notifications"
    const val PRODUCTS_MENU = "products_menu"
    const val ORDER_HISTORY = "order_history"
    const val STAFF_USERS = "staff_users"
    const val STOCK_INVENTORY = "stock_inventory"
    const val DISCOUNT_OFFERS = "discount_offers"
    const val INVOICE_RECEIPT_SETTINGS = "invoice_receipt_settings"
    const val PRINTER_SETTINGS = "printer_settings"
    const val SALES_ANALYTICS = "sales_analytics"
    const val BUSINESS_SETTINGS = "business_settings"
    const val BACKUP_RESTORE = "backup_restore"
    const val TABLES = "tables"
    const val APP_UPDATE = "app_update"
}


@Composable
fun AppNavigation(
    viewModel: RestaurantViewModel = viewModel(),
    navController: NavHostController = rememberNavController()
) {
    val language by viewModel.appLanguage.collectAsState()
    val rootContext = LocalContext.current
    val hostActivity = (rootContext as? android.app.Activity) ?: rootContext.findActivity()
    val localizedContext = remember(language, rootContext) {
        getLocalizedContext(rootContext, language)
    }
    val activityResultRegistryOwner = androidx.activity.compose.LocalActivityResultRegistryOwner.current
    val locals = if (activityResultRegistryOwner != null) {
        arrayOf(
            LocalContext provides localizedContext,
            androidx.activity.compose.LocalActivityResultRegistryOwner provides activityResultRegistryOwner
        )
    } else {
        arrayOf(LocalContext provides localizedContext)
    }
    CompositionLocalProvider(*locals) {
        androidx.compose.runtime.key(language) {
            NavHost(
                navController = navController,
                startDestination = Routes.SPLASH
            ) {
        composable(Routes.SPLASH) {
            SplashScreen(
                viewModel = viewModel,
                onNavigateNext = { isLoggedIn ->
                    val destination = if (isLoggedIn) Routes.DASHBOARD else Routes.LOGIN
                    navController.navigate(destination) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.LOGIN) {
            val activity = hostActivity ?: (LocalContext.current as? android.app.Activity) ?: LocalContext.current.findActivity()
            LoginScreen(
                viewModel = viewModel,
                activity = activity,
                onLoginSuccess = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onBack = {
                    activity?.finish()
                }
            )
        }

        composable(Routes.DASHBOARD) {
            DashboardScreen(
                viewModel = viewModel,
                onNavigate = { route ->
                    handleBottomNav(navController, route)
                },
                onViewOrderDetails = { order ->
                    viewModel.setSelectedOrderDetails(order)
                    navController.navigate(Routes.ORDER_DETAILS)
                }
            )
        }

        composable(Routes.NEW_ORDER) {
            NewOrderScreen(
                viewModel = viewModel,
                onProceedToSummary = {
                    navController.navigate(Routes.ORDER_SUMMARY)
                },
                onAddMoreItems = {
                    navController.navigate(Routes.CATEGORY)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.CATEGORY) {
            CategoryScreen(
                viewModel = viewModel,
                onSelectItem = { item ->
                    viewModel.setSelectedMenuItem(item)
                    navController.navigate(Routes.ADD_ITEM_CART)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.ADD_ITEM_CART) {
            AddItemCartScreen(
                viewModel = viewModel,
                onViewCart = {
                    navController.navigate(Routes.NEW_ORDER)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.ORDER_SUMMARY) {
            OrderSummaryScreen(
                viewModel = viewModel,
                onOrderPlaced = {
                    navController.navigate(Routes.ORDER_DETAILS)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.ORDERS) {
            OrdersScreen(
                viewModel = viewModel,
                onNavigate = { route ->
                    handleBottomNav(navController, route)
                },
                onViewOrderDetails = { order ->
                    viewModel.setSelectedOrderDetails(order)
                    navController.navigate(Routes.ORDER_DETAILS)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.KITCHEN) {
            KitchenViewScreen(
                viewModel = viewModel,
                onNavigate = { route ->
                    handleBottomNav(navController, route)
                },
                onViewOrderDetails = { order ->
                    viewModel.setSelectedOrderDetails(order)
                    navController.navigate(Routes.ORDER_DETAILS)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.ORDER_DETAILS) {
            OrderDetailsScreen(
                viewModel = viewModel,
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.REPORTS) {
            ReportsScreen(
                viewModel = viewModel,
                onNavigate = { route ->
                    handleBottomNav(navController, route)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.MORE) {
            MoreScreen(
                viewModel = viewModel,
                onNavigate = { route ->
                    handleBottomNav(navController, route)
                },
                onOpenNotifications = {
                    navController.navigate(Routes.NOTIFICATIONS)
                },
                onOpenTablesManagement = {
                    navController.navigate(Routes.TABLES)
                },
                onOpenBusinessSettings = {
                    navController.navigate(Routes.BUSINESS_SETTINGS)
                },
                onOpenShopReport = {
                    navController.navigate(Routes.REPORTS)
                },
                onOpenSalesAnalytics = {
                    navController.navigate(Routes.SALES_ANALYTICS)
                },
                onOpenProductsMenu = {
                    navController.navigate(Routes.PRODUCTS_MENU)
                },
                onOpenOrderHistory = {
                    navController.navigate(Routes.ORDER_HISTORY)
                },
                onOpenStaffUsers = {
                    navController.navigate(Routes.STAFF_USERS)
                },
                onOpenStockInventory = {
                    navController.navigate(Routes.STOCK_INVENTORY)
                },
                onOpenDiscountOffers = {
                    navController.navigate(Routes.DISCOUNT_OFFERS)
                },
                onOpenInvoiceReceiptSettings = {
                    navController.navigate(Routes.INVOICE_RECEIPT_SETTINGS)
                },
                onOpenPrinterSettings = {
                    navController.navigate(Routes.PRINTER_SETTINGS)
                },
                onOpenBackupRestore = {
                    navController.navigate(Routes.BACKUP_RESTORE)
                },
                onOpenAppUpdate = {
                    navController.navigate(Routes.APP_UPDATE)
                },
                onLogout = {
                    viewModel.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.NOTIFICATIONS) {
            NotificationsScreen(
                viewModel = viewModel,
                onNavigate = { route ->
                    handleBottomNav(navController, route)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.BACKUP_RESTORE) {
            BackupRestoreScreen(
                viewModel = viewModel,
                onNavigate = { route ->
                    handleBottomNav(navController, route)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.BUSINESS_SETTINGS) {
            BusinessSettingsScreen(
                viewModel = viewModel,
                onNavigate = { route ->
                    if (route == Routes.DISCOUNT_OFFERS || route == "discount_offers") {
                        navController.navigate(Routes.DISCOUNT_OFFERS)
                    } else {
                        handleBottomNav(navController, route)
                    }
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.DISCOUNT_OFFERS) {
            DiscountOffersScreen(
                viewModel = viewModel,
                onNavigate = { route ->
                    handleBottomNav(navController, route)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.SALES_ANALYTICS) {
            SalesAnalyticsScreen(
                viewModel = viewModel,
                onBack = {
                    navController.popBackStack()
                }
            )
        }


        composable(Routes.PRODUCTS_MENU) {
            ProductsMenuScreen(
                viewModel = viewModel,
                onNavigate = { route ->
                    handleBottomNav(navController, route)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.ORDER_HISTORY) {
            OrderHistoryScreen(
                viewModel = viewModel,
                onNavigate = { route ->
                    handleBottomNav(navController, route)
                },
                onViewOrderDetails = { order ->
                    viewModel.setSelectedOrderDetails(order)
                    navController.navigate(Routes.ORDER_DETAILS)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.STAFF_USERS) {
            StaffUsersScreen(
                viewModel = viewModel,
                onNavigate = { route ->
                    handleBottomNav(navController, route)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.STOCK_INVENTORY) {
            StockInventoryScreen(
                viewModel = viewModel,
                onNavigate = { route ->
                    handleBottomNav(navController, route)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.INVOICE_RECEIPT_SETTINGS) {
            InvoiceReceiptSettingsScreen(
                viewModel = viewModel,
                onNavigate = { route ->
                    handleBottomNav(navController, route)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.PRINTER_SETTINGS) {
            PrinterSettingsScreen(
                viewModel = viewModel,
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.TABLES) {
            TablesScreen(
                viewModel = viewModel,
                onNavigateToOrderDetails = {
                    navController.navigate(Routes.ORDER_DETAILS)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.APP_UPDATE) {
            AppUpdateScreen(
                viewModel = viewModel,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
    }
    }
}

fun Context.findActivity(): android.app.Activity? {
    var ctx: Context? = this
    while (ctx != null) {
        if (ctx is android.app.Activity) return ctx
        if (ctx is android.content.ContextWrapper) {
            ctx = ctx.baseContext
        } else {
            break
        }
    }
    return null
}

fun getLocalizedContext(baseContext: Context, languageCode: String): Context {
    val locale = Locale(languageCode)
    Locale.setDefault(locale)
    val config = Configuration(baseContext.resources.configuration)
    config.setLocale(locale)
    val configContext = baseContext.createConfigurationContext(config)
    return object : android.content.ContextWrapper(configContext) {
        override fun getBaseContext(): Context = baseContext
    }
}


private fun handleBottomNav(navController: NavHostController, route: String) {
    val target = when (route) {
        "dashboard" -> Routes.DASHBOARD
        "order_list" -> Routes.ORDERS
        "new_order" -> Routes.NEW_ORDER
        "kitchen" -> Routes.KITCHEN
        "reports", "settings" -> Routes.REPORTS
        "more" -> Routes.MORE
        else -> route
    }
    if (navController.currentDestination?.route != target) {
        navController.navigate(target) {
            popUpTo(Routes.DASHBOARD) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
}
