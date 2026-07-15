package com.aiagents.app.presentation.scheduled_tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduledTasksViewModelTest {
    @Test
    fun `normalizes weekly and interval schedule values`() {
        assertEquals(
            "MON,WED,FRI 07:00",
            ScheduledTasksViewModel.normalizeScheduleValue(
                ScheduledTasksViewModel.SCHEDULE_WEEKLY,
                " mon,wed,fri 07:00 "
            )
        )
        assertEquals(
            "2h",
            ScheduledTasksViewModel.normalizeScheduleValue(
                ScheduledTasksViewModel.SCHEDULE_INTERVAL,
                " 2H "
            )
        )
    }

    @Test
    fun `exposes every schedule type supported by the scheduler`() {
        assertEquals(4, ScheduledTasksViewModel.SUPPORTED_SCHEDULES.size)
        assertTrue(ScheduledTasksViewModel.SCHEDULE_ONCE in ScheduledTasksViewModel.SUPPORTED_SCHEDULES)
        assertTrue(ScheduledTasksViewModel.SCHEDULE_DAILY in ScheduledTasksViewModel.SUPPORTED_SCHEDULES)
        assertTrue(ScheduledTasksViewModel.SCHEDULE_WEEKLY in ScheduledTasksViewModel.SUPPORTED_SCHEDULES)
        assertTrue(ScheduledTasksViewModel.SCHEDULE_INTERVAL in ScheduledTasksViewModel.SUPPORTED_SCHEDULES)
    }
}
