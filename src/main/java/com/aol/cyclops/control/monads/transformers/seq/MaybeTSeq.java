package com.aol.cyclops.control.monads.transformers.seq;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;

import org.jooq.lambda.Collectable;
import org.jooq.lambda.Seq;
import org.jooq.lambda.tuple.Tuple2;
import org.jooq.lambda.tuple.Tuple3;
import org.jooq.lambda.tuple.Tuple4;

import com.aol.cyclops.Monoid;
import com.aol.cyclops.control.AnyM;
import com.aol.cyclops.control.Maybe;
import com.aol.cyclops.control.ReactiveSeq;
import com.aol.cyclops.control.monads.transformers.MaybeT;
import com.aol.cyclops.control.monads.transformers.values.ValueTransformerSeq;
import com.aol.cyclops.data.collections.extensions.standard.ListX;
import com.aol.cyclops.types.IterableFoldable;
import com.aol.cyclops.types.MonadicValue;
import com.aol.cyclops.types.Sequential;
import com.aol.cyclops.types.Traversable;
import com.aol.cyclops.types.anyM.AnyMSeq;
import com.aol.cyclops.types.stream.ConvertableSequence;
import com.aol.cyclops.types.stream.CyclopsCollectable;

/**
 * Monad transformer for  Maybe nested within Sequential or non-scalar data types (e.g. Lists, Streams etc)
 * 
 * MaybeT allows the deeply wrapped Maybe to be manipulating within it's nested
 * /contained context
 * 
 * 
 * @author johnmcclean
 *
 * @param <T>
 *            The type contained on the Maybe within
 */
