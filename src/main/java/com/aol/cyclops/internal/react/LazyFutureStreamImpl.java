package com.aol.cyclops.internal.react;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jooq.lambda.Collectable;
import org.jooq.lambda.Seq;

import com.aol.cyclops.Monoid;
import com.aol.cyclops.Reducer;
import com.aol.cyclops.control.LazyReact;
import com.aol.cyclops.control.ReactiveSeq;
import com.aol.cyclops.control.StreamUtils;
import com.aol.cyclops.data.async.QueueFactories;
import com.aol.cyclops.data.async.QueueFactory;
import com.aol.cyclops.data.collections.extensions.standard.ListX;
import com.aol.cyclops.internal.react.stream.LazyStreamWrapper;
import com.aol.cyclops.react.async.subscription.Continueable;
import com.aol.cyclops.react.async.subscription.Subscription;
import com.aol.cyclops.react.collectors.lazy.BatchingCollector;
import com.aol.cyclops.react.collectors.lazy.LazyResultConsumer;
import com.aol.cyclops.react.collectors.lazy.MaxActive;
import com.aol.cyclops.react.threads.ReactPool;
import com.aol.cyclops.types.futurestream.LazyFutureStream;
import com.aol.cyclops.types.stream.HeadAndTail;
import com.aol.cyclops.types.stream.HotStream;
import com.aol.cyclops.types.stream.PausableHotStream;
import com.nurkiewicz.asyncretry.RetryExecutor;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Wither;
import lombok.extern.slf4j.Slf4j;

