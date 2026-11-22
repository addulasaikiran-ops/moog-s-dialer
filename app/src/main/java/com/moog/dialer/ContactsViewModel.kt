package com.moog.dialer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moog.dialer.data.ContactsRepository
import com.moog.dialer.data.PhoneContact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ContactsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ContactsRepository(application.contentResolver)
    private val _contacts = MutableStateFlow<List<PhoneContact>>(emptyList())
    val contacts: StateFlow<List<PhoneContact>> = _contacts.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _contacts.value = runCatching { repository.getPhoneContacts() }.getOrDefault(emptyList())
            _loading.value = false
        }
    }
}
