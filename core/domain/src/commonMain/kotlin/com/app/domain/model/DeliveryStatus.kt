package com.app.domain.model

import kotlinx.datetime.Instant


sealed class DeliveryStatus {
     data object Sending : DeliveryStatus()
     
     data object Sent : DeliveryStatus()

     data class Delivered(val to: String, val at: Instant) : DeliveryStatus()

     data class Read(val by: String, val at: Instant) : DeliveryStatus()

     data class Failed(val reason: String) : DeliveryStatus()

     data class PartiallyDelivered(val reached: Int, val total: Int) : DeliveryStatus()
}