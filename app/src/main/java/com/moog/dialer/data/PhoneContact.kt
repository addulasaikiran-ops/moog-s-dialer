package com.moog.dialer.data

data class PhoneContact(
    val id: Long,
    val displayName: String,
    val phoneNumber: String,
    val photoUri: String?,
    val phoneType: Int
)
