package com.aol.cyclops.data.collections.extensions.standard;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jooq.lambda.Seq;
import org.jooq.lambda.tuple.Tuple2;
import org.jooq.lambda.tuple.Tuple3;
import org.jooq.lambda.tuple.Tuple4;
import org.reactivestreams.Publisher;

import com.aol.cyclops.Monoid;
import com.aol.cyclops.control.Matchable.CheckValue1;
import com.aol.cyclops.control.ReactiveSeq;
import com.aol.cyclops.control.StreamUtils;
import com.aol.cyclops.control.Trampoline;
import com.aol.cyclops.types.Applicative;
import com.aol.cyclops.types.OnEmptySwitch;
import com.aol.cyclops.types.Value;

public interface SortedSetX<T> extends SortedSet<T>, MutableCollectionX<T>, OnEmptySwitch<T, SortedSet<T>> {
    static <T> Collector<T, ?, SortedSet<T>> defaultCollector() {
        return Collectors.toCollection(() -> new TreeSet<T>(
                                                            (Comparator) Comparator.<Comparable> naturalOrder()));
    }

    static <T> Collector<T, ?, SortedSet<T>> immutableCollector() {
        return Collectors.collectingAndThen(defaultCollector(), (final SortedSet<T> d) -> Collections.unmodifiableSortedSet(d));

    }

    /**
    * Create a SortedSetX that contains the Integers between start and end
    * 
    * @param start
    *            Number of range to start from
    * @param end
    *            Number for range to end at
    * @return Range SortedSetX
    */
    public static SortedSetX<Integer> range(final int start, final int end) {
        return ReactiveSeq.range(start, end)
                          .toSortedSetX();
    }

    /**
     * Create a SortedSetX that contains the Longs between start and end
     * 
     * @param start
     *            Number of range to start from
     * @param end
     *            Number for range to end at
     * @return Range SortedSetX
     */
    public static SortedSetX<Long> rangeLong(final long start, final long end) {
        return ReactiveSeq.rangeLong(start, end)
                          .toSortedSetX();
    }

    /**
     * Unfold a function into a SortedSetX
     * 
     * <pre>
     * {@code 
     *  SortedSetX.unfold(1,i->i<=6 ? Optional.of(Tuple.tuple(i,i+1)) : Optional.empty());
     * 
     * //(1,2,3,4,5)
     * 
     * }</pre>
     * 
     * @param seed Initial value 
     * @param unfolder Iteratively applied function, terminated by an empty Optional
     * @return SortedSetX generated by unfolder function
     */
    static <U, T> SortedSetX<T> unfold(final U seed, final Function<? super U, Optional<Tuple2<T, U>>> unfolder) {
        return ReactiveSeq.unfold(seed, unfolder)
                          .toSortedSetX();
    }

    /**
     * Generate a SortedSetX from the provided Supplier up to the provided limit number of times
     * 
     * @param limit Max number of elements to generate
     * @param s Supplier to generate SortedSetX elements
     * @return SortedSetX generated from the provided Supplier
     */
    public static <T> SortedSetX<T> generate(final long limit, final Supplier<T> s) {

        return ReactiveSeq.generate(s)
                          .limit(limit)
                          .toSortedSetX();
    }

    /**
     * Create a SortedSetX by iterative application of a function to an initial element up to the supplied limit number of times
     * 
     * @param limit Max number of elements to generate
     * @param seed Initial element
     * @param f Iteratively applied to each element to generate the next element
     * @return SortedSetX generated by iterative application
     */
    public static <T> SortedSetX<T> iterate(final long limit, final T seed, final UnaryOperator<T> f) {
        return ReactiveSeq.iterate(seed, f)
                          .limit(limit)
                          .toSortedSetX();

    }

    public static <T> SortedSetX<T> empty() {
        return fromIterable((SortedSet<T>) defaultCollector().supplier()
                                                             .get());
    }

    public static <T> SortedSetX<T> of(final T... values) {
        final SortedSet<T> res = (SortedSet<T>) defaultCollector().supplier()
                                                                  .get();
        for (final T v : values)
            res.add(v);
        return fromIterable(res);
    }

    public static <T> SortedSetX<T> singleton(final T value) {
        return of(value);
    }

    /**
     * Construct a SortedSetX from an Publisher
     * 
     * @param publisher
     *            to construct SortedSetX from
     * @return SortedSetX
     */
    public static <T> SortedSetX<T> fromPublisher(final Publisher<? extends T> publisher) {
        return ReactiveSeq.fromPublisher((Publisher<T>) publisher)
                          .toSortedSetX();
    }

    public static <T> SortedSetX<T> fromIterable(final Iterable<T> it) {
        return fromIterable(defaultCollector(), it);
    }

    public static <T> SortedSetX<T> fromIterable(final Collector<T, ?, SortedSet<T>> collector, final Iterable<T> it) {
        if (it instanceof SortedSetX)
            return (SortedSetX<T>) it;
        if (it instanceof SortedSet)
            return new SortedSetXImpl<T>(
                                         (SortedSet) it, collector);
        return new SortedSetXImpl<T>(
                                     StreamUtils.stream(it)
                                                .collect(collector),
                                     collector);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.sequence.traits.ConvertableSequence#toListX()
     */
    @Override
    default SortedSetX<T> toSortedSetX() {
        return this;
    }

    /**
     * Combine two adjacent elements in a SortedSetX using the supplied BinaryOperator
     * This is a stateful grouping and reduction operation. The output of a combination may in turn be combined
     * with it's neighbor
     * <pre>
     * {@code 
     *  SortedSetX.of(1,1,2,3)
                   .combine((a, b)->a.equals(b),Semigroups.intSum)
                   .toListX()
                   
     *  //ListX(3,4) 
     * }</pre>
     * 
     * @param predicate Test to see if two neighbors should be joined
     * @param op Reducer to combine neighbors
     * @return Combined / Partially Reduced SortedSetX
     */
    @Override
    default SortedSetX<T> combine(final BiPredicate<? super T, ? super T> predicate, final BinaryOperator<T> op) {
        return (SortedSetX<T>) MutableCollectionX.super.combine(predicate, op);
    }
    /* (non-Javadoc)
     * @see com.aol.cyclops.types.Applicative#combine(com.aol.cyclops.types.Value, java.util.function.BiFunction)
     */
    @Override
    default <T2, R> SortedSetX<R> combine(Value<? extends T2> app, BiFunction<? super T, ? super T2, ? extends R> fn) {
        
        return ( SortedSetX<R>)MutableCollectionX.super.combine(app, fn);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.types.Applicative#combine(java.util.function.BinaryOperator, com.aol.cyclops.types.Applicative)
     */
    @Override
    default  SortedSetX<T> combine(BinaryOperator<Applicative<T>> combiner, Applicative<T> app) {
      
        return ( SortedSetX<T>)MutableCollectionX.super.combine(combiner, app);
    }
    @Override
    default <R> SortedSetX<R> unit(final R value) {
        return singleton(value);
    }

    @Override
    default <R> SortedSetX<R> unit(final Collection<R> col) {
        return fromIterable(col);
    }

    @Override
    default <R> SortedSetX<R> unitIterator(final Iterator<R> it) {
        return fromIterable(() -> it);
    }

    @Override
    default ReactiveSeq<T> stream() {

        return ReactiveSeq.fromIterable(this);
    }

    @Override
    default <T1> SortedSetX<T1> from(final Collection<T1> c) {
        return SortedSetX.<T1> fromIterable(getCollector(), c);
    }

    public <T> Collector<T, ?, SortedSet<T>> getCollector();

    @Override
    default <X> SortedSetX<X> fromStream(final Stream<X> stream) {
        return new SortedSetXImpl<>(
                                    stream.collect(getCollector()), getCollector());
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#reverse()
     */
    @Override
    default SortedSetX<T> reverse() {
        return (SortedSetX<T>) MutableCollectionX.super.reverse();
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#filter(java.util.function.Predicate)
     */
    @Override
    default SortedSetX<T> filter(final Predicate<? super T> pred) {

        return (SortedSetX<T>) MutableCollectionX.super.filter(pred);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#map(java.util.function.Function)
     */
    @Override
    default <R> SortedSetX<R> map(final Function<? super T, ? extends R> mapper) {

        return (SortedSetX<R>) MutableCollectionX.super.<R> map(mapper);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#flatMap(java.util.function.Function)
     */
    @Override
    default <R> SortedSetX<R> flatMap(final Function<? super T, ? extends Iterable<? extends R>> mapper) {

        return (SortedSetX<R>) MutableCollectionX.super.<R> flatMap(mapper);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#limit(long)
     */
    @Override
    default SortedSetX<T> limit(final long num) {
        return (SortedSetX<T>) MutableCollectionX.super.limit(num);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#skip(long)
     */
    @Override
    default SortedSetX<T> skip(final long num) {

        return (SortedSetX<T>) MutableCollectionX.super.skip(num);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#takeWhile(java.util.function.Predicate)
     */
    @Override
    default SortedSetX<T> takeWhile(final Predicate<? super T> p) {

        return (SortedSetX<T>) MutableCollectionX.super.takeWhile(p);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#dropWhile(java.util.function.Predicate)
     */
    @Override
    default SortedSetX<T> dropWhile(final Predicate<? super T> p) {

        return (SortedSetX<T>) MutableCollectionX.super.dropWhile(p);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#takeUntil(java.util.function.Predicate)
     */
    @Override
    default SortedSetX<T> takeUntil(final Predicate<? super T> p) {

        return (SortedSetX<T>) MutableCollectionX.super.takeUntil(p);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#dropUntil(java.util.function.Predicate)
     */
    @Override
    default SortedSetX<T> dropUntil(final Predicate<? super T> p) {

        return (SortedSetX<T>) MutableCollectionX.super.dropUntil(p);
    }

    @Override
    default SortedSetX<T> takeRight(final int num) {
        return (SortedSetX<T>) MutableCollectionX.super.takeRight(num);
    }

    @Override
    default SortedSetX<T> dropRight(final int num) {
        return (SortedSetX<T>) MutableCollectionX.super.dropRight(num);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#trampoline(java.util.function.Function)
     */
    @Override
    default <R> SortedSetX<R> trampoline(final Function<? super T, ? extends Trampoline<? extends R>> mapper) {

        return (SortedSetX<R>) MutableCollectionX.super.<R> trampoline(mapper);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#slice(long, long)
     */
    @Override
    default SortedSetX<T> slice(final long from, final long to) {

        return (SortedSetX<T>) MutableCollectionX.super.slice(from, to);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#sorted(java.util.function.Function)
     */
    @Override
    default <U extends Comparable<? super U>> SortedSetX<T> sorted(final Function<? super T, ? extends U> function) {

        return (SortedSetX<T>) MutableCollectionX.super.sorted(function);
    }

    @Override
    default SortedSetX<ListX<T>> grouped(final int groupSize) {
        return (SortedSetX<ListX<T>>) (SortedSetX<T>) MutableCollectionX.super.grouped(groupSize);
    }

    @Override
    default <K, A, D> SortedSetX<Tuple2<K, D>> grouped(final Function<? super T, ? extends K> classifier,
            final Collector<? super T, A, D> downstream) {
        return (SortedSetX) MutableCollectionX.super.grouped(classifier, downstream);
    }

    @Override
    default <K> SortedSetX<Tuple2<K, Seq<T>>> grouped(final Function<? super T, ? extends K> classifier) {

        return (SortedSetX) fromStream(stream().grouped(classifier)
                                               .map(t -> t.map2(Comparables::comparable)));
    }

    @Override
    default <U> SortedSetX<Tuple2<T, U>> zip(final Iterable<? extends U> other) {
        return (SortedSetX<Tuple2<T, U>>) (SortedSetX<T>) MutableCollectionX.super.zip(other);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#zip(java.lang.Iterable, java.util.function.BiFunction)
     */
    @Override
    default <U, R> SortedSetX<R> zip(final Iterable<? extends U> other, final BiFunction<? super T, ? super U, ? extends R> zipper) {
        return (SortedSetX<R>) MutableCollectionX.super.zip(other, zipper);
    }

    @Override
    default <U, R> SortedSetX<R> zip(final Seq<? extends U> other, final BiFunction<? super T, ? super U, ? extends R> zipper) {
        return (SortedSetX<R>) MutableCollectionX.super.zip(other, zipper);
    }

    @Override
    default <U, R> SortedSetX<R> zip(final Stream<? extends U> other, final BiFunction<? super T, ? super U, ? extends R> zipper) {
        return (SortedSetX<R>) MutableCollectionX.super.zip(other, zipper);
    }

    @Override
    default SortedSetX<ListX<T>> sliding(final int windowSize) {
        return (SortedSetX<ListX<T>>) (SortedSetX<T>) MutableCollectionX.super.sliding(windowSize);
    }

    @Override
    default SortedSetX<ListX<T>> sliding(final int windowSize, final int increment) {
        return (SortedSetX<ListX<T>>) (SortedSetX<T>) MutableCollectionX.super.sliding(windowSize, increment);
    }

    @Override
    default SortedSetX<T> scanLeft(final Monoid<T> monoid) {
        return (SortedSetX<T>) MutableCollectionX.super.scanLeft(monoid);
    }

    @Override
    default <U> SortedSetX<U> scanLeft(final U seed, final BiFunction<? super U, ? super T, ? extends U> function) {
        return (SortedSetX<U>) (SortedSetX<T>) MutableCollectionX.super.scanLeft(seed, function);
    }

    @Override
    default SortedSetX<T> scanRight(final Monoid<T> monoid) {
        return (SortedSetX<T>) MutableCollectionX.super.scanRight(monoid);
    }

    @Override
    default <U> SortedSetX<U> scanRight(final U identity, final BiFunction<? super T, ? super U, ? extends U> combiner) {
        return (SortedSetX<U>) (SortedSetX<T>) MutableCollectionX.super.scanRight(identity, combiner);
    }

    @Override
    default SortedSetX<T> plus(final T e) {
        add(e);
        return this;
    }

    @Override
    default SortedSetX<T> plusAll(final Collection<? extends T> list) {
        addAll(list);
        return this;
    }

    @Override
    default SortedSetX<T> minus(final Object e) {
        remove(e);
        return this;
    }

    @Override
    default SortedSetX<T> minusAll(final Collection<?> list) {
        removeAll(list);
        return this;
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#cycle(int)
     */
    @Override
    default ListX<T> cycle(final int times) {

        return this.stream()
                   .cycle(times)
                   .toListX();
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#cycle(com.aol.cyclops.sequence.Monoid, int)
     */
    @Override
    default ListX<T> cycle(final Monoid<T> m, final int times) {

        return this.stream()
                   .cycle(m, times)
                   .toListX();
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#cycleWhile(java.util.function.Predicate)
     */
    @Override
    default ListX<T> cycleWhile(final Predicate<? super T> predicate) {

        return this.stream()
                   .cycleWhile(predicate)
                   .toListX();
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#cycleUntil(java.util.function.Predicate)
     */
    @Override
    default ListX<T> cycleUntil(final Predicate<? super T> predicate) {

        return this.stream()
                   .cycleUntil(predicate)
                   .toListX();
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#zip(java.util.stream.Stream)
     */
    @Override
    default <U> SortedSetX<Tuple2<T, U>> zip(final Stream<? extends U> other) {

        return (SortedSetX) MutableCollectionX.super.zip(other);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#zip(org.jooq.lambda.Seq)
     */
    @Override
    default <U> SortedSetX<Tuple2<T, U>> zip(final Seq<? extends U> other) {

        return (SortedSetX) MutableCollectionX.super.zip(other);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#zip3(java.util.stream.Stream, java.util.stream.Stream)
     */
    @Override
    default <S, U> SortedSetX<Tuple3<T, S, U>> zip3(final Stream<? extends S> second, final Stream<? extends U> third) {

        return (SortedSetX) MutableCollectionX.super.zip3(second, third);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#zip4(java.util.stream.Stream, java.util.stream.Stream, java.util.stream.Stream)
     */
    @Override
    default <T2, T3, T4> SortedSetX<Tuple4<T, T2, T3, T4>> zip4(final Stream<? extends T2> second, final Stream<? extends T3> third,
            final Stream<? extends T4> fourth) {

        return (SortedSetX) MutableCollectionX.super.zip4(second, third, fourth);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#zipWithIndex()
     */
    @Override
    default SortedSetX<Tuple2<T, Long>> zipWithIndex() {

        return (SortedSetX<Tuple2<T, Long>>) MutableCollectionX.super.zipWithIndex();
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#distinct()
     */
    @Override
    default SortedSetX<T> distinct() {

        return (SortedSetX<T>) MutableCollectionX.super.distinct();
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#sorted()
     */
    @Override
    default SortedSetX<T> sorted() {

        return (SortedSetX<T>) MutableCollectionX.super.sorted();
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#sorted(java.util.Comparator)
     */
    @Override
    default SortedSetX<T> sorted(final Comparator<? super T> c) {

        return (SortedSetX<T>) MutableCollectionX.super.sorted(c);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#skipWhile(java.util.function.Predicate)
     */
    @Override
    default SortedSetX<T> skipWhile(final Predicate<? super T> p) {

        return (SortedSetX<T>) MutableCollectionX.super.skipWhile(p);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#skipUntil(java.util.function.Predicate)
     */
    @Override
    default SortedSetX<T> skipUntil(final Predicate<? super T> p) {

        return (SortedSetX<T>) MutableCollectionX.super.skipUntil(p);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#limitWhile(java.util.function.Predicate)
     */
    @Override
    default SortedSetX<T> limitWhile(final Predicate<? super T> p) {

        return (SortedSetX<T>) MutableCollectionX.super.limitWhile(p);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#limitUntil(java.util.function.Predicate)
     */
    @Override
    default SortedSetX<T> limitUntil(final Predicate<? super T> p) {

        return (SortedSetX<T>) MutableCollectionX.super.limitUntil(p);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#intersperse(java.lang.Object)
     */
    @Override
    default SortedSetX<T> intersperse(final T value) {

        return (SortedSetX<T>) MutableCollectionX.super.intersperse(value);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#shuffle()
     */
    @Override
    default SortedSetX<T> shuffle() {

        return (SortedSetX<T>) MutableCollectionX.super.shuffle();
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#skipLast(int)
     */
    @Override
    default SortedSetX<T> skipLast(final int num) {

        return (SortedSetX<T>) MutableCollectionX.super.skipLast(num);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#limitLast(int)
     */
    @Override
    default SortedSetX<T> limitLast(final int num) {

        return (SortedSetX<T>) MutableCollectionX.super.limitLast(num);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.types.OnEmptySwitch#onEmptySwitch(java.util.function.Supplier)
     */
    @Override
    default SortedSetX<T> onEmptySwitch(final Supplier<? extends SortedSet<T>> supplier) {
        if (isEmpty())
            return SortedSetX.fromIterable(supplier.get());
        return this;
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#onEmpty(java.lang.Object)
     */
    @Override
    default SortedSetX<T> onEmpty(final T value) {

        return (SortedSetX<T>) MutableCollectionX.super.onEmpty(value);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#onEmptyGet(java.util.function.Supplier)
     */
    @Override
    default SortedSetX<T> onEmptyGet(final Supplier<? extends T> supplier) {

        return (SortedSetX<T>) MutableCollectionX.super.onEmptyGet(supplier);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#onEmptyThrow(java.util.function.Supplier)
     */
    @Override
    default <X extends Throwable> SortedSetX<T> onEmptyThrow(final Supplier<? extends X> supplier) {

        return (SortedSetX<T>) MutableCollectionX.super.onEmptyThrow(supplier);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#shuffle(java.util.Random)
     */
    @Override
    default SortedSetX<T> shuffle(final Random random) {

        return (SortedSetX<T>) MutableCollectionX.super.shuffle(random);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#ofType(java.lang.Class)
     */
    @Override
    default <U> SortedSetX<U> ofType(final Class<? extends U> type) {

        return (SortedSetX<U>) MutableCollectionX.super.ofType(type);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#filterNot(java.util.function.Predicate)
     */
    @Override
    default SortedSetX<T> filterNot(final Predicate<? super T> fn) {

        return (SortedSetX<T>) MutableCollectionX.super.filterNot(fn);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#notNull()
     */
    @Override
    default SortedSetX<T> notNull() {

        return (SortedSetX<T>) MutableCollectionX.super.notNull();
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#removeAll(java.util.stream.Stream)
     */
    @Override
    default SortedSetX<T> removeAll(final Stream<? extends T> stream) {

        return (SortedSetX<T>) MutableCollectionX.super.removeAll(stream);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#removeAll(java.lang.Iterable)
     */
    @Override
    default SortedSetX<T> removeAll(final Iterable<? extends T> it) {

        return (SortedSetX<T>) MutableCollectionX.super.removeAll(it);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#removeAll(java.lang.Object[])
     */
    @Override
    default SortedSetX<T> removeAll(final T... values) {

        return (SortedSetX<T>) MutableCollectionX.super.removeAll(values);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#retainAll(java.lang.Iterable)
     */
    @Override
    default SortedSetX<T> retainAll(final Iterable<? extends T> it) {

        return (SortedSetX<T>) MutableCollectionX.super.retainAll(it);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#retainAll(java.util.stream.Stream)
     */
    @Override
    default SortedSetX<T> retainAll(final Stream<? extends T> seq) {

        return (SortedSetX<T>) MutableCollectionX.super.retainAll(seq);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#retainAll(java.lang.Object[])
     */
    @Override
    default SortedSetX<T> retainAll(final T... values) {

        return (SortedSetX<T>) MutableCollectionX.super.retainAll(values);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#cast(java.lang.Class)
     */
    @Override
    default <U> SortedSetX<U> cast(final Class<? extends U> type) {

        return (SortedSetX<U>) MutableCollectionX.super.cast(type);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.collections.extensions.standard.MutableCollectionX#patternMatch(java.lang.Object, java.util.function.Function)
     */
    @Override
    default <R> SortedSetX<R> patternMatch(final Function<CheckValue1<T, R>, CheckValue1<T, R>> case1, final Supplier<? extends R> otherwise) {
        return (SortedSetX<R>) MutableCollectionX.super.patternMatch(case1, otherwise);
    }

    @Override
    default <C extends Collection<? super T>> SortedSetX<C> grouped(final int size, final Supplier<C> supplier) {

        return (SortedSetX<C>) MutableCollectionX.super.grouped(size, supplier);
    }

    @Override
    default SortedSetX<ListX<T>> groupedUntil(final Predicate<? super T> predicate) {

        return (SortedSetX<ListX<T>>) MutableCollectionX.super.groupedUntil(predicate);
    }

    @Override
    default SortedSetX<ListX<T>> groupedWhile(final Predicate<? super T> predicate) {

        return (SortedSetX<ListX<T>>) MutableCollectionX.super.groupedWhile(predicate);
    }

    @Override
    default <C extends Collection<? super T>> SortedSetX<C> groupedWhile(final Predicate<? super T> predicate, final Supplier<C> factory) {

        return (SortedSetX<C>) MutableCollectionX.super.groupedWhile(predicate, factory);
    }

    @Override
    default <C extends Collection<? super T>> SortedSetX<C> groupedUntil(final Predicate<? super T> predicate, final Supplier<C> factory) {

        return (SortedSetX<C>) MutableCollectionX.super.groupedUntil(predicate, factory);
    }

    @Override
    default SortedSetX<ListX<T>> groupedStatefullyUntil(final BiPredicate<ListX<? super T>, ? super T> predicate) {

        return (SortedSetX<ListX<T>>) MutableCollectionX.super.groupedStatefullyUntil(predicate);
    }

    @Override
    default SortedSetX<T> removeAll(final Seq<? extends T> stream) {

        return (SortedSetX<T>) MutableCollectionX.super.removeAll(stream);
    }

    @Override
    default SortedSetX<T> retainAll(final Seq<? extends T> stream) {

        return (SortedSetX<T>) MutableCollectionX.super.retainAll(stream);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.lambda.monads.ExtendedTraversable#permutations()
     */
    @Override
    default SortedSetX<ReactiveSeq<T>> permutations() {
        return fromStream(stream().permutations()
                                  .map(Comparables::comparable));

    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.lambda.monads.ExtendedTraversable#combinations(int)
     */
    @Override
    default SortedSetX<ReactiveSeq<T>> combinations(final int size) {
        return fromStream(stream().combinations(size)
                                  .map(Comparables::comparable));
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.lambda.monads.ExtendedTraversable#combinations()
     */
    @Override
    default SortedSetX<ReactiveSeq<T>> combinations() {
        return fromStream(stream().combinations()
                                  .map(Comparables::comparable));
    }

    /**
     * Narrow a covariant SortedSet
     * 
     * <pre>
     * {@code 
     * SortedSetX<? extends Fruit> set = SortedSetX.of(apple,bannana);
     * SortedSetX<Fruit> fruitSet = SortedSetX.narrow(set);
     * }
     * </pre>
     * 
     * @param sortedSetX to narrow generic type
     * @return SortedSetX with narrowed type
     */
    public static <T> SortedSetX<T> narrow(final SortedSetX<? extends T> setX) {
        return (SortedSetX<T>) setX;
    }

    static class Comparables {

        static <T, R extends ReactiveSeq<T> & Comparable<T>> R comparable(final Seq<T> seq) {
            return comparable(ReactiveSeq.fromStream(seq));
        }

        @SuppressWarnings("unchecked")

        static <T, R extends ReactiveSeq<T> & Comparable<T>> R comparable(final ReactiveSeq<T> seq) {
            final Method compareTo = Stream.of(Comparable.class.getMethods())
                                           .filter(m -> m.getName()
                                                         .equals("compareTo"))
                                           .findFirst()
                                           .get();

            return (R) Proxy.newProxyInstance(SortedSetX.class.getClassLoader(), new Class[] { ReactiveSeq.class, Comparable.class },
                                              (proxy, method, args) -> {
                                                  if (compareTo.equals(method))
                                                      return Objects.compare(System.identityHashCode(seq), System.identityHashCode(args[0]),
                                                                             Comparator.naturalOrder());
                                                  else
                                                      return method.invoke(seq, args);
                                              });

        }
    }

}
