package com.baccours.kronos.ui.setup

import com.baccours.kronos.ui.components.FieldId

/** Keypad field ids of the setup screen. */
internal object SetupFields {
    val Sets = FieldId("sets")
    val SetRest = FieldId("set_rest")
    val Rounds = FieldId("rounds")
    val TimeCap = FieldId("time_cap")
    val TotalTimeCap = FieldId("total_time_cap")

    fun task(taskId: String) = FieldId("task_$taskId")
}
