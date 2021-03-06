/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package alpha1.android.arch.core.executor;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;

/**
 * A static class that serves as a central point to execute common tasks.
 * <p>
 *
 * @hide This API is not final.
 */
@RestrictTo({ RestrictTo.Scope.LIBRARY_GROUP, RestrictTo.Scope.TESTS })
public class AppToolkitTaskExecutor extends TaskExecutor {
    private static volatile AppToolkitTaskExecutor sInstance;

    @NonNull
    private TaskExecutor mDelegate;

    @NonNull
    private TaskExecutor mDefaultTaskExecutor;


    private AppToolkitTaskExecutor() {
        mDefaultTaskExecutor = new DefaultTaskExecutor();
        mDelegate = mDefaultTaskExecutor;
    }


    /**
     * Returns an instance of the task executor.
     *
     * @return The singleton AppToolkitTaskExecutor.
     */
    public static AppToolkitTaskExecutor getInstance() {
        if (sInstance != null) {
            return sInstance;
        }
        synchronized (AppToolkitTaskExecutor.class) {
            if (sInstance == null) {
                sInstance = new AppToolkitTaskExecutor();
            }
        }
        return sInstance;
    }


    /**
     * Sets a delegate to handle task execution requests.
     * <p>
     * If you have a common executor, you can set it as the delegate and App Toolkit components will
     * use your executors. You may also want to use this for your tests.
     * <p>
     * Calling this method with {@code null} sets it to the default TaskExecutor.
     *
     * @param taskExecutor The task executor to handle task requests.
     */
    public void setDelegate(@Nullable TaskExecutor taskExecutor) {
        mDelegate = taskExecutor == null ? mDefaultTaskExecutor : taskExecutor;
    }


    @Override
    public void executeOnDiskIO(Runnable runnable) {
        mDelegate.executeOnDiskIO(runnable);
    }


    @Override
    public void postToMainThread(Runnable runnable) {
        mDelegate.postToMainThread(runnable);
    }


    @Override
    public boolean isMainThread() {
        return mDelegate.isMainThread();
    }
}
