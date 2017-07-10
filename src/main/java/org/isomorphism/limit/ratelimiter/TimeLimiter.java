package org.isomorphism.limit.ratelimiter;

import com.google.common.util.concurrent.ExecutionError;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.common.util.concurrent.UncheckedTimeoutException;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 对方法调用加上时间限制
 *
 * @author Kevin Bourrillion
 * @author Jens Nyman
 * @since 1.0
 */
public interface TimeLimiter {

    /**
     * 返回{@code interfaceType}的一个实例，它将所有方法调用委托给{@code target}对象，对每个调用执行指定的时间限制。
     * 对于{@link Object＃equals}，{@link Object＃hashCode}和{@link Object＃toString}的调用，也会执行此时间有限的委托。
     * <p>
     * 如果目标方法调用在达到限制之前完成，返回值或异常将按原样传播到调用者。如果达到时间限制，代理将尝试中止对目标的调用，并将向调用者发出一个{@link UncheckedTimeoutException}。
     * <p>
     * 注意:代理对象的主要目的是在超时过程时将控制权返回给调用者; 中止目标方法调用是次要考虑。
     * 由代理作出的保证的特殊性质和强度是与实现相关的。 但是，当线程中断时，目标对象上的每个方法都能很好工作(behaves appropriately)。
     * <p>
     * <p>For example, to return the value of {@code target.someMethod()}, but substitute {@code DEFAULT_VALUE} if this method call takes over 50 ms, you can
     * use this code:
     * <p>
     * <pre>
     *   TimeLimiter limiter = . . .;
     *   TargetType proxy = limiter.newProxy(target, TargetType.class, 50, TimeUnit.MILLISECONDS);
     *   try {
     *     return proxy.someMethod();
     *   } catch (UncheckedTimeoutException e) {
     *     return DEFAULT_VALUE;
     *   }
     * </pre>
     *
     * @param target          the object to proxy
     * @param interfaceType   the interface you wish the returned proxy to implement
     * @param timeoutDuration with timeoutUnit, the maximum length of time that callers are willing to
     *                        wait on each method call to the proxy
     * @param timeoutUnit     with timeoutDuration, the maximum length of time that callers are willing to
     *                        wait on each method call to the proxy
     * @return a time-limiting proxy
     * @throws IllegalArgumentException if {@code interfaceType} is a regular class, enum, or
     *                                  annotation type, rather than an interface
     */
    <T> T newProxy(T target, Class<T> interfaceType, long timeoutDuration, TimeUnit timeoutUnit);

    /**
     * Invokes a specified Callable, timing out after the specified time limit. If the target method
     * call finished before the limit is reached, the return value or exception is propagated to the
     * caller exactly as-is. If, on the other hand, the time limit is reached, we attempt to abort the
     * call to the target, and throw an {@link UncheckedTimeoutException} to the caller.
     *
     * @param callable        the Callable to execute
     * @param timeoutDuration with timeoutUnit, the maximum length of time to wait
     * @param timeoutUnit     with timeoutDuration, the maximum length of time to wait
     * @param interruptible   whether to respond to thread interruption by aborting the operation and
     *                        throwing InterruptedException; if false, the operation is allowed to complete or time out,
     *                        and the current thread's interrupt status is re-asserted.
     * @return the result returned by the Callable
     * @throws InterruptedException      if {@code interruptible} is true and our thread is interrupted
     *                                   during execution
     * @throws UncheckedTimeoutException if the time limit is reached
     * @deprecated Use one of the other {@code call[Uninterruptibly]WithTimeout()} or {@code
     * run[Uninterruptibly]WithTimeout()} methods. This method is scheduled to be removed in Guava
     * 23.0.
     */
    @Deprecated
    <T> T callWithTimeout(Callable<T> callable, long timeoutDuration, TimeUnit timeoutUnit, boolean interruptible) throws Exception;