@Wither
@Getter
@Slf4j
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LazyFutureStreamImpl<U> implements LazyFutureStream<U> {

    private final Optional<Consumer<Throwable>> errorHandler;
    private final LazyStreamWrapper<U> lastActive;

    private final Supplier<LazyResultConsumer<U>> lazyCollector;
    private final QueueFactory<U> queueFactory;
    private final LazyReact simpleReact;
    private final Continueable subscription;
    private final static ReactPool<LazyReact> pool = ReactPool.elasticPool(() -> new LazyReact(
                                                                                               Executors.newSingleThreadExecutor()));

    private final ConsumerHolder error;

    private final MaxActive maxActive;

    @AllArgsConstructor
    static class ConsumerHolder {
        volatile Consumer<Throwable> forward;
    }

    public LazyFutureStreamImpl(final LazyReact lazyReact, final Stream<U> stream) {

        this.simpleReact = lazyReact;

        this.lastActive = new LazyStreamWrapper<>(
                                                  stream, lazyReact);
        this.error = new ConsumerHolder(
                                        a -> {
                                        });
        this.errorHandler = Optional.of((e) -> {
            error.forward.accept(e);
            log.error(e.getMessage(), e);
        });
        this.lazyCollector = () -> new BatchingCollector<U>(
                                                            getMaxActive(), this);
        this.queueFactory = QueueFactories.unboundedNonBlockingQueue();
        this.subscription = new Subscription();

        this.maxActive = lazyReact.getMaxActive();

    }

    @Override
    public void forwardErrors(final Consumer<Throwable> c) {
        error.forward = c;
    }

    @Override
    public LazyReact getPopulator() {
        return pool.nextReactor();
    }

    @Override
    public void returnPopulator(final LazyReact service) {
        pool.populate(service);
    }

    @Override
    public <R, A> R collect(final Collector<? super U, A, R> collector) {
        return block(collector);
    }

    @Override
    public void close() {

    }

    @Override
    public LazyFutureStream<U> withAsync(final boolean b) {

        return this.withSimpleReact(this.simpleReact.withAsync(b));
    }

    @Override
    public Executor getTaskExecutor() {
        return this.simpleReact.getExecutor();
    }

    @Override
    public RetryExecutor getRetrier() {
        return this.simpleReact.getRetrier();
    }

    @Override
    public boolean isAsync() {
        return this.simpleReact.isAsync();
    }

    @Override
    public LazyFutureStream<U> withTaskExecutor(final Executor e) {
        return this.withSimpleReact(simpleReact.withExecutor(e));
    }

    @Override
    public LazyFutureStream<U> withRetrier(final RetryExecutor retry) {
        return this.withSimpleReact(simpleReact.withRetrier(retry));
    }

    @Override
    public LazyFutureStream<U> withLastActive(final LazyStreamWrapper w) {
        return new LazyFutureStreamImpl<U>(
                                           errorHandler, w, lazyCollector, queueFactory, simpleReact, subscription, error, maxActive);

    }

    @Override
    public LazyFutureStream<U> maxActive(final int max) {
        return this.withMaxActive(new MaxActive(
                                                max, max));
    }

    /**
     * Cancel the CompletableFutures in this stage of the stream
     */
    @Override
    public void cancel() {
        this.subscription.closeAll();
        //also need to mark cancelled =true and check during collection
    }

    @Override
    public HotStream<U> schedule(final String cron, final ScheduledExecutorService ex) {
        return ReactiveSeq.<U> fromStream(this.toStream())
                          .schedule(cron, ex);
    }

    @Override
    public HotStream<U> scheduleFixedDelay(final long delay, final ScheduledExecutorService ex) {
        return ReactiveSeq.<U> fromStream(this.toStream())
                          .scheduleFixedDelay(delay, ex);
    }

    @Override
    public HotStream<U> scheduleFixedRate(final long rate, final ScheduledExecutorService ex) {
        return ReactiveSeq.<U> fromStream(this.toStream())
                          .scheduleFixedRate(rate, ex);
    }

    @Override
    public <T> LazyFutureStream<T> unitIterator(final Iterator<T> it) {
        return simpleReact.from(it);
    }

    @Override
    public LazyFutureStream<U> append(final U value) {
        return fromStream(stream().append(value));
    }

    @Override
    public LazyFutureStream<U> prepend(final U value) {

        return fromStream(stream().prepend(value));
    }

    @Override
    public <T> LazyFutureStream<T> unit(final T unit) {
        return fromStream(stream().unit(unit));
    }

    @Override
    public HotStream<U> hotStream(final Executor e) {
        return StreamUtils.hotStream(this, e);
    }

    @Override
    public HotStream<U> primedHotStream(final Executor e) {
        return StreamUtils.primedHotStream(this, e);
    }

    @Override
    public PausableHotStream<U> pausableHotStream(final Executor e) {
        return StreamUtils.pausableHotStream(this, e);
    }

    @Override
    public PausableHotStream<U> primedPausableHotStream(final Executor e) {
        return StreamUtils.primedPausableHotStream(this, e);
    }

    @Override
    public String format() {
        return Seq.seq(this)
                  .format();
    }

    @Override
    public Collectable<U> collectable() {
        //in order for tasks to be executed concurrently we need to make sure that collect is
        //ultimately called via LazyStream#collect. Passing 'this' directly into Seq results in 'this' being returned
        //Seq implements the collection extensions on SeqImpl, so we need to construct a SeqImpl with this as the Stream.
        return Seq.seq(new DelegateStream<U>(
                                             this));
    }

    @Override
    public U foldRight(final Monoid<U> reducer) {
        return reducer.reduce(this);
    }

    @Override
    public <T> T foldRightMapToType(final Reducer<T> reducer) {
        return reducer.mapReduce(reverse());

    }

    @Override
    public <R> R mapReduce(final Reducer<R> reducer) {
        return reducer.mapReduce(this);
    }

    @Override
    public <R> R mapReduce(final Function<? super U, ? extends R> mapper, final Monoid<R> reducer) {
        return Reducer.fromMonoid(reducer, mapper)
                      .mapReduce(this);
    }

    @Override
    public U reduce(final Monoid<U> reducer) {
        return reducer.reduce(this);
    }

    @Override
    public ListX<U> reduce(final Stream<? extends Monoid<U>> reducers) {
        return StreamUtils.reduce(this, reducers);
    }

    @Override
    public ListX<U> reduce(final Iterable<? extends Monoid<U>> reducers) {
        return StreamUtils.reduce(this, reducers);
    }

    @Override
    public U foldRight(final U identity, final BinaryOperator<U> accumulator) {
        return reverse().foldLeft(identity, accumulator);
    }

    @Override
    public Optional<U> min(final Comparator<? super U> comparator) {
        return StreamUtils.min(this, comparator);
    }

    @Override
    public Optional<U> max(final Comparator<? super U> comparator) {
        return StreamUtils.max(this, comparator);
    }

    @Override
    public long count() {
        return this.collect(Collectors.toList())
                   .size();
    }

    @Override
    public boolean allMatch(final Predicate<? super U> c) {

        return filterNot(c).count() == 0l;
    }

    @Override
    public boolean anyMatch(final Predicate<? super U> c) {

        return filter(c).findAny()
                        .isPresent();
    }

    @Override
    public boolean xMatch(final int num, final Predicate<? super U> c) {

        return StreamUtils.xMatch(this, num, c);
    }

    @Override
    public boolean noneMatch(final Predicate<? super U> c) {
        return !anyMatch(c);
    }

    @Override
    public final String join() {
        return StreamUtils.join(this);
    }

    @Override
    public final String join(final String sep) {
        return StreamUtils.join(this, sep);
    }

    @Override
    public String join(final String sep, final String start, final String end) {
        return StreamUtils.join(this, sep, start, end);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.ReactiveSeq#headAndTail()
     */
    @Override
    public HeadAndTail<U> headAndTail() {
        return StreamUtils.headAndTail(this);
    }

}