public class MaybeTSeq<T>
        implements MaybeT<T>, ValueTransformerSeq<T>, IterableFoldable<T>, ConvertableSequence<T>, CyclopsCollectable<T>, Sequential<T> {

    public AnyMSeq<T> anyM() {

        return null;
    }

    private final AnyMSeq<Maybe<T>> run;

    private MaybeTSeq(final AnyMSeq<Maybe<T>> run) {
        this.run = run;
    }

    @Override
    public <T> MaybeTSeq<T> unitStream(final ReactiveSeq<T> traversable) {
        return MaybeT.fromStream(traversable.map(Maybe::just));

    }

    @Override
    public <T> MaybeTSeq<T> unitAnyM(final AnyM<Traversable<T>> traversable) {

        return of((AnyMSeq) traversable.map(t -> Maybe.fromIterable(t)));
    }

    @Override
    public AnyMSeq<? extends Traversable<T>> transformerStream() {

        return run.map(m -> m.toListX());
    }

    /**
     * @return The wrapped AnyM
     */
    @Override
    public AnyMSeq<Maybe<T>> unwrap() {
        return run;
    }

    /**
     * Peek at the current value of the Maybe
     * 
     * <pre>
     * {@code 
     *    MaybeT.of(AnyM.fromStream(Maybe.of(10))
     *             .peek(System.out::println);
     *             
     *     //prints 10        
     * }
     * </pre>
     * 
     * @param peek
     *            Consumer to accept current value of Maybe
     * @return MaybeT with peek call
     */
    @Override
    public MaybeTSeq<T> peek(final Consumer<? super T> peek) {
        return of(run.peek(opt -> opt.map(a -> {
            peek.accept(a);
            return a;
        })));
    }

    /**
     * Filter the wrapped Maybe
     * 
     * <pre>
     * {@code 
     *    MaybeT.of(AnyM.fromStream(Maybe.of(10))
     *             .filter(t->t!=10);
     *             
     *     //MaybeT<AnyMSeq<Stream<Maybe.empty>>>
     * }
     * </pre>
     * 
     * @param test
     *            Predicate to filter the wrapped Maybe
     * @return MaybeT that applies the provided filter
     */
    @Override
    public MaybeTSeq<T> filter(final Predicate<? super T> test) {
        return of(run.map(opt -> opt.filter(test)));
    }

    /**
     * Map the wrapped Maybe
     * 
     * <pre>
     * {@code 
     *  MaybeT.of(AnyM.fromStream(Maybe.of(10))
     *             .map(t->t=t+1);
     *  
     *  
     *  //MaybeT<AnyMSeq<Stream<Maybe[11]>>>
     * }
     * </pre>
     * 
     * @param f
     *            Mapping function for the wrapped Maybe
     * @return MaybeT that applies the map function to the wrapped Maybe
     */
    @Override
    public <B> MaybeTSeq<B> map(final Function<? super T, ? extends B> f) {
        return new MaybeTSeq<B>(
                                run.map(o -> o.map(f)));
    }

    /**
     * Flat Map the wrapped Maybe
     * 
     * <pre>
    * {@code 
    *  MaybeT.of(AnyM.fromStream(Maybe.of(10))
    *             .flatMap(t->Maybe.empty();
    *  
    *  
    *  //MaybeT<AnyMSeq<Stream<Maybe.empty>>>
    * }
     * </pre>
     * 
     * @param f
     *            FlatMap function
     * @return MaybeT that applies the flatMap function to the wrapped Maybe
     */
    public <B> MaybeTSeq<B> flatMapT(final Function<? super T, MaybeTSeq<? extends B>> f) {

        return of(run.bind(opt -> {
            if (opt.isPresent())
                return f.apply(opt.get()).run.unwrap();
            return run.unit(Maybe.<B> none())
                      .unwrap();
        }));

    }

    @Override
    public <B> MaybeTSeq<B> flatMap(final Function<? super T, ? extends MonadicValue<? extends B>> f) {

        return new MaybeTSeq<B>(
                                run.map(o -> o.flatMap(f)));

    }

    /**
     * Lift a function into one that accepts and returns an MaybeT This allows
     * multiple monad types to add functionality to existing functions and
     * methods
     * 
     * e.g. to add null handling (via Maybe) and iteration (via Stream) to an
     * existing function
     * 
     * <pre>
     * {
     *     &#64;code
     *     Function<Integer, Integer> add2 = i -> i + 2;
     *     Function<MaybeT<Integer>, MaybeT<Integer>> optTAdd2 = MaybeT.lift(add2);
     * 
     *     Stream<Integer> withNulls = Stream.of(1, 2, null);
     *     AnyMSeq<Integer> stream = AnyM.ofMonad(withNulls);
     *     AnyMSeq<Maybe<Integer>> streamOpt = stream.map(Maybe::ofNullable);
     *     List<Integer> results = optTAdd2.apply(MaybeT.of(streamOpt)).unwrap().<Stream<Maybe<Integer>>> unwrap()
     *             .filter(Maybe::isPresent).map(Maybe::get).collect(Collectors.toList());
     * 
     *     // Arrays.asList(3,4);
     * 
     * }
     * </pre>
     * 
     * 
     * @param fn
     *            Function to enhance with functionality from Maybe and another
     *            monad type
     * @return Function that accepts and returns an MaybeT
     */
    public static <U, R> Function<MaybeTSeq<U>, MaybeTSeq<R>> lift(final Function<? super U, ? extends R> fn) {
        return optTu -> optTu.map(input -> fn.apply(input));
    }

    /**
     * Lift a BiFunction into one that accepts and returns MaybeTs This allows
     * multiple monad types to add functionality to existing functions and
     * methods
     * 
     * e.g. to add null handling (via Maybe), iteration (via Stream) and
     * asynchronous execution (CompletableFuture) to an existing function
     * 
     * <pre>
     * {
     *     &#64;code
     *     BiFunction<Integer, Integer, Integer> add = (a, b) -> a + b;
     *     BiFunction<MaybeT<Integer>, MaybeT<Integer>, MaybeT<Integer>> optTAdd2 = MaybeT.lift2(add);
     * 
     *     Stream<Integer> withNulls = Stream.of(1, 2, null);
     *     AnyMSeq<Integer> stream = AnyM.ofMonad(withNulls);
     *     AnyMSeq<Maybe<Integer>> streamOpt = stream.map(Maybe::ofNullable);
     * 
     *     CompletableFuture<Maybe<Integer>> two = CompletableFuture.supplyAsync(() -> Maybe.of(2));
     *     AnyMSeq<Maybe<Integer>> future = AnyM.ofMonad(two);
     *     List<Integer> results = optTAdd2.apply(MaybeT.of(streamOpt), MaybeT.of(future)).unwrap()
     *             .<Stream<Maybe<Integer>>> unwrap().filter(Maybe::isPresent).map(Maybe::get)
     *             .collect(Collectors.toList());
     *     // Arrays.asList(3,4);
     * }
     * </pre>
     * 
     * @param fn
     *            BiFunction to enhance with functionality from Maybe and
     *            another monad type
     * @return Function that accepts and returns an MaybeT
     */
    public static <U1, U2, R> BiFunction<MaybeTSeq<U1>, MaybeTSeq<U2>, MaybeTSeq<R>> lift2(final BiFunction<? super U1, ? super U2, ? extends R> fn) {
        return (optTu1, optTu2) -> optTu1.flatMapT(input1 -> optTu2.map(input2 -> fn.apply(input1, input2)));
    }

    /**
     * Construct an MaybeT from an AnyM that contains a monad type that contains
     * type other than Maybe The values in the underlying monad will be mapped
     * to Maybe<A>
     * 
     * @param anyM
     *            AnyM that doesn't contain a monad wrapping an Maybe
     * @return MaybeT
     */
    public static <A> MaybeTSeq<A> fromAnyM(final AnyMSeq<A> anyM) {
        return of(anyM.map(Maybe::ofNullable));
    }

    /**
     * Construct an MaybeT from an AnyM that wraps a monad containing Maybes
     * 
     * @param monads
     *            AnyM that contains a monad wrapping an Maybe
     * @return MaybeT
     */
    public static <A> MaybeTSeq<A> of(final AnyMSeq<Maybe<A>> monads) {

        return new MaybeTSeq<>(
                               monads);
    }

    public static <A> MaybeTSeq<A> of(final Maybe<A> monads) {
        return MaybeT.fromIterable(ListX.of(monads));
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return String.format("MaybeTSeq[%s]", run);
    }

    public boolean isPresent() {
        return run.anyMatch(m -> m.isPresent());
    }

    @Override
    public <R> MaybeTSeq<R> unit(final R value) {
        return of(run.unit(Maybe.of(value)));
    }

    @Override
    public <R> MaybeTSeq<R> empty() {
        return of(run.unit(Maybe.none()));
    }

    @Override
    public ReactiveSeq<T> stream() {
        return run.stream()
                  .flatMapIterable(e -> e);
    }

    @Override
    public Iterator<T> iterator() {
        return stream().iterator();
    }

    public <R> MaybeTSeq<R> unitIterator(final Iterator<R> it) {
        return of(run.unitIterator(it)
                     .map(i -> Maybe.just(i)));
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.types.stream.CyclopsCollectable#collectable()
     */
    @Override
    public Collectable<T> collectable() {
        return stream();
    }

    @Override
    public boolean isSeqPresent() {
        return !run.isEmpty();
    }

    public static <T> MaybeTSeq<T> emptyList() {
        return MaybeT.fromIterable(ListX.of());
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#combine(java.util.function.BiPredicate, java.util.function.BinaryOperator)
     */
    @Override
    public MaybeTSeq<T> combine(final BiPredicate<? super T, ? super T> predicate, final BinaryOperator<T> op) {

        return (MaybeTSeq<T>) ValueTransformerSeq.super.combine(predicate, op);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#cycle(int)
     */
    @Override
    public MaybeTSeq<T> cycle(final int times) {

        return (MaybeTSeq<T>) ValueTransformerSeq.super.cycle(times);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#cycle(com.aol.cyclops.Monoid, int)
     */
    @Override
    public MaybeTSeq<T> cycle(final Monoid<T> m, final int times) {

        return (MaybeTSeq<T>) ValueTransformerSeq.super.cycle(m, times);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#cycleWhile(java.util.function.Predicate)
     */
    @Override
    public MaybeTSeq<T> cycleWhile(final Predicate<? super T> predicate) {

        return (MaybeTSeq<T>) ValueTransformerSeq.super.cycleWhile(predicate);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#cycleUntil(java.util.function.Predicate)
     */
    @Override
    public MaybeTSeq<T> cycleUntil(final Predicate<? super T> predicate) {

        return (MaybeTSeq<T>) ValueTransformerSeq.super.cycleUntil(predicate);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#zip(java.lang.Iterable, java.util.function.BiFunction)
     */
    @Override
    public <U, R> MaybeTSeq<R> zip(final Iterable<? extends U> other, final BiFunction<? super T, ? super U, ? extends R> zipper) {

        return (MaybeTSeq<R>) ValueTransformerSeq.super.zip(other, zipper);
    }

    @Override
    public <U, R> MaybeTSeq<R> zip(final Seq<? extends U> other, final BiFunction<? super T, ? super U, ? extends R> zipper) {

        return (MaybeTSeq<R>) ValueTransformerSeq.super.zip(other, zipper);
    }

    @Override
    public <U, R> MaybeTSeq<R> zip(final Stream<? extends U> other, final BiFunction<? super T, ? super U, ? extends R> zipper) {

        return (MaybeTSeq<R>) ValueTransformerSeq.super.zip(other, zipper);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#zipStream(java.util.stream.Stream)
     */
    @Override
    public <U> MaybeTSeq<Tuple2<T, U>> zip(final Stream<? extends U> other) {

        return (MaybeTSeq) ValueTransformerSeq.super.zip(other);
    }

    @Override
    public <U> MaybeTSeq<Tuple2<T, U>> zip(final Iterable<? extends U> other) {

        return (MaybeTSeq) ValueTransformerSeq.super.zip(other);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#zip(org.jooq.lambda.Seq)
     */
    @Override
    public <U> MaybeTSeq<Tuple2<T, U>> zip(final Seq<? extends U> other) {

        return (MaybeTSeq) ValueTransformerSeq.super.zip(other);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#zip3(java.util.stream.Stream, java.util.stream.Stream)
     */
    @Override
    public <S, U> MaybeTSeq<Tuple3<T, S, U>> zip3(final Stream<? extends S> second, final Stream<? extends U> third) {

        return (MaybeTSeq) ValueTransformerSeq.super.zip3(second, third);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#zip4(java.util.stream.Stream, java.util.stream.Stream, java.util.stream.Stream)
     */
    @Override
    public <T2, T3, T4> MaybeTSeq<Tuple4<T, T2, T3, T4>> zip4(final Stream<? extends T2> second, final Stream<? extends T3> third,
            final Stream<? extends T4> fourth) {

        return (MaybeTSeq) ValueTransformerSeq.super.zip4(second, third, fourth);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#zipWithIndex()
     */
    @Override
    public MaybeTSeq<Tuple2<T, Long>> zipWithIndex() {

        return (MaybeTSeq<Tuple2<T, Long>>) ValueTransformerSeq.super.zipWithIndex();
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#sliding(int)
     */
    @Override
    public MaybeTSeq<ListX<T>> sliding(final int windowSize) {

        return (MaybeTSeq<ListX<T>>) ValueTransformerSeq.super.sliding(windowSize);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#sliding(int, int)
     */
    @Override
    public MaybeTSeq<ListX<T>> sliding(final int windowSize, final int increment) {

        return (MaybeTSeq<ListX<T>>) ValueTransformerSeq.super.sliding(windowSize, increment);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#grouped(int, java.util.function.Supplier)
     */
    @Override
    public <C extends Collection<? super T>> MaybeTSeq<C> grouped(final int size, final Supplier<C> supplier) {

        return (MaybeTSeq<C>) ValueTransformerSeq.super.grouped(size, supplier);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#groupedUntil(java.util.function.Predicate)
     */
    @Override
    public MaybeTSeq<ListX<T>> groupedUntil(final Predicate<? super T> predicate) {

        return (MaybeTSeq<ListX<T>>) ValueTransformerSeq.super.groupedUntil(predicate);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#groupedStatefullyUntil(java.util.function.BiPredicate)
     */
    @Override
    public MaybeTSeq<ListX<T>> groupedStatefullyUntil(final BiPredicate<ListX<? super T>, ? super T> predicate) {

        return (MaybeTSeq<ListX<T>>) ValueTransformerSeq.super.groupedStatefullyUntil(predicate);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#groupedWhile(java.util.function.Predicate)
     */
    @Override
    public MaybeTSeq<ListX<T>> groupedWhile(final Predicate<? super T> predicate) {

        return (MaybeTSeq<ListX<T>>) ValueTransformerSeq.super.groupedWhile(predicate);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#groupedWhile(java.util.function.Predicate, java.util.function.Supplier)
     */
    @Override
    public <C extends Collection<? super T>> MaybeTSeq<C> groupedWhile(final Predicate<? super T> predicate, final Supplier<C> factory) {

        return (MaybeTSeq<C>) ValueTransformerSeq.super.groupedWhile(predicate, factory);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#groupedUntil(java.util.function.Predicate, java.util.function.Supplier)
     */
    @Override
    public <C extends Collection<? super T>> MaybeTSeq<C> groupedUntil(final Predicate<? super T> predicate, final Supplier<C> factory) {

        return (MaybeTSeq<C>) ValueTransformerSeq.super.groupedUntil(predicate, factory);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#grouped(int)
     */
    @Override
    public MaybeTSeq<ListX<T>> grouped(final int groupSize) {

        return (MaybeTSeq<ListX<T>>) ValueTransformerSeq.super.grouped(groupSize);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#grouped(java.util.function.Function, java.util.stream.Collector)
     */
    @Override
    public <K, A, D> MaybeTSeq<Tuple2<K, D>> grouped(final Function<? super T, ? extends K> classifier, final Collector<? super T, A, D> downstream) {

        return (MaybeTSeq) ValueTransformerSeq.super.grouped(classifier, downstream);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#grouped(java.util.function.Function)
     */
    @Override
    public <K> MaybeTSeq<Tuple2<K, Seq<T>>> grouped(final Function<? super T, ? extends K> classifier) {

        return (MaybeTSeq) ValueTransformerSeq.super.grouped(classifier);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#distinct()
     */
    @Override
    public MaybeTSeq<T> distinct() {

        return (MaybeTSeq<T>) ValueTransformerSeq.super.distinct();
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#scanLeft(com.aol.cyclops.Monoid)
     */
    @Override
    public MaybeTSeq<T> scanLeft(final Monoid<T> monoid) {

        return (MaybeTSeq<T>) ValueTransformerSeq.super.scanLeft(monoid);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#scanLeft(java.lang.Object, java.util.function.BiFunction)
     */
    @Override
    public <U> MaybeTSeq<U> scanLeft(final U seed, final BiFunction<? super U, ? super T, ? extends U> function) {

        return (MaybeTSeq<U>) ValueTransformerSeq.super.scanLeft(seed, function);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#scanRight(com.aol.cyclops.Monoid)
     */
    @Override
    public MaybeTSeq<T> scanRight(final Monoid<T> monoid) {

        return (MaybeTSeq<T>) ValueTransformerSeq.super.scanRight(monoid);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#scanRight(java.lang.Object, java.util.function.BiFunction)
     */
    @Override
    public <U> MaybeTSeq<U> scanRight(final U identity, final BiFunction<? super T, ? super U, ? extends U> combiner) {

        return (MaybeTSeq<U>) ValueTransformerSeq.super.scanRight(identity, combiner);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#sorted()
     */
    @Override
    public MaybeTSeq<T> sorted() {

        return (MaybeTSeq<T>) ValueTransformerSeq.super.sorted();
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#sorted(java.util.Comparator)
     */
    @Override
    public MaybeTSeq<T> sorted(final Comparator<? super T> c) {

        return (MaybeTSeq<T>) ValueTransformerSeq.super.sorted(c);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#takeWhile(java.util.function.Predicate)
     */
    @Override
    public MaybeTSeq<T> takeWhile(final Predicate<? super T> p) {

        return (MaybeTSeq<T>) ValueTransformerSeq.super.takeWhile(p);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#dropWhile(java.util.function.Predicate)
     */
    @Override
    public MaybeTSeq<T> dropWhile(final Predicate<? super T> p) {

        return (MaybeTSeq<T>) ValueTransformerSeq.super.dropWhile(p);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#takeUntil(java.util.function.Predicate)
     */
    @Override
    public MaybeTSeq<T> takeUntil(final Predicate<? super T> p) {

        return (MaybeTSeq<T>) ValueTransformerSeq.super.takeUntil(p);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#dropUntil(java.util.function.Predicate)
     */
    @Override
    public MaybeTSeq<T> dropUntil(final Predicate<? super T> p) {

        return (MaybeTSeq<T>) ValueTransformerSeq.super.dropUntil(p);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#dropRight(int)
     */
    @Override
    public MaybeTSeq<T> dropRight(final int num) {

        return (MaybeTSeq<T>) ValueTransformerSeq.super.dropRight(num);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#takeRight(int)
     */
    @Override
    public MaybeTSeq<T> takeRight(final int num) {

        return (MaybeTSeq<T>) ValueTransformerSeq.super.takeRight(num);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#skip(long)
     */
    @Override
    public MaybeTSeq<T> skip(final long num) {

        return (MaybeTSeq<T>) ValueTransformerSeq.super.skip(num);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#skipWhile(java.util.function.Predicate)
     */
    @Override
    public MaybeTSeq<T> skipWhile(final Predicate<? super T> p) {

        return (MaybeTSeq<T>) ValueTransformerSeq.super.skipWhile(p);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#skipUntil(java.util.function.Predicate)
     */
    @Override
    public MaybeTSeq<T> skipUntil(final Predicate<? super T> p) {

        return (MaybeTSeq<T>) ValueTransformerSeq.super.skipUntil(p);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#limit(long)
     */
    @Override
    public MaybeTSeq<T> limit(final long num) {

        return (MaybeTSeq<T>) ValueTransformerSeq.super.limit(num);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#limitWhile(java.util.function.Predicate)
     */
    @Override
    public MaybeTSeq<T> limitWhile(final Predicate<? super T> p) {

        return (MaybeTSeq<T>) ValueTransformerSeq.super.limitWhile(p);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#limitUntil(java.util.function.Predicate)
     */
    @Override
    public MaybeTSeq<T> limitUntil(final Predicate<? super T> p) {

        return (MaybeTSeq<T>) ValueTransformerSeq.super.limitUntil(p);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#intersperse(java.lang.Object)
     */
    @Override
    public MaybeTSeq<T> intersperse(final T value) {

        return (MaybeTSeq<T>) ValueTransformerSeq.super.intersperse(value);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#reverse()
     */
    @Override
    public MaybeTSeq<T> reverse() {

        return (MaybeTSeq<T>) ValueTransformerSeq.super.reverse();
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#shuffle()
     */
    @Override
    public MaybeTSeq<T> shuffle() {

        return (MaybeTSeq<T>) ValueTransformerSeq.super.shuffle();
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#skipLast(int)
     */
    @Override
    public MaybeTSeq<T> skipLast(final int num) {

        return (MaybeTSeq<T>) ValueTransformerSeq.super.skipLast(num);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#limitLast(int)
     */
    @Override
    public MaybeTSeq<T> limitLast(final int num) {

        return (MaybeTSeq<T>) ValueTransformerSeq.super.limitLast(num);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#onEmpty(java.lang.Object)
     */
    @Override
    public MaybeTSeq<T> onEmpty(final T value) {

        return (MaybeTSeq<T>) ValueTransformerSeq.super.onEmpty(value);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#onEmptyGet(java.util.function.Supplier)
     */
    @Override
    public MaybeTSeq<T> onEmptyGet(final Supplier<? extends T> supplier) {

        return (MaybeTSeq<T>) ValueTransformerSeq.super.onEmptyGet(supplier);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#onEmptyThrow(java.util.function.Supplier)
     */
    @Override
    public <X extends Throwable> MaybeTSeq<T> onEmptyThrow(final Supplier<? extends X> supplier) {

        return (MaybeTSeq<T>) ValueTransformerSeq.super.onEmptyThrow(supplier);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#shuffle(java.util.Random)
     */
    @Override
    public MaybeTSeq<T> shuffle(final Random random) {

        return (MaybeTSeq<T>) ValueTransformerSeq.super.shuffle(random);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#slice(long, long)
     */
    @Override
    public MaybeTSeq<T> slice(final long from, final long to) {

        return (MaybeTSeq<T>) ValueTransformerSeq.super.slice(from, to);
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.control.monads.transformers.values.Traversable#sorted(java.util.function.Function)
     */
    @Override
    public <U extends Comparable<? super U>> MaybeTSeq<T> sorted(final Function<? super T, ? extends U> function) {
        return (MaybeTSeq) ValueTransformerSeq.super.sorted(function);
    }

    @Override
    public int hashCode() {
        return run.hashCode();
    }

    @Override
    public boolean equals(final Object o) {
        if (o instanceof MaybeTSeq) {
            return run.equals(((MaybeTSeq) o).run);
        }
        return false;
    }
}
