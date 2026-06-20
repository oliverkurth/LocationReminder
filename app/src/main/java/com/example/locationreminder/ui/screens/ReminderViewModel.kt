package com.example.locationreminder.ui.screens

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.locationreminder.data.AppDatabase
import com.example.locationreminder.data.LocationEntity
import com.example.locationreminder.data.ReminderEntity
import com.example.locationreminder.repository.AppRepository
import com.example.locationreminder.workers.ReminderWorker
import com.example.locationreminder.workers.AlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

class ReminderViewModel(
    private val context: Context,
    private val repository: AppRepository,
    private val workManager: WorkManager
) : ViewModel() {

    private val _currentReminder = MutableStateFlow<ReminderEntity?>(null)
    val currentReminder: StateFlow<ReminderEntity?> = _currentReminder.asStateFlow()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description.asStateFlow()

    private val _selectedLocationId = MutableStateFlow<Long?>(null)
    val selectedLocationId: StateFlow<Long?> = _selectedLocationId.asStateFlow()

    private val _timeOfDay = MutableStateFlow<String?>(null)
    val timeOfDay: StateFlow<String?> = _timeOfDay.asStateFlow()

    private val _specificDate = MutableStateFlow<Long?>(null)
    val specificDate: StateFlow<Long?> = _specificDate.asStateFlow()

    private val _isRecurring = MutableStateFlow(false)
    val isRecurring: StateFlow<Boolean> = _isRecurring.asStateFlow()

    private val _recurringDays = MutableStateFlow<List<Int>>(listOf(
        Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
    ))
    val recurringDays: StateFlow<List<Int>> = _recurringDays.asStateFlow()

    private val _isActive = MutableStateFlow(true)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _allLocations = MutableStateFlow<List<LocationEntity>>(emptyList())
    val allLocations: StateFlow<List<LocationEntity>> = _allLocations.asStateFlow()

    private val _allReminders = MutableStateFlow<List<ReminderEntity>>(emptyList())
    val allReminders: StateFlow<List<ReminderEntity>> = _allReminders.asStateFlow()

    init {
        viewModelScope.launch {
            repository.allLocations.collect {
                _allLocations.value = it
            }
        }
        viewModelScope.launch {
            repository.allReminders.collect {
                _allReminders.value = it
            }
        }
    }

    fun loadReminder(id: Long) {
        if (id != -1L) {
            viewModelScope.launch {
                val reminder = repository.getReminderById(id)
                if (reminder != null) {
                    _currentReminder.value = reminder
                    _title.value = reminder.title
                    _description.value = reminder.description
                    _selectedLocationId.value = reminder.locationId
                    _timeOfDay.value = reminder.timeOfDay
                    _specificDate.value = reminder.specificDate
                    _isRecurring.value = reminder.isRecurring
                    _recurringDays.value = reminder.recurringDays ?: listOf(
                        Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
                    )
                    _isActive.value = reminder.isActive
                }
            }
        } else {
            _currentReminder.value = null
            _title.value = ""
            _description.value = ""
            _selectedLocationId.value = null
            _timeOfDay.value = null
            _specificDate.value = null
            _isRecurring.value = false
            _recurringDays.value = listOf(
                Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
            )
            _isActive.value = true
        }
    }

    fun updateTitle(t: String) { _title.value = t }
    fun updateDescription(d: String) { _description.value = d }
    fun updateLocation(locId: Long?) { _selectedLocationId.value = locId }
    fun updateTimeOfDay(time: String?) { _timeOfDay.value = time }
    fun updateSpecificDate(date: Long?) { _specificDate.value = date }
    fun updateIsRecurring(recurring: Boolean) { _isRecurring.value = recurring }
    fun toggleRecurringDay(day: Int) {
        val current = _recurringDays.value.toMutableList()
        if (current.contains(day)) {
            current.remove(day)
        } else {
            current.add(day)
        }
        _recurringDays.value = current
    }
    fun updateIsActive(active: Boolean) { _isActive.value = active }

    fun resetTriggerState() {
        _currentReminder.value?.let { reminder ->
            viewModelScope.launch {
                val updated = reminder.copy(lastTriggeredTimestamp = null)
                repository.updateReminder(updated)
                _currentReminder.value = updated
            }
        }
    }

    fun toggleReminderActive(reminder: ReminderEntity) {
        viewModelScope.launch {
            repository.updateReminder(reminder.copy(isActive = !reminder.isActive))
        }
    }

    fun saveReminder(onSaved: () -> Unit) {
        if (_title.value.isBlank()) return

        viewModelScope.launch {
            val nextOrder = _allReminders.value.maxOfOrNull { it.displayOrder }?.plus(1) ?: 0
            val currentOrder = _currentReminder.value?.displayOrder ?: nextOrder

            val entity = ReminderEntity(
                id = _currentReminder.value?.id ?: 0,
                title = _title.value,
                description = _description.value,
                locationId = _selectedLocationId.value,
                timeOfDay = _timeOfDay.value,
                recurringDays = _recurringDays.value,
                specificDate = _specificDate.value,
                isRecurring = _isRecurring.value,
                isActive = _isActive.value,
                lastTriggeredTimestamp = _currentReminder.value?.lastTriggeredTimestamp,
                displayOrder = currentOrder
            )

            val id = if (entity.id == 0L) {
                repository.insertReminder(entity)
            } else {
                repository.updateReminder(entity)
                entity.id
            }

            scheduleWorkManager(id, entity)
            onSaved()
        }
    }
    
    fun deleteReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            repository.deleteReminder(reminder)
            cancelAlarm(reminder.id)
            workManager.cancelAllWorkByTag("reminder_${reminder.id}")
            com.example.locationreminder.notifications.NotificationHelper(context).cancelNotification(reminder.id)
        }
    }

    fun onReminderMoved(fromIndex: Int, toIndex: Int) {
        val currentList = _allReminders.value.toMutableList()
        val item = currentList.removeAt(fromIndex)
        currentList.add(toIndex, item)
        _allReminders.value = currentList
    }

    fun onReminderDragStopped() {
        val currentList = _allReminders.value.mapIndexed { index, item -> 
            item.copy(displayOrder = index) 
        }
        viewModelScope.launch {
            repository.updateReminders(currentList)
        }
    }

    fun deleteLocation(location: LocationEntity) {
        viewModelScope.launch {
            // Remove the location reference from any reminders that use it
            val remindersWithLocation = allReminders.value.filter { it.locationId == location.id }
            remindersWithLocation.forEach { 
                repository.updateReminder(it.copy(locationId = null))
            }
            repository.deleteLocation(location)
        }
    }

    fun onLocationMoved(fromIndex: Int, toIndex: Int) {
        val currentList = _allLocations.value.toMutableList()
        val item = currentList.removeAt(fromIndex)
        currentList.add(toIndex, item)
        _allLocations.value = currentList
    }

    fun onLocationDragStopped() {
        val currentList = _allLocations.value.mapIndexed { index, item -> 
            item.copy(displayOrder = index) 
        }
        viewModelScope.launch {
            repository.updateLocations(currentList)
        }
    }

    private fun cancelAlarm(reminderId: Long) {
        AlarmScheduler.cancelAlarm(context, reminderId)
    }

    private fun scheduleWorkManager(reminderId: Long, reminder: ReminderEntity) {
        AlarmScheduler.cancelAlarm(context, reminderId)
        AlarmScheduler.scheduleAlarm(context, reminder)
    }
}

class ReminderViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val database = AppDatabase.getDatabase(context)
        val repository = AppRepository(database.locationDao(), database.reminderDao())
        val workManager = WorkManager.getInstance(context)
        @Suppress("UNCHECKED_CAST")
        return ReminderViewModel(context, repository, workManager) as T
    }
}