    /**
     * Invokes a specified Callable, timing out after the specified time limit. If the target method
     * call finishes before the limit is reached, the return value or a wrapped exception is
     * propagated. If, on the other hand, the time limit is reached, we attempt to abort the call to
     * the target, and throw a {@link TimeoutException} to the caller.
     *
     * @param callable        the Callable to execute
     * @param timeoutDuration with timeoutUnit, the maximum length of time to wait
     * @param timeoutUnit     with timeoutDuration, the maximum length of time to wait
     * @return the result returned by the Callable
     * @throws TimeoutException            if the time limit is reached
     * @throws InterruptedException        if the current thread was interrupted during execution
     * @throws ExecutionException          if {@code callable} throws a checked exception
     * @throws UncheckedExecutionException if {@code callable} throws a {@code RuntimeException}
     * @throws ExecutionError              if {@code callable} throws an {@code Error}
     * @since 22.0
     */
    <T> T callWithTimeout(Callable<T> callable, long timeoutDuration, TimeUnit timeoutUnit) throws TimeoutException, InterruptedException, ExecutionException;

    /**
     * Invokes a specified Callable, timing out after the specified time limit. If the target method
     * call finishes before the limit is reached, the return value or a wrapped exception is
     * propagated. If, on the other hand, the time limit is reached, we attempt to abort the call to
     * the target, and throw a {@link TimeoutException} to the caller.
     * <p>
     * <p>The difference with {@link #callWithTimeout(Callable, long, TimeUnit)} is that this method
     * will ignore interrupts on the current thread.
     *
     * @param callable        the Callable to execute
     * @param timeoutDuration with timeoutUnit, the maximum length of time to wait
     * @param timeoutUnit     with timeoutDuration, the maximum length of time to wait
     * @return the result returned by the Callable
     * @throws TimeoutException            if the time limit is reached
     * @throws ExecutionException          if {@code callable} throws a checked exception
     * @throws UncheckedExecutionException if {@code callable} throws a {@code RuntimeException}
     * @throws ExecutionError              if {@code callable} throws an {@code Error}
     * @since 22.0
     */
    <T> T callUninterruptiblyWithTimeout(Callable<T> callable, long timeoutDuration, TimeUnit timeoutUnit) throws TimeoutException, ExecutionException;

    /**
     * Invokes a specified Runnable, timing out after the specified time limit. If the target method
     * run finishes before the limit is reached, this method returns or a wrapped exception is
     * propagated. If, on the other hand, the time limit is reached, we attempt to abort the run, and
     * throw a {@link TimeoutException} to the caller.
     *
     * @param runnable        the Runnable to execute
     * @param timeoutDuration with timeoutUnit, the maximum length of time to wait
     * @param timeoutUnit     with timeoutDuration, the maximum length of time to wait
     * @throws TimeoutException            if the time limit is reached
     * @throws InterruptedException        if the current thread was interrupted during execution
     * @throws UncheckedExecutionException if {@code runnable} throws a {@code RuntimeException}
     * @throws ExecutionError              if {@code runnable} throws an {@code Error}
     * @since 22.0
     */
    void runWithTimeout(Runnable runnable, long timeoutDuration, TimeUnit timeoutUnit) throws TimeoutException, InterruptedException;

    /**
     * Invokes a specified Runnable, timing out after the specified time limit. If the target method
     * run finishes before the limit is reached, this method returns or a wrapped exception is
     * propagated. If, on the other hand, the time limit is reached, we attempt to abort the run, and
     * throw a {@link TimeoutException} to the caller.
     * <p>
     * <p>The difference with {@link #runWithTimeout(Runnable, long, TimeUnit)} is that this method
     * will ignore interrupts on the current thread.
     *
     * @param runnable        the Runnable to execute
     * @param timeoutDuration with timeoutUnit, the maximum length of time to wait
     * @param timeoutUnit     with timeoutDuration, the maximum length of time to wait
     * @throws TimeoutException            if the time limit is reached
     * @throws UncheckedExecutionException if {@code runnable} throws a {@code RuntimeException}
     * @throws ExecutionError              if {@code runnable} throws an {@code Error}
     * @since 22.0
     */
    void runUninterruptiblyWithTimeout(Runnable runnable, long timeoutDuration, TimeUnit timeoutUnit) throws TimeoutException;
}
