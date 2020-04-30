/*
 * Copyright 2016-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines.flow

import kotlinx.coroutines.*
import kotlin.test.*

class StateFlowTest : TestBase() {
    @Test
    fun testNormalAndNull() = runTest {
        expect(1)
        val state = MutableStateFlow<Int?>(0)
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            expect(2)
            assertFailsWith<CancellationException> {
                state.collect { value ->
                    when (value) {
                        0 -> expect(3)
                        1 -> expect(5)
                        null -> expect(8)
                        2 -> expect(10)
                        else -> expectUnreached()
                    }
                }
            }
            expect(12)
        }
        expect(4) // collector is waiting
        state.value = 1 // fire in the hole!
        assertEquals(1, state.value)
        yield()
        expect(6)
        state.value = 1 // same value, nothing happens
        yield()
        expect(7)
        state.value = null // null value
        assertNull(state.value)
        yield()
        expect(9)
        state.value = 2 // another value
        assertEquals(2, state.value)
        yield()
        expect(11)
        job.cancel()
        yield()
        finish(13)
    }

    @Test
    fun testEqualsConflation() = runTest {
        expect(1)
        val state = MutableStateFlow(Data(0))
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            expect(2)
            assertFailsWith<CancellationException> {
                state.collect { value ->
                    when(value.i) {
                        0 -> expect(3) // initial value
                        2 -> expect(5)
                        4 -> expect(7)
                        else -> error("Unexpected $value")
                    }
                }
            }
            expect(9)
        }
        state.value = Data(1) // conflated
        state.value = Data(0) // equals to last emitted
        yield() // no repeat zero
        state.value = Data(3) // conflated
        state.value = Data(2) // delivered
        expect(4)
        yield()
        state.value = Data(2) // equals to last one, dropped
        yield()
        state.value = Data(5) // conflated
        state.value = Data(4) // delivered
        expect(6)
        yield()
        expect(8)
        job.cancel()
        yield()
        finish(10)
    }

    data class Data(val i: Int)

    @Test
    fun testDataModel() = runTest {
        val s = CounterModel()
        launch {
            val sum = s.counter.take(11).sum()
            assertEquals(55, sum)
        }
        repeat(10) {
            yield()
            s.inc()
        }
    }

    class CounterModel {
        // private data flow
        private val _counter = MutableStateFlow(0)
        // publicly exposed as a flow
        val counter: StateFlow<Int> get() = _counter

        fun inc() {
            _counter.value++
        }
    }
}