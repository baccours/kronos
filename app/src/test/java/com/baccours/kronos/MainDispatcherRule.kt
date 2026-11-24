package com.baccours.kronos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Installs a test dispatcher as `Dispatchers.Main` for the duration of a test.
 *
 * Needed by every view model test: `viewModelScope` runs on Main, which does not exist in a JVM
 * unit test. Unconfined by default, so launched work runs immediately; `runTest` picks up the same
 * scheduler, so `delay` and `debounce` run in virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
