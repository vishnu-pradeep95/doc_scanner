package com.pdfscanner.app.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit TestWatcher that replaces Dispatchers.Main with a test dispatcher
 * for the duration of each test.
 *
 * WHY NEEDED:
 * - ScannerViewModel uses Dispatchers.Main for LiveData updates
 * - The JVM has no Android Looper, so Dispatchers.Main has no default
 * - InstantTaskExecutorRule handles LiveData executor, but not coroutine dispatchers
 * - This rule makes Dispatchers.Main synchronous so assertions run immediately
 *
 * USAGE:
 * ```kotlin
 * @get:Rule val mainDispatcherRule = MainDispatcherRule()
 * ```
 *
 * Use UnconfinedTestDispatcher (NOT the deprecated TestCoroutineDispatcher).
 * UnconfinedTestDispatcher runs coroutines eagerly without suspending.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {

    override fun starting(description: Description?) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description?) {
        Dispatchers.resetMain()
    }
}
