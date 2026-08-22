package com.restaurant.pos.data.model

enum class PermissionCategory(val title: String, val icon: String, val description: String) {
    DASHBOARD("Dashboard", "📊", "Overview stats, summary cards & quick insights"),
    PRODUCTS("Products", "🍔", "Menu catalog, food items & pricing"),
    CATEGORIES("Categories", "📑", "Product categorization & grouping"),
    ORDERS("Orders / POS", "🛍️", "Order placement, billing & kitchen tickets"),
    PAYMENT("Payment", "💳", "Cash, card & digital payments"),
    REPORTS("Reports", "📈", "Financial, sales & expense reporting"),
    CUSTOMERS("Customers", "👥", "Customer profiles & order tracking"),
    STAFF_USERS("Staff & User Management", "🛡️", "User accounts, roles & permissions"),
    SETTINGS("Settings", "⚙️", "Restaurant info, printer & invoice setup")
}

enum class AppPermission(
    val key: String,
    val title: String,
    val description: String,
    val category: PermissionCategory
) {
    // Dashboard
    DASHBOARD_VIEW(
        key = "permission_dashboard_view",
        title = "View Dashboard",
        description = "Access dashboard stats, live revenue & overview metrics",
        category = PermissionCategory.DASHBOARD
    ),

    // Products
    PRODUCTS_VIEW(
        key = "permission_products_view",
        title = "View Products",
        description = "Browse menu items, prices & stock details",
        category = PermissionCategory.PRODUCTS
    ),
    PRODUCTS_ADD(
        key = "permission_products_add",
        title = "Add Product",
        description = "Create and register new menu items in the catalog",
        category = PermissionCategory.PRODUCTS
    ),
    PRODUCTS_EDIT(
        key = "permission_products_edit",
        title = "Edit Product",
        description = "Modify menu item price, name, stock & availability",
        category = PermissionCategory.PRODUCTS
    ),
    PRODUCTS_DELETE(
        key = "permission_products_delete",
        title = "Delete Product",
        description = "Delete menu items permanently from the system",
        category = PermissionCategory.PRODUCTS
    ),

    // Categories
    CATEGORIES_VIEW(
        key = "permission_categories_view",
        title = "View Categories",
        description = "View list of menu item categories",
        category = PermissionCategory.CATEGORIES
    ),
    CATEGORIES_ADD(
        key = "permission_categories_add",
        title = "Add Category",
        description = "Create new food & drink categories",
        category = PermissionCategory.CATEGORIES
    ),
    CATEGORIES_EDIT(
        key = "permission_categories_edit",
        title = "Edit Category",
        description = "Update category names, icons & ordering",
        category = PermissionCategory.CATEGORIES
    ),
    CATEGORIES_DELETE(
        key = "permission_categories_delete",
        title = "Delete Category",
        description = "Delete categories from the catalog",
        category = PermissionCategory.CATEGORIES
    ),

    // Orders / POS
    ORDERS_CREATE(
        key = "permission_orders_create",
        title = "Create Order",
        description = "Take new customer orders, select tables & add cart items",
        category = PermissionCategory.ORDERS
    ),
    ORDERS_EDIT(
        key = "permission_orders_edit",
        title = "Edit Order",
        description = "Modify items, quantities & notes in active orders",
        category = PermissionCategory.ORDERS
    ),
    ORDERS_CANCEL(
        key = "permission_orders_cancel",
        title = "Cancel Order",
        description = "Cancel or void placed customer orders",
        category = PermissionCategory.ORDERS
    ),
    ORDERS_VIEW(
        key = "permission_orders_view",
        title = "View Orders",
        description = "Browse active, completed & historic order lists",
        category = PermissionCategory.ORDERS
    ),

    // Payment
    PAYMENT_RECEIVE(
        key = "permission_payment_receive",
        title = "Receive Payment",
        description = "Collect cash, card, mobile & split payments",
        category = PermissionCategory.PAYMENT
    ),
    PAYMENT_VIEW(
        key = "permission_payment_view",
        title = "View Payment",
        description = "View payment breakdowns, receipts & transactions",
        category = PermissionCategory.PAYMENT
    ),
    PAYMENT_REFUND(
        key = "permission_payment_refund",
        title = "Refund/Cancel Payment",
        description = "Process refunds & cancel settled payments",
        category = PermissionCategory.PAYMENT
    ),

    // Reports
    REPORTS_VIEW(
        key = "permission_reports_view",
        title = "View Reports",
        description = "Access restaurant analytics & reporting section",
        category = PermissionCategory.REPORTS
    ),
    REPORTS_SALES(
        key = "permission_reports_sales",
        title = "Sales Report",
        description = "View daily/monthly sales, gross profit & top items",
        category = PermissionCategory.REPORTS
    ),
    REPORTS_EXPENSE(
        key = "permission_reports_expense",
        title = "Expense Report",
        description = "View and log restaurant operational expenses",
        category = PermissionCategory.REPORTS
    ),
    REPORTS_STAFF(
        key = "permission_reports_staff",
        title = "Staff Report",
        description = "View staff sales volume, shifts & performance",
        category = PermissionCategory.REPORTS
    ),

    // Customers
    CUSTOMERS_VIEW(
        key = "permission_customers_view",
        title = "View Customers",
        description = "Access customer list & purchase history",
        category = PermissionCategory.CUSTOMERS
    ),
    CUSTOMERS_ADD(
        key = "permission_customers_add",
        title = "Add Customer",
        description = "Register new customers & contact info",
        category = PermissionCategory.CUSTOMERS
    ),
    CUSTOMERS_EDIT(
        key = "permission_customers_edit",
        title = "Edit Customer",
        description = "Update customer contact, address & loyalty details",
        category = PermissionCategory.CUSTOMERS
    ),
    CUSTOMERS_DELETE(
        key = "permission_customers_delete",
        title = "Delete Customer",
        description = "Remove customer records from the system",
        category = PermissionCategory.CUSTOMERS
    ),

    // Staff & User Management
    USERS_VIEW(
        key = "permission_users_view",
        title = "View Users",
        description = "View list of staff members & account profiles",
        category = PermissionCategory.STAFF_USERS
    ),
    USERS_ADD(
        key = "permission_users_add",
        title = "Add User",
        description = "Create & register new staff user accounts",
        category = PermissionCategory.STAFF_USERS
    ),
    USERS_EDIT(
        key = "permission_users_edit",
        title = "Edit User",
        description = "Update staff profile names, status & credentials",
        category = PermissionCategory.STAFF_USERS
    ),
    USERS_DELETE(
        key = "permission_users_delete",
        title = "Delete User",
        description = "Delete staff accounts from the system",
        category = PermissionCategory.STAFF_USERS
    ),
    USERS_ROLES(
        key = "permission_users_roles",
        title = "Manage Roles",
        description = "Assign and change staff account roles",
        category = PermissionCategory.STAFF_USERS
    ),
    USERS_PERMISSIONS(
        key = "permission_users_permissions",
        title = "Manage Permissions",
        description = "Configure custom permissions for staff accounts",
        category = PermissionCategory.STAFF_USERS
    ),

    // Settings
    SETTINGS_VIEW(
        key = "permission_settings_view",
        title = "View Settings",
        description = "Browse restaurant info, invoice format & printer settings",
        category = PermissionCategory.SETTINGS
    ),
    SETTINGS_MODIFY(
        key = "permission_settings_modify",
        title = "Modify Settings",
        description = "Change restaurant business info, receipt layout & printer configs",
        category = PermissionCategory.SETTINGS
    );

    companion object {
        fun fromKey(key: String): AppPermission? {
            val normalized = key.trim().lowercase()
            return entries.firstOrNull {
                it.key.lowercase() == normalized ||
                it.name.lowercase() == normalized ||
                it.key.removePrefix("permission_").lowercase() == normalized.removePrefix("permission_")
            }
        }

        fun allKeys(): Set<String> = entries.map { it.key }.toSet()

        fun byCategory(): Map<PermissionCategory, List<AppPermission>> {
            return entries.groupBy { it.category }
        }
    }
}

enum class UserRole(
    val roleName: String,
    val displayName: String,
    val description: String,
    val icon: String,
    val defaultPermissions: Set<String>
) {
    ADMINISTRATOR(
        roleName = "Administrator",
        displayName = "Administrator",
        description = "Full root access to all POS operations, settings, staff and reports.",
        icon = "👑",
        defaultPermissions = AppPermission.allKeys()
    ),
    ADMIN(
        roleName = "Admin",
        displayName = "Admin",
        description = "Full root access to all POS operations, settings, staff and reports.",
        icon = "👑",
        defaultPermissions = AppPermission.allKeys()
    ),
    MANAGER(
        roleName = "Manager",
        displayName = "Manager",
        description = "Operational management for menu items, categories, reports, orders & staff view.",
        icon = "💼",
        defaultPermissions = setOf(
            AppPermission.DASHBOARD_VIEW.key,
            AppPermission.PRODUCTS_VIEW.key,
            AppPermission.PRODUCTS_ADD.key,
            AppPermission.PRODUCTS_EDIT.key,
            AppPermission.PRODUCTS_DELETE.key,
            AppPermission.CATEGORIES_VIEW.key,
            AppPermission.CATEGORIES_ADD.key,
            AppPermission.CATEGORIES_EDIT.key,
            AppPermission.CATEGORIES_DELETE.key,
            AppPermission.ORDERS_CREATE.key,
            AppPermission.ORDERS_EDIT.key,
            AppPermission.ORDERS_CANCEL.key,
            AppPermission.ORDERS_VIEW.key,
            AppPermission.PAYMENT_RECEIVE.key,
            AppPermission.PAYMENT_VIEW.key,
            AppPermission.PAYMENT_REFUND.key,
            AppPermission.REPORTS_VIEW.key,
            AppPermission.REPORTS_SALES.key,
            AppPermission.REPORTS_EXPENSE.key,
            AppPermission.REPORTS_STAFF.key,
            AppPermission.CUSTOMERS_VIEW.key,
            AppPermission.CUSTOMERS_ADD.key,
            AppPermission.CUSTOMERS_EDIT.key,
            AppPermission.CUSTOMERS_DELETE.key,
            AppPermission.USERS_VIEW.key,
            AppPermission.USERS_ADD.key,
            AppPermission.USERS_EDIT.key,
            AppPermission.SETTINGS_VIEW.key,
            AppPermission.SETTINGS_MODIFY.key
        )
    ),
    CASHIER(
        roleName = "Cashier",
        displayName = "Cashier",
        description = "POS counter cashier. Place orders, accept payments & manage customer records.",
        icon = "💵",
        defaultPermissions = setOf(
            AppPermission.DASHBOARD_VIEW.key,
            AppPermission.PRODUCTS_VIEW.key,
            AppPermission.CATEGORIES_VIEW.key,
            AppPermission.ORDERS_CREATE.key,
            AppPermission.ORDERS_VIEW.key,
            AppPermission.PAYMENT_RECEIVE.key,
            AppPermission.PAYMENT_VIEW.key,
            AppPermission.CUSTOMERS_VIEW.key,
            AppPermission.CUSTOMERS_ADD.key,
            AppPermission.CUSTOMERS_EDIT.key
        )
    ),
    STAFF(
        roleName = "Staff",
        displayName = "Staff",
        description = "Floor service & waiter staff. Create orders, view products & receive payment.",
        icon = "🧑‍🍳",
        defaultPermissions = setOf(
            AppPermission.DASHBOARD_VIEW.key,
            AppPermission.PRODUCTS_VIEW.key,
            AppPermission.CATEGORIES_VIEW.key,
            AppPermission.ORDERS_CREATE.key,
            AppPermission.ORDERS_VIEW.key,
            AppPermission.PAYMENT_RECEIVE.key,
            AppPermission.PAYMENT_VIEW.key,
            AppPermission.CUSTOMERS_VIEW.key
        )
    );

    companion object {
        fun fromRoleName(name: String): UserRole {
            val normalized = name.trim().lowercase()
            return when (normalized) {
                "administrator" -> ADMINISTRATOR
                "admin" -> ADMIN
                "manager" -> MANAGER
                "cashier" -> CASHIER
                else -> STAFF
            }
        }
    }
}
