/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.agent.core.profile;

import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.context.TracingContext;
import org.apache.skywalking.apm.agent.core.context.ids.ID;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * profile task execution context, it will create on process this profile task
 *
 * @author MrPro
 */
public class ProfileTaskExecutionContext {

    // task data
    private final ProfileTask task;

    // record current profiling count, use this to check has available profile slot
    private final AtomicInteger currentProfilingCount = new AtomicInteger(0);

    // profiling segment slot
    private volatile ThreadProfiler[] profilingSegmentSlots = new ThreadProfiler[Config.Profile.MAX_PARALLEL];

    // current profiling execution future
    private volatile Future profilingFuture;

    public ProfileTaskExecutionContext(ProfileTask task) {
        this.task = task;
    }

    /**
     * start profiling this task
     * @param executorService
     */
    public void startProfiling(ExecutorService executorService) {
        profilingFuture = executorService.submit(new ProfileThread(this));
    }

    /**
     * stop profiling
     */
    public void stopProfiling() {
        if (profilingFuture != null) {
            profilingFuture.cancel(true);
        }
    }

    /**
     * check have available slot to profile and add it
     * @param tracingContext
     * @param firstSpanOPName
     * @return
     */
    public boolean attemptProfiling(TracingContext tracingContext, ID traceSegmentId, String firstSpanOPName) {
        // check has available slot
        final int usingSlotCount = currentProfilingCount.get();
        if (usingSlotCount >= Config.Profile.MAX_PARALLEL) {
            return false;
        }

        // check first operation name matches
        if (!Objects.equals(task.getFistSpanOPName(), firstSpanOPName)) {
            return false;
        }

        // try to occupy slot
        if (!currentProfilingCount.compareAndSet(usingSlotCount, usingSlotCount + 1)) {
            return false;
        }

        final ThreadProfiler segmentContext = new ThreadProfiler(tracingContext, traceSegmentId, Thread.currentThread(), this);
        for (int slot = 0; slot < profilingSegmentSlots.length; slot++) {
            if (profilingSegmentSlots[slot] == null) {
                profilingSegmentSlots[slot] = segmentContext;
                break;
            }
        }
        return true;
    }

    /**
     * find tracing context and clear on slot
     *
     * @param tracingContext
     */
    public void stopTracingProfile(TracingContext tracingContext) {
        // find current tracingContext and clear it
        for (int slot = 0; slot < profilingSegmentSlots.length; slot++) {
            ThreadProfiler currentProfiler = profilingSegmentSlots[slot];
            if (currentProfiler != null && currentProfiler.matches(tracingContext)) {
                profilingSegmentSlots[slot] = null;

                // setting stop running
                currentProfiler.stopProfiling();
                currentProfilingCount.addAndGet(-1);

                // see https://www.javamex.com/tutorials/volatile_arrays.shtml, solution 2
                profilingSegmentSlots = profilingSegmentSlots;
                break;
            }
        }

    }

    public ProfileTask getTask() {
        return task;
    }

    public ThreadProfiler[] threadProfilerSlots() {
        return profilingSegmentSlots;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProfileTaskExecutionContext that = (ProfileTaskExecutionContext) o;
        return Objects.equals(task, that.task);
    }

    @Override
    public int hashCode() {
        return Objects.hash(task);
    }

}
