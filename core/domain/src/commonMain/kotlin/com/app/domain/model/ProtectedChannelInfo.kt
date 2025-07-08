package com.app.domain.model

data class ProtectedChannelInfo(val channel: String, val isProtected: Boolean, val creatorId: String?, val keyCommitment: String?)
data class RetentionChannelInfo(val channel: String, val isEnabled: Boolean, val creatorId: String?)