package com.odnovolov.forgetmenot.presentation.screen.changegrade

import com.odnovolov.forgetmenot.presentation.common.serializers.DateTimeSpanSerializer
import com.soywiz.klock.DateTimeSpan
import kotlinx.serialization.Serializable

@Serializable
data class GradeItem(
    val grade: Int,
    @Serializable(with = DateTimeSpanSerializer::class)
    val waitingPeriod: DateTimeSpan?
)