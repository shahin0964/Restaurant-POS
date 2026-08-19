package com.restaurant.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.restaurant.pos.data.db.MenuItemDao
import com.restaurant.pos.data.db.StaffFoodEntity
import com.restaurant.pos.data.db.UserDao
import com.restaurant.pos.data.repository.StaffFoodRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class StaffFoodViewModel(
    private val staffFoodRepo: StaffFoodRepository,
    menuItemDao: MenuItemDao,
    userDao: UserDao
) : ViewModel() {

    val allProducts = menuItemDao.getAllMenuItems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allStaff = userDao.getAllUsers().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedDate = MutableStateFlow(System.currentTimeMillis())
    val selectedDate: StateFlow<Long> = _selectedDate

    val staffFoodList = _selectedDate.flatMapLatest { date ->
        staffFoodRepo.getStaffFoodForDate(date)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSelectedDate(date: Long) {
        _selectedDate.value = date
    }

    fun addStaffFood(staffName: String, productName: String, quantity: Int, unitPrice: Double) {
        viewModelScope.launch {
            staffFoodRepo.addStaffFood(
                StaffFoodEntity(
                    staffName = staffName,
                    productName = productName,
                    quantity = quantity,
                    unitPrice = unitPrice,
                    totalPrice = quantity * unitPrice,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    fun deleteStaffFood(id: Long) {
        viewModelScope.launch {
            staffFoodRepo.deleteStaffFood(id)
        }
    }
}
