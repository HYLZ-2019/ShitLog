package com.lunhui.log

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class UiFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as LunhuiApplication
    private fun screenshot(name: String) {
        val image = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir = File(app.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(dir,"$name.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        image.recycle()
    }
    @Test fun startRecreateFinishSaveCalendarAndTheme() {
        var created: String? = null
        try {
            compose.onNodeWithText("开拉").assertExists()
            screenshot("01-home")
            compose.onNodeWithText("开拉").performClick()
            compose.waitUntil(10_000) { runBlocking { app.repository.dao.active() != null } }
            created = runBlocking { app.repository.dao.active()!!.recordId }
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("结束").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("结束").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("排便详情").fetchSemanticsNodes().isNotEmpty() }
            val recordId = created
            val before = runBlocking { app.repository.dao.record(recordId!!)!! }
            assertEquals("completed",before.status)
            assertEquals("pending",before.detailsStatus)
            assertNotNull(before.durationMs)
            compose.onNodeWithText("结束时间").assertDoesNotExist()
            compose.onNodeWithText("开始时间").assertDoesNotExist()
            compose.onNodeWithText("新记录默认否，按实际情况开启").assertDoesNotExist()
            compose.onNodeWithText("文字备注").assertIsDisplayed()
            compose.onNodeWithText("黏马桶").assertIsDisplayed()
            screenshot("02-details")
            compose.onNodeWithContentDescription("4 型 · 光滑柔软条状").performClick()
            compose.onNodeWithContentDescription("肠胃疼").performClick()
            compose.onNodeWithText("文字备注").performTextInput("测试记录：本地保存")
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("排便详情").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("文字备注").performScrollTo()
            compose.onNodeWithText("测试记录：本地保存").assertExists()
            compose.onNodeWithText("保存").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("开拉").fetchSemanticsNodes().isNotEmpty() }
            val entry = runBlocking { app.repository.dao.allEntries().single { it.record.id == recordId } }
            assertEquals("completed",entry.record.detailsStatus)
            assertEquals(listOf(true, false, false, false, false),entry.details!!.flags())
            assertEquals(before.endedAt, entry.record.endedAt)
            assertEquals(before.durationMs, entry.record.durationMs)
            assertEquals("bristol_4",entry.details.stoolForm)
            assertEquals("测试记录：本地保存",entry.record.note)
            compose.onNodeWithText("历史").performClick()
            compose.onNodeWithText("测试记录：本地保存").performScrollTo().assertExists()
            screenshot("03-history")
            compose.onNodeWithText("趋势").performClick()
            compose.onNodeWithText("排便次数").assertExists()
            screenshot("04-trends")
            compose.onNodeWithContentDescription("设置").performClick()
            compose.onNodeWithText("深色").performClick()
            compose.waitForIdle()
            screenshot("05-settings-dark")
            compose.onNodeWithText("跟随系统").performClick()
        } finally {
            created?.let { id -> runBlocking { app.repository.delete(id) } }
        }
    }
}
