package com.example.locationreminder.ui.screens

import android.content.Context
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

class ReminderViewModel(
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

    private val _isActive = MutableStateFlow(true)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    val allLocations: StateFlow<List<LocationEntity>> = repository.allLocations
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(), emptyList())

    val allReminders: StateFlow<List<ReminderEntity>> = repository.allReminders
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(), emptyList())

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
            _isActive.value = true
        }
    }

    fun updateTitle(t: String) { _title.value = t }
    fun updateDescription(d: String) { _description.value = d }
    fun updateLocation(locId: Long?) { _selectedLocationId.value = locId }
    fun updateTimeOfDay(time: String?) { _timeOfDay.value = time }
    fun updateSpecificDate(date: Long?) { _specificDate.value = date }
    fun updateIsRecurring(recurring: Boolean) { _isRecurring.value = recurring }

    fun toggleReminderActive(reminder: ReminderEntity) {
        viewModelScope.launch {
            repository.updateReminder(reminder.copy(isActive = !reminder.isActive))
        }
    }

    fun saveReminder(onSaved: () -> Unit) {
        if (_title.value.isBlank()) return

        viewModelScope.launch {
            val entity = ReminderEntity(
                id = _currentReminder.value?.id ?: 0,
                title = _title.value,
                description = _description.value,
                locationId = _selectedLocationId.value,
                timeOfDay = _timeOfDay.value,
                specificDate = _specificDate.value,
                isRecurring = _isRecurring.value,
                isActive = _isActive.value
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
            workManager.cancelAllWorkByTag("reminder_${reminder.id}")
        }
    }

    private fun scheduleWorkManager(reminderId: Long, reminder: ReminderEntity) {
        workManager.cancelAllWorkByTag("reminder_$reminderId")

        if (reminder.specificDate != null && reminder.timeOfDay != null) {
            val parts = reminder.timeOfDay.split(":")
            if (parts.size == 2) {
                val hour = parts[0].toInt()
                val minute = parts[1].toInt()

                val calendar = Calendar.getInstance().apply {
                    timeInMillis = reminder.specificDate
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                }

                val delay = calendar.timeInMillis - System.currentTimeMillis()
                if (delay > 0) {
                    val workData = Data.Builder()
                        .putLong(ReminderWorker.KEY_REMINDER_ID, reminderId)
                        .build()

                    val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
                        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                        .setInputData(workData)
                        .addTag("reminder_$reminderId")
                        .build()

                    workManager.enqueue(workRequest)
                }
            }
        }
    }
}

class ReminderViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val database = AppDatabase.getDatabase(context)
        val repository = AppRepository(database.locationDao(), database.reminderDao())
        val workManager = WorkManager.getInstance(context)
        @Suppress("UNCHECKED_CAST")
        return ReminderViewModel(repository, workManager) as T
    }
}
