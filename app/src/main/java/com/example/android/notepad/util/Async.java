package com.example.android.notepad.util;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 极简的异步工具：在后台线程执行数据库操作，再把结果切回主线程。
 * <p>
 * SQLite 查询 / 写入是阻塞 IO，放在主线程会造成掉帧，因此统一通过这里调度。
 */
public final class Async {

    private static final String TAG = "Async";

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Async() {
    }

    /** 后台任务，可以抛出异常 */
    public interface Work<T> {
        T run() throws Exception;
    }

    /** 主线程回调，出错时 result 为 null */
    public interface Callback<T> {
        void onResult(T result);
    }

    /** 后台执行 work，完成后切回主线程回调；异常时 result 为 null。 */
    public static <T> void run(final Work<T> work, final Callback<T> callback) {
        EXECUTOR.execute(() -> {
            T result = null;
            try {
                result = work.run();
            } catch (Exception e) {
                Log.w(TAG, "后台任务执行失败", e);
            }
            final T finalResult = result;
            MAIN.post(() -> {
                if (callback != null) {
                    callback.onResult(finalResult);
                }
            });
        });
    }

    /** 只关心执行、不关心结果的后台任务。 */
    public static void io(Runnable task) {
        EXECUTOR.execute(task);
    }

    /** 把任务切回主线程执行（用于更新 UI）。 */
    public static void main(Runnable task) {
        MAIN.post(task);
    }
}
