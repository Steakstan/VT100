package org.msv.vt100.util;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

public class Waiter {
    private static final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(
                    Math.max(2, Runtime.getRuntime().availableProcessors()/2),
                    r -> { Thread t = new Thread(r, "waiter-scheduler"); t.setDaemon(true); return t; }
            );
    public static void shutdown() { scheduler.shutdownNow(); }

    public static CompletableFuture<Void> waitFor(BooleanSupplier condition, Duration timeout, Duration interval) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        ScheduledFuture<?> pollingTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (condition.getAsBoolean()) {
                    future.complete(null);
                }
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        }, 0, interval.toMillis(), TimeUnit.MILLISECONDS);

        // Таймаут
        scheduler.schedule(() -> {
            if (!future.isDone()) {
                future.completeExceptionally(new TimeoutException("Bedingung nicht erfüllt innerhalb der Frist."));
            }
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);

        // Остановка задачи после выполнения
        future.whenComplete((result, error) -> pollingTask.cancel(true));

        return future;
    }

    public static CompletableFuture<Void> waitFor(BooleanSupplier condition) {
        return waitFor(condition, Duration.ofSeconds(10), Duration.ofMillis(1000));
    }

    public static boolean waitUntil(String debugText, Callable<Boolean> condition) throws InterruptedException {
        try {
            waitFor(() -> {
                try {
                    return condition.call();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                } catch (Exception e) {
                    System.out.println("[Fehler bei Bedingung: " + debugText + "] " + e.getMessage());
                    return false;
                }
            }).get();
            return true;
        } catch (ExecutionException e) {
            System.out.println("[Fehler beim Warten auf Bedingung: " + debugText + "] " + e.getMessage());
            return false;
        }
    }

}
