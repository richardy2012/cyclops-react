package com.aol.cyclops.control;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jooq.lambda.tuple.Tuple;
import org.jooq.lambda.tuple.Tuple1;
import org.jooq.lambda.tuple.Tuple2;
import org.jooq.lambda.tuple.Tuple3;
import org.jooq.lambda.tuple.Tuple4;
import org.jooq.lambda.tuple.Tuple5;

import com.aol.cyclops.Matchables;
import com.aol.cyclops.internal.UNSET;
import com.aol.cyclops.internal.matcher2.ADTPredicateBuilder;
import com.aol.cyclops.internal.matcher2.MatchableCase;
import com.aol.cyclops.internal.matcher2.MatchingInstance;
import com.aol.cyclops.internal.matcher2.PatternMatcher;
import com.aol.cyclops.internal.matcher2.SeqUtils;
import com.aol.cyclops.types.Value;
import com.aol.cyclops.util.function.Predicates;
import com.aol.cyclops.util.function.QuadFunction;
import com.aol.cyclops.util.function.QuintFunction;
import com.aol.cyclops.util.function.TriFunction;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Matchable
 * 
 * Gateway to the guard based pattern matching API.
 * 
 * Supports Structural Pattern matching
 * 
 * Matchable uses a when / then / otherwise DSL.
 * The Matchable class provides static when / then /otherwise methods @see {@link Matchable#then(Supplier)} {@link Matchable#when(Predicate)} {@link Matchable#otherwise(Supplier)}
 * 
 * 
 * 
 * @see {@link Matchables} for precanned structural pattern matching against JDK classes
 * 
 * Use Matchable#of or Matchable#matchable (an alias for use with static imports) to pattern match via guards on an Object.
 *
 * <pre>
 * {@code 
 * import static com.aol.cyclops.control.Matchable.otherwise;
   import static com.aol.cyclops.control.Matchable.then;
   import static com.aol.cyclops.control.Matchable.when;
   import static com.aol.cyclops.util.function.Predicates.decons;
   
 *  Customer customer = new Customer("test",new Address(10,"hello","my city"));
 *  String result =   customer.match()
                              .on$_2() //match on address
                              .matches(c->c.is(when(decons(when(10,"hello","my city"))),then("hello")), 
                                       otherwise("miss"))
                              .get();
      
      //"hello"
 * 
 * @AllArgsConstructor
    static class Address{
        int house;
        String street;
        String city;
        
        public MTuple3<Integer,String,String> match(){
            return Matchables.supplier3(()->house,()->street,()->city);
        }
    }
    @AllArgsConstructor
    static class Customer{
        String name;
        Address address;
        public MTuple2<String,MTuple3<Integer,String,String>> match(){
            return Matchables.supplier2(()->name,()->Maybe.ofNullable(address).map(a->a.match()).orElseGet(()->null));
        }
    }
 * }
 * </pre>
 *
 *
 *
 * Matchable supports tail recursion
 * 
 * <pre>
 * {@code 
 * 
 * import static com.aol.cyclops.control.Matchable.matchable;
 * 
 *  @Test
    public void oddRun(){
        System.out.println(even(Eval.now(200000)).get());
    }
    public Eval<String> odd(Eval<Integer> n )  {
       
       return n.flatMap(x->even(Eval.now(x-1)));
    }
    public Eval<String> even(Eval<Integer> n )  {
        return n.flatMap(x->{
            return matchable(x).matches(c->c.is(when(lessThanOrEquals(0)), then(()->"done")), 
                                                    odd(Eval.now(x-1)));
        });
     }
 * 
 * }
 * </pre>
 * 
 * cyclops-react pattern matching uses the is /has methods to denote individual cases.

    is : indicates exact matching

    has : indicates that the supplied values must be present in order

    <pre>
    {@code 
        is(when(1,2,3),then("match")) : must have values 1,2,3 only in that order

        has(when(1,2,3),then("match")) : must have values 1,2,3 in that order, but may also have additional values
    }
    </pre>
 * 
 * @author johnmcclean
 *
 */
public interface Matchable<TYPE> {

    /**
     * Match on the supplied Object
     * 
     * <pre>
     * {@code 
     * Matchable.matchable(100)
                 .matches(c->c.is(when(greaterThan(50)), ()->"large"), ()->"small");
       
       //Eval["large"]
     * }
     * </pre>
     * 
     * For a structural equivalent, matching on a single object @see {@link Matchables#match(Object)}
     * 
     * 
     * @param o
     * @return
     */
    public static <T> MTuple1<T> matchable(final T o) {
        return of(o);
    }

    /**
     * Create a new matchable that will match on the fields of the provided Object
     * 
     * <pre>
     * {@code 
     * import static com.aol.cyclops.util.function.Predicates.__;
     * import static com.aol.cyclops.control.Matchable.whenGuard;
     * import static com.aol.cyclops.util.function.Predicates.has;
     * 
     * Matchable.of(new NestedCase(1,2,new NestedCase(3,4,null))) 
                 .matches(cases->cases.is(whenGuard(1,__,has(3,4,__)),then("2")),otherwise("4"))
     * 
     * }</pre>
     * 
     * @param o Object to match on it's fields
     * @return new Matchable
     */
    public static <T> MTuple1<T> of(final T o) {

        return Matchables.match(o);
    }

    /**
    * Create a new matchable that will match on the fields of the provided Stream
    * 
    * @param o Object to match on it's fields
    * @return new Matchable
    */
    public static <T> MatchableObject<T> of(final Stream<T> o) {
        return AsMatchable.asMatchable(o.collect(Collectors.toList()));
    }

    /**
     * Matchable DSL operator for outcomes
     * 
     * <pre>
     * {@code 
     * import static com.aol.cyclops.control.Matchable.otherwise;
       import static com.aol.cyclops.control.Matchable.then;
       import static com.aol.cyclops.control.Matchable.when;
       import static com.aol.cyclops.util.function.Predicates.instanceOf;
       
       Eval<Integer> result = Matchables.future(CompletableFuture.completedFuture(10))
                                         .matches(c-> 
                                                     c.is( when(some(10)), then(20)),  //success
                                                      
                                                     c->c.is(when(instanceOf(RuntimeException.class)), then(2)), //failure
                                                      
                                                     otherwise(3) //no match
                                                 );
        
        //Eval.now(20)
    
     * 
     * }
     * </pre>
     * 
     * @param value Result value
     * @return Supplier for outcomes in the Pattern matching DSL
     */
    public static <T, R> Supplier<? extends R> then(final R value) {
        return () -> value;
    }

    /**
     * Matchable DSL operator for outcomes
     * 
     * <pre>
     * {@code 
     * import static com.aol.cyclops.control.Matchable.otherwise;
       import static com.aol.cyclops.control.Matchable.then;
       import static com.aol.cyclops.control.Matchable.when;
       
     *  Maybe.just(2).matches(c->c.is(when(1),then("hello"))
                                  .is(when(2),then(()->load(1)))
                                  .is(when(3),then(()->"hello")),otherwise("miss"));
                                  
     *  //Eval[load(1)]
     * }
     * </pre>
     * 
     * 
     * @param fn Supplier that generates the result value
     * @return Supplier for outcomes in the Pattern matching DSL
     */
    public static <T, R> Supplier<? extends R> then(final Supplier<? extends R> fn) {
        return fn;
    }

    /**
     * Matchable DSL operator for default results when no match is found
     * 
     * <pre>
     * {@code 
     *  import static com.aol.cyclops.control.Matchable.otherwise;
       import static com.aol.cyclops.control.Matchable.then;
       import static com.aol.cyclops.control.Matchable.when;
       
     * Try.success(30).matches(c->c.is(when(10),then("hello")),
                               c->c.is(when(instanceOf(Throwable.class)), then("error")),
                              otherwise("miss")).get()
                              
     * //miss
     *  
     * }</pre>
     * 
     * 
     * @param value Defult value
     * @return Supplier for default results in Pattern matching DSL
     */
    public static <R> Supplier<R> otherwise(final R value) {
        return () -> value;
    }

    /**
     * 
     * Matchable DSL operator for default results when no match is found
     * 
     * <pre>
     * {@code 
     * import static com.aol.cyclops.control.Matchable.otherwise;
       import static com.aol.cyclops.control.Matchable.then;
       import static com.aol.cyclops.control.Matchable.when;
     * 
       Try.success(30).matches(c->c.is(when(10),then("hello")),
                               c->c.is(when(instanceOf(Throwable.class)), then("error")),
                               otherwise(()->"miss")).get()
                              
     * //miss
     * }</pre>
     * 
     * @param s Supplier that generates a default value
     * @return Supplier for default results in Pattern matching DSL
     */
    public static <R> Supplier<R> otherwise(final Supplier<? extends R> s) {
        return (Supplier<R>) s;
    }

    @SafeVarargs
    public static <T, V> Iterable<Predicate<? super T>> whenGuard2(final V... values) {
        return (Iterable) () -> ReactiveSeq.of(values)
                                           .map(v -> ADTPredicateBuilder.convertToPredicateTyped(v))
                                           .iterator();
    }

    /**
     * Matchable DSL operator for matching recursively
     * 
     * <pre>
     * {@code 
     *  //given a recursively defined Case class
     *  @Value
        public class NestedCase implements  Decomposable{
        int a;
        int b;
        NestedCase c;
       }
      
       import static com.aol.cyclops.control.Matchable.otherwise;
       import static com.aol.cyclops.control.Matchable.then;
       import static com.aol.cyclops.control.Matchable.whenGuard;
       import static com.aol.cyclops.util.function.Predicates.__;
       import static com.aol.cyclops.util.function.Predicates.type;
     * 
     * 
     * 
     * 
     * Matchable.of(new NestedCase(1,2,new NestedCase(3,4,null)))
            .matches(c->c.is(whenGuard(1,__,type(NestedCase.class).hasGuard(3,4,__)),then("2")),
                    otherwise("-1"))
            .get();
     * //"2"
     * }
     * </pre>
     * 
     * 
     * 
     * @param values To match target against with potential for recursively matching within provided values
     * @return Pattern Matcher for further matching
     */
    @SafeVarargs
    public static <T, V> MTuple1<Predicate<? super T>> whenGuard(final V... values) {
        return () -> Tuple.tuple((final T in) -> ReactiveSeq.of(values)
                                                            .map(v -> ADTPredicateBuilder.convertToPredicateTyped(v))
                                                            .allMatch(p -> p.test(in)));
    }

    /**
     * Create an Iterable of Predicates that check for equality against the provided values
     * 
     * @param t1 Values to create equality check predicates for
     * @return Iterable of Predicates that check equality against the provided array
     */
    public static <T1> Iterable<Predicate<? super T1>> whenValues(final T1... t1){

        return (List) ReactiveSeq.of(t1)
                                 .map(Predicates::eq)
                                 .toList();
    }

    public static <T1> Iterable<Predicate<? super T1>> whenTrue(final Predicate<? super T1>... t1) {

        return ReactiveSeq.of(t1)
                          .toList();
    }

    //when arity 1
    /**
     * Matchable DSL operator for matching against a single value
     * 
     * <pre>
     * {@code 
     * import static com.aol.cyclops.control.Matchable.when;
     * import static com.aol.cyclops.control.Matchable.then;
     * import static com.aol.cyclops.control.Matchable.otherwise;
     * 
     * Eval.later(()->10)
     *     .matches(c->c.is(when(10),then("hello")),
     *              otherwise("miss"))
     * 
     * //Eval["hello"]
     * }
     * </pre>
     * 
     * 
     * 
     * @param t1 Value to match against (equality check)
     * @return Pattern Matcher for further matching
     */
    public static <T1> MTuple1<Predicate<? super T1>> when(final T1 t1) {

        return () -> Tuple.tuple(test -> Objects.equals(test, t1));
    }

    /**
     * Matchable DSL operator for matching against a single value
     * 
     * <pre>
     * {@code 
     *   
     *   FutureW.ofResult(10)
     *          .map(i->throw new RuntimeException())
     *          .matches(c->c.is(when(10),then("hello")).is(when(2),then("hello")),
                         c->c.is(when(Predicates.instanceOf(Throwable.class)), then("error")),
                         otherwise("miss"));
                                           
     * //Eval["error"]
     * }
     * </pre>
     * 
     * @param t1 Predicate to match against
     * @return Pattern Matcher for further matching
     */
    public static <T1> MTuple1<Predicate<? super T1>> when(final Predicate<? super T1> t1) {

        return () -> Tuple.tuple(t1);
    }

    //when arity 2

    /**
     * Matchable DSL operator for matching against two values
     * 
     * 
     * <pre>
     * {@code  
     *  URL url =  ReactiveSeq.of("input.file")
                           .map(getClass().getClassLoader()::getResource)
                           .single();
       
       //Match on the first two lines of a file defined by an URL
        * 
       String result = Matchables.lines(url)
                                 .on$12___()
                                 .matches(c->c.is(when("hello","world2"),then("incorrect"))
                                              .is(when("hello","world"),then("correct")), 
                                          otherwise("miss"))
                                 .get();
       //"correct"
       
       }
     * @param t1 1st value to match against (equality check)
     * @param t2 2nd value to match against (equality check)
     * @return Pattern Matcher for further matching
     */
    public static <T1, T2> MTuple2<Predicate<? super T1>, Predicate<? super T2>> when(final T1 t1, final T2 t2) {

        return () -> Tuple.tuple(test -> Objects.equals(test, t1), test -> Objects.equals(test, t2));
    }

    /**
     * Matchable DSL operator for matching against two values using predicates
     * 
     * <pre>
     * {@code 
     * Matchables.match2(100,2)
                .matches(c->c.is(when(Predicates.greaterThan(50),Predicates.lessThan(10)), ()->"large and small"), ()->"not large and small")
                .get(),
        //"large and small"
     * 
     * }
     * </pre>
     * 
     * 
     * @param t1 1st value to match against
     * @param t2 2nd value to match against
     * @return Pattern Matcher for further matching
     */
    public static <T1, T2, T3> MTuple2<Predicate<? super T1>, Predicate<? super T2>> when(final Predicate<? super T1> t1,
            final Predicate<? super T2> t2) {

        return () -> Tuple.tuple(t1, t2);
    }

    //when arity 3
    /**
     * Matchable DSL operator for matching against three values 
     * <pre>
     * {@code 
     * 
     *  Predicates.decons(when(10,"hello","my city"))
     *            .test(new Address(10,"hello","my city").match()));
     *  //true          
       
       String result =  new Customer("test",new Address(10,"hello","my city"))
                                .match()
                                .on$_2()
                                .matches(c->c.is(when(decons(when(10,"hello","my city"))),then("hello")), otherwise("miss")).get();
      
        //"hello")
     * 
     * }
     * </pre>
     * 
     * @param t1 1st value to match against
     * @param t2 2nd value to match against
     * @param t3 3rd value to match against
     * @return Pattern Matcher for further matching
     */
    public static <T1, T2, T3> MTuple3<Predicate<? super T1>, Predicate<? super T2>, Predicate<? super T3>> when(final T1 t1, final T2 t2,
            final T3 t3) {

        return () -> Tuple.tuple(test -> Objects.equals(test, t1), test -> Objects.equals(test, t2), test -> Objects.equals(test, t3));
    }

    /**
     * Matchable DSL operator for matching against three values with predicates
     * <pre>
     * {@code 
     *    Matchables.match3(100,2,1000)
                    .matches(c->c.is(when(Predicates.greaterThan(50),Predicates.lessThan(10),Predicates.greaterThan(500)), ()->"large and small and huge"), ()->"not large and small")
                    .get()
                
          //"large and small and huge"
     * 
     * }
     * </pre>
     * 
     * 
     * @param t1 1st value to match against
     * @param t2 2nd value to match against
     * @param t3 3rd value to match against
     * @return Pattern Matcher for further matching
     */
    public static <T1, T2, T3> MTuple3<Predicate<? super T1>, Predicate<? super T2>, Predicate<? super T3>> when(final Predicate<? super T1> t1,
            final Predicate<? super T2> t2, final Predicate<? super T3> t3) {

        return () -> Tuple.tuple(t1, t2, t3);
    }

    //when arity 4
    /**
     * Matchable DSL operator for matching against four values
     * 
     * <pre>
     * {@code 
     * Eval<String> url = Matchables.url(new URL("http://www.aol.com/path?q=hello"))
                                     .on$12_45()
                                     .matches(c->c.is(when("http","www.aol.com","/path","q=hello"), then("correct")),otherwise("miss"));
        
        //Eval.now("correct")
     * 
     * 
     * }
     * </pre>
     * 
     * 
     * 
     * @param t1 1st value to match against
     * @param t2 2nd value to match against
     * @param t3 3rd value to match against
     * @param t4 4th value to match against
     * @return Pattern Matcher for further matching
     */
    public static <T1, T2, T3, T4> MTuple4<Predicate<? super T1>, Predicate<? super T2>, Predicate<? super T3>, Predicate<? super T4>> when(
            final T1 t1, final T2 t2, final T3 t3, final T4 t4) {

        return () -> Tuple.tuple(test -> Objects.equals(test, t1), test -> Objects.equals(test, t2), test -> Objects.equals(test, t3),
                                 test -> Objects.equals(test, t4));
    }

    /**
     * Matchable DSL operator for matching against four values with predicates
     * 
     * <pre>
     * {@code 
     * Matchables.url(new URL("http://www.aol.com/path?q=hello"))
                 .on$12_45()
                 .matches(c->c.is(when(eq("http"),in("www.aol.com","aol.com"),any(),not(eq("q=hello!"))), then("correct")),
                          otherwise("miss"));
   
        //Eval["miss"]
     * 
     * }
     * </pre>
     * 
     * @param t1 1st value to match against
     * @param t2 2nd value to match against
     * @param t3 3rd value to match against
     * @param t4 4th value to match against
     * @return  Pattern Matcher for further matching
     */
    public static <T1, T2, T3, T4> MTuple4<Predicate<? super T1>, Predicate<? super T2>, Predicate<? super T3>, Predicate<? super T4>> when(
            final Predicate<? super T1> t1, final Predicate<? super T2> t2, final Predicate<? super T3> t3, final Predicate<? super T4> t4) {

        return () -> Tuple.tuple(t1, t2, t3, t4);
    }

    //when arity 5
    /**
     * Matchable DSL operator for matching against five values with predicates
     * 
     * <pre>
     * {@code 
     *   String result =   Matchables.chars("hello,world")
                                     .matches(c->c.has(when('h','e','l','l','o'), then("startsWith")),
                                               otherwise("miss"))
                                     .get();
      
         Eval["startsWith"]
     * }
     * </pre>
     * @param t1 1st value to match against
     * @param t2 2nd value to match against
     * @param t3 3rd value to match against
     * @param t4 4th value to match against
     * @param t5 5th value to match against
     * @return Pattern Matcher for further matching
     */
    public static <T1, T2, T3, T4, T5> MTuple5<Predicate<? super T1>, Predicate<? super T2>, Predicate<? super T3>, Predicate<? super T4>, Predicate<? super T5>> when(
            final T1 t1, final T2 t2, final T3 t3, final T4 t4, final T5 t5) {

        return () -> Tuple.tuple(test -> Objects.equals(test, t1), test -> Objects.equals(test, t2), test -> Objects.equals(test, t3),
                                 test -> Objects.equals(test, t4), test -> Objects.equals(test, t5));
    }

    /**
     * Matchable DSL operator for matching against five values with predicates
     * 
     * <pre>
     * {@code 
     *   String result =   Matchables.chars("hello,world")
                                     .matches(c->c.has(when(eq('h'),not(eq('e')),eq('l'),eq('l'),eq('o')), then("startsWith")),
                                               otherwise("miss"))
                                     .get();
      
         Eval["miss"]
     * } 
     * 
     * 
     * @param t1 1st value to match against
     * @param t2 2nd value to match against
     * @param t3 3rd value to match against
     * @param t4 4th value to match against
     * @param t5 5th value to match against
     * @return Pattern Matcher for further matching
     */
    public static <T1, T2, T3, T4, T5> MTuple5<Predicate<? super T1>, Predicate<? super T2>, Predicate<? super T3>, Predicate<? super T4>, Predicate<? super T5>> when(
            final Predicate<? super T1> t1, final Predicate<? super T2> t2, final Predicate<? super T3> t3, final Predicate<? super T4> t4,
            final Predicate<? super T5> t5) {

        return () -> Tuple.tuple(t1, t2, t3, t4, t5);
    }

    /**
     * Interface that returns this as the Matchable
     * 
     * <pre>
     * {@code 
     * @Value
       public class MyCase  implements MatchSelf<MyCase>, Decomposable{
               int a;
               int b;
               int c;
       }


       import static com.aol.cyclops.control.Matchable.otherwise;
       import static com.aol.cyclops.control.Matchable.then;
       import static com.aol.cyclops.control.Matchable.when;
       import static com.aol.cyclops.util.function.Predicates.__;

       Eval<String> result = new MyCase(4,5,6).matches(c->c.is(when(4,5,6),then("res")),
                                                       otherwise("n/a"));
        
       //Eval["res"]
     * }
     * </pre>
     * 
     * 
     * @author johnmcclean
     *
     * @param <TYPE> Concrete type to match against. 
     */
    public static interface MatchSelf<TYPE> extends MatchableObject<TYPE> {
        @Override
        default Object getMatchable() {
            return this;
        }
    }

    static interface MatchableObject<TYPE> {
        /**
         * @return matchable
         */
        Object getMatchable();

        /*
         * Match against the values inside the matchable with a single case
         * 
         * <pre>
         * {@code
         * int result = Matchable.of(Optional.of(1))
        							.matches(c->c.hasValues(1).then(i->2));
        	//2						
         * }</pre>
         * 
         * Note, it is possible to continue to chain cases within a single case, but cleaner
         * to use the appropriate overloaded matches method that accepts two (or more) cases.
         * 
         * @param fn1 Describes the matching case
         * @return Result - this method requires a match or an NoSuchElement exception is thrown
         */
        @SuppressWarnings({ "rawtypes", "unchecked" })
        default <R> Eval<R> matches(final Function<CheckValues<TYPE, R>, CheckValues<TYPE, R>> fn1, final Supplier<? extends R> otherwise) {

            if (otherwise instanceof Eval) {
                final Eval<R> tailRec = (Eval<R>) otherwise;
                return Eval.later(() -> (R) new MatchingInstance(
                                                                 new MatchableCase(
                                                                                   fn1.apply(new MatchableCase(
                                                                                                               new PatternMatcher()).withType(getMatchable().getClass()))
                                                                                      .getPatternMatcher())).match(getMatchable())
                                                                                                            .orElse(UNSET.VOID))
                           .flatMap(i -> i == UNSET.VOID ? tailRec : Eval.now(i));
            }

            return Eval.later(() -> (R) new MatchingInstance(
                                                             new MatchableCase(
                                                                               fn1.apply(new MatchableCase(
                                                                                                           new PatternMatcher()).withType(getMatchable().getClass()))
                                                                                  .getPatternMatcher())).match(getMatchable())
                                                                                                        .orElseGet(otherwise));

        }

    }

    @AllArgsConstructor
    public static class AutoCloseableMatchableIterable<TYPE> implements MatchableIterable<TYPE>, AutoCloseable {
        private final AutoCloseable closeable;
        @Getter
        private final Iterable<TYPE> matchable;

        @Override
        public void close() throws Exception {
            closeable.close();

        }

    }

    @FunctionalInterface
    public static interface MatchableIterable<TYPE> {

        Iterable<TYPE> getMatchable();

        default <R> Eval<R> visitSeq(final BiFunction<? super TYPE, ? super ReactiveSeq<TYPE>, ? extends R> match,
                final Supplier<? extends R> absent) {
            @SuppressWarnings("unchecked")
            final Iterable<TYPE> it = getMatchable();
            return Eval.later(() -> ReactiveSeq.fromIterable(it)
                                               .visit(match, absent));
        }

        default <R> Eval<R> visit(final BiFunction<? super TYPE, ? super MatchableIterable<TYPE>, ? extends R> match,
                final Supplier<? extends R> absent) {
            @SuppressWarnings("unchecked")
            final Iterable<TYPE> it = getMatchable();

            return Eval.later(() -> {
                final Iterator<TYPE> iter = it.iterator();
                final Maybe<TYPE> head = Maybe.ofNullable(iter.hasNext() ? iter.next() : null);
                final MatchableIterable<TYPE> matchable = () -> () -> iter;
                return head.visit(some -> match.apply(some, matchable), absent);
            });
        }

        default <R> Eval<R> matches(final Function<CheckValues<TYPE, R>, CheckValues<TYPE, R>> iterable, final Supplier<? extends R> otherwise) {
            final MatchableObject<TYPE> obj = () -> getMatchable();
            return obj.matches(iterable, otherwise);

        }

        static interface MIUtil {

            static <TYPE> Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE> toTuple5(final Object o) {
                final Iterator it = ((Iterable) o).iterator();
                return Tuple.tuple((TYPE) (it.hasNext() ? it.next() : null), (TYPE) (it.hasNext() ? it.next() : null),
                                   (TYPE) (it.hasNext() ? it.next() : null), (TYPE) (it.hasNext() ? it.next() : null),
                                   (TYPE) (it.hasNext() ? it.next() : null));
            }
        }

        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple1<TYPE> on$1____() {
            final Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE> it = (Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE>) MIUtil.toTuple5(getMatchable());
            return () -> new Tuple1<TYPE>(
                                          it.v1);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple1<TYPE> on$_2___() {
            final Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE> it = (Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE>) MIUtil.toTuple5(getMatchable());
            return () -> new Tuple1<TYPE>(
                                          it.v2);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple1<TYPE> on$__3__() {
            final Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE> it = (Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE>) MIUtil.toTuple5(getMatchable());
            return () -> new Tuple1<TYPE>(
                                          it.v3);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple1<TYPE> on$___4_() {
            final Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE> it = (Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE>) MIUtil.toTuple5(getMatchable());
            return () -> new Tuple1<TYPE>(
                                          it.v4);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple1<TYPE> on$____5() {
            final Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE> it = (Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE>) MIUtil.toTuple5(getMatchable());
            return () -> new Tuple1<TYPE>(
                                          it.v5);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple2<TYPE, TYPE> on$12___() {
            final Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE> it = (Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE>) MIUtil.toTuple5(getMatchable());
            return () -> new Tuple2<TYPE, TYPE>(
                                                it.v1, it.v2);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple2<TYPE, TYPE> on$1_3__() {
            final Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE> it = (Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE>) MIUtil.toTuple5(getMatchable());
            return () -> new Tuple2<TYPE, TYPE>(
                                                it.v1, it.v3);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple2<TYPE, TYPE> on$1__4_() {
            final Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE> it = (Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE>) MIUtil.toTuple5(getMatchable());
            return () -> new Tuple2<TYPE, TYPE>(
                                                it.v1, it.v4);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple2<TYPE, TYPE> on$1___5() {
            final Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE> it = (Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE>) MIUtil.toTuple5(getMatchable());
            return () -> new Tuple2<TYPE, TYPE>(
                                                it.v1, it.v5);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple2<TYPE, TYPE> on$_23__() {
            final Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE> it = (Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE>) MIUtil.toTuple5(getMatchable());
            return () -> new Tuple2<TYPE, TYPE>(
                                                it.v2, it.v3);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple2<TYPE, TYPE> on$_2_4_() {
            final Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE> it = (Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE>) MIUtil.toTuple5(getMatchable());
            return () -> new Tuple2<TYPE, TYPE>(
                                                it.v2, it.v4);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple2<TYPE, TYPE> on$_2__5() {
            final Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE> it = (Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE>) MIUtil.toTuple5(getMatchable());
            return () -> new Tuple2<TYPE, TYPE>(
                                                it.v2, it.v5);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple2<TYPE, TYPE> on$__34_() {
            final Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE> it = (Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE>) MIUtil.toTuple5(getMatchable());
            return () -> new Tuple2<TYPE, TYPE>(
                                                it.v3, it.v4);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple2<TYPE, TYPE> on$__3_5() {
            final Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE> it = (Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE>) MIUtil.toTuple5(getMatchable());
            return () -> new Tuple2<TYPE, TYPE>(
                                                it.v3, it.v5);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple2<TYPE, TYPE> on$___45() {
            final Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE> it = (Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE>) MIUtil.toTuple5(getMatchable());
            return () -> new Tuple2<TYPE, TYPE>(
                                                it.v4, it.v5);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple3<TYPE, TYPE, TYPE> on$123__() {
            final Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE> it = (Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE>) MIUtil.toTuple5(getMatchable());
            return () -> new Tuple3<TYPE, TYPE, TYPE>(
                                                      it.v1, it.v2, it.v3);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple3<TYPE, TYPE, TYPE> on$12_4_() {
            final Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE> it = (Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE>) MIUtil.toTuple5(getMatchable());
            return () -> new Tuple3<TYPE, TYPE, TYPE>(
                                                      it.v1, it.v2, it.v4);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple3<TYPE, TYPE, TYPE> on$12__5() {
            final Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE> it = (Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE>) MIUtil.toTuple5(getMatchable());
            return () -> new Tuple3<TYPE, TYPE, TYPE>(
                                                      it.v1, it.v2, it.v5);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple3<TYPE, TYPE, TYPE> on$1_34_() {
            final Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE> it = (Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE>) MIUtil.toTuple5(getMatchable());
            return () -> new Tuple3<TYPE, TYPE, TYPE>(
                                                      it.v1, it.v3, it.v4);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple3<TYPE, TYPE, TYPE> on$1_3_5() {
            final Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE> it = (Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE>) MIUtil.toTuple5(getMatchable());
            return () -> new Tuple3<TYPE, TYPE, TYPE>(
                                                      it.v1, it.v3, it.v5);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple3<TYPE, TYPE, TYPE> on$1__45() {
            final Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE> it = (Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE>) MIUtil.toTuple5(getMatchable());
            return () -> new Tuple3<TYPE, TYPE, TYPE>(
                                                      it.v1, it.v4, it.v5);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple3<TYPE, TYPE, TYPE> on$_234_() {
            final Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE> it = (Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE>) MIUtil.toTuple5(getMatchable());
            return () -> new Tuple3<TYPE, TYPE, TYPE>(
                                                      it.v2, it.v3, it.v4);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple3<TYPE, TYPE, TYPE> on$_23_5() {
            final Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE> it = (Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE>) MIUtil.toTuple5(getMatchable());
            return () -> new Tuple3<TYPE, TYPE, TYPE>(
                                                      it.v2, it.v3, it.v5);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple3<TYPE, TYPE, TYPE> on$__345() {
            final Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE> it = (Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE>) MIUtil.toTuple5(getMatchable());
            return () -> new Tuple3<TYPE, TYPE, TYPE>(
                                                      it.v3, it.v4, it.v5);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple4<TYPE, TYPE, TYPE, TYPE> on$1234_() {
            final Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE> it = (Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE>) MIUtil.toTuple5(getMatchable());
            return () -> new Tuple4<TYPE, TYPE, TYPE, TYPE>(
                                                            it.v1, it.v2, it.v3, it.v4);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple4<TYPE, TYPE, TYPE, TYPE> on$123_5() {
            final Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE> it = (Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE>) MIUtil.toTuple5(getMatchable());
            return () -> new Tuple4<TYPE, TYPE, TYPE, TYPE>(
                                                            it.v1, it.v2, it.v3, it.v5);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple4<TYPE, TYPE, TYPE, TYPE> on$12_45() {
            final Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE> it = (Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE>) MIUtil.toTuple5(getMatchable());
            return () -> new Tuple4<TYPE, TYPE, TYPE, TYPE>(
                                                            it.v1, it.v2, it.v4, it.v5);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple4<TYPE, TYPE, TYPE, TYPE> on$1_345() {
            final Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE> it = (Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE>) MIUtil.toTuple5(getMatchable());
            return () -> new Tuple4<TYPE, TYPE, TYPE, TYPE>(
                                                            it.v1, it.v3, it.v4, it.v5);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple4<TYPE, TYPE, TYPE, TYPE> on$_2345() {
            final Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE> it = (Tuple5<TYPE, TYPE, TYPE, TYPE, TYPE>) MIUtil.toTuple5(getMatchable());
            return () -> new Tuple4<TYPE, TYPE, TYPE, TYPE>(
                                                            it.v2, it.v3, it.v4, it.v5);
        }

    }

    static interface ValueAndOptionalMatcher<T> extends MatchableOptional<T>, Value<T> {

        /* (non-Javadoc)
         * @see com.aol.cyclops.control.Matchable.MatchableOptional#toOptional()
         */
        @Override
        default Optional<T> toOptional() {

            return Value.super.toOptional();
        }

        /* (non-Javadoc)
         * @see com.aol.cyclops.control.Matchable.MatchableOptional#visit(java.util.function.Function, java.util.function.Supplier)
         */
        @Override
        default <R> R visit(final Function<? super T, ? extends R> present, final Supplier<? extends R> absent) {
            return Value.super.visit(present, absent);
        }

        /* (non-Javadoc)
         * @see com.aol.cyclops.control.Matchable.MatchableOptional#iterator()
         */
        @Override
        default Iterator<T> iterator() {

            return MatchableOptional.super.iterator();
        }

    }

    static interface MatchableOptional<T> extends Iterable<T> {

        Optional<T> toOptional();

        @Override
        default Iterator<T> iterator() {
            final Optional<T> opt = toOptional();
            return opt.isPresent() ? Arrays.asList(opt.get())
                                           .iterator()
                    : Arrays.<T> asList()
                            .iterator();
        }

        default <R> R visit(final Function<? super T, ? extends R> some, final Supplier<? extends R> none) {
            final Optional<T> opt = toOptional();
            if (opt.isPresent())
                return some.apply(opt.get());
            return none.get();
        }

        default <R> Eval<R> matches(final Function<CheckValueOpt<T, R>, CheckValueOpt<T, R>> some, final Supplier<? extends R> otherwise) {
            final Optional<T> opt = toOptional();
            // if(opt.isPresent()){

            if (otherwise instanceof Eval) { //tail call optimization
                final Eval<R> tailRec = (Eval<R>) otherwise;
                return Eval.later(() -> (R) new MatchingInstance(
                                                                 new MatchableCase(
                                                                                   some.apply(new MatchableCase(
                                                                                                                new PatternMatcher()).withTypeOpt(Tuple1.class))
                                                                                       .getPatternMatcher())).match(Tuple.tuple(opt.orElse(null)))
                                                                                                             .orElse(UNSET.VOID))
                           .flatMap(i -> i == UNSET.VOID ? tailRec : Eval.now(i));
            }

            return Eval.later(() -> (R) new MatchingInstance(
                                                             new MatchableCase(
                                                                               some.apply(new MatchableCase(
                                                                                                            new PatternMatcher()).withTypeOpt(Tuple1.class))
                                                                                   .getPatternMatcher())).match(Tuple.tuple(opt.orElse(null)))
                                                                                                         .orElseGet(otherwise));

            //  }
            // return Eval.later(()->otherwise.get());
        }
    }

    public static interface MXor<T1, T2> extends Iterable<Object> {
        Xor<T1, T2> getMatchable();

        @Override
        default Iterator<Object> iterator() {
            return getMatchable().isPrimary() ? (Iterator) getMatchable().toList()
                                                                         .iterator()
                    : (Iterator) Arrays.asList(getMatchable().secondaryGet())
                                       .iterator();
        }

        default <R> R visit(final Function<? super T1, ? extends R> case1, final Function<? super T2, ? extends R> case2) {

            return getMatchable().visit(case1, case2);
        }

        default <R> Eval<R> matches(final Function<CheckValue1<T1, R>, CheckValue1<T1, R>> secondary,
                final Function<CheckValue1<T2, R>, CheckValue1<T2, R>> primary, final Supplier<? extends R> otherwise) {
            return getMatchable().matches(secondary, primary, otherwise);
        }

    }

    @FunctionalInterface
    public static interface MTuple1<T1> extends Iterable<Object> {
        Tuple1<T1> getMatchable();

        @Override
        default Iterator<Object> iterator() {
            return getMatchable().iterator();
        }

        default <R> R visit(final Function<? super T1, ? extends R> some, final Supplier<? extends R> none) {
            @SuppressWarnings("unchecked")
            final Tuple1<T1> it = getMatchable();

            return Maybe.ofNullable(it.v1)
                        .visit(some, none);
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        default <R> Eval<R> matches(final Function<CheckValue1<T1, R>, CheckValue1<T1, R>> fn1, final Supplier<? extends R> otherwise) {

            if (otherwise instanceof Eval) {
                final Eval<R> tailRec = (Eval<R>) otherwise;
                return Eval.later(() -> (R) new MatchingInstance(
                                                                 new MatchableCase(
                                                                                   fn1.apply(new MatchableCase(
                                                                                                               new PatternMatcher()).withType1(getMatchable().getClass()))
                                                                                      .getPatternMatcher())).match(getMatchable())
                                                                                                            .orElse(UNSET.VOID))
                           .flatMap(i -> i == UNSET.VOID ? tailRec : Eval.now(i));
            }

            return Eval.later(() -> (R) new MatchingInstance(
                                                             new MatchableCase(
                                                                               fn1.apply(new MatchableCase(
                                                                                                           new PatternMatcher()).withType1(getMatchable().getClass()))
                                                                                  .getPatternMatcher())).match(getMatchable())
                                                                                                        .orElseGet(otherwise));
        }

    }

    @FunctionalInterface
    public static interface MTuple2<T1, T2> extends Iterable<Object> {
        Tuple2<T1, T2> getMatchable();

        @Override
        default Iterator<Object> iterator() {
            return getMatchable().iterator();
        }

        default <R> R visit(final BiFunction<? super T1, ? super T2, ? extends R> match) {
            @SuppressWarnings("unchecked")
            final Tuple2<T1, T2> it = getMatchable();
            return match.apply(it.v1, it.v2);
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        default <R> Eval<R> matches(final Function<CheckValue2<T1, T2, R>, CheckValue2<T1, T2, R>> fn1, final Supplier<? extends R> otherwise) {
            if (otherwise instanceof Eval) {
                final Eval<R> tailRec = (Eval<R>) otherwise;
                return Eval.later(() -> (R) new MatchingInstance(
                                                                 new MatchableCase(
                                                                                   fn1.apply(new MatchableCase(
                                                                                                               new PatternMatcher()).withType2(getMatchable().getClass()))
                                                                                      .getPatternMatcher())).match(getMatchable())
                                                                                                            .orElse(UNSET.VOID))
                           .flatMap(i -> i == UNSET.VOID ? tailRec : Eval.now(i));
            }
            return Eval.later(() -> (R) new MatchingInstance(
                                                             new MatchableCase(
                                                                               fn1.apply(new MatchableCase(
                                                                                                           new PatternMatcher()).withType2(getMatchable().getClass()))
                                                                                  .getPatternMatcher())).match(getMatchable())
                                                                                                        .orElseGet(otherwise));
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 2 fields.
         * Wildcards are indicated by '_'.
         * $1_ matches on elements / field 1 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple1<T1> on$1_() {
            final Tuple2<T1, T2> it = getMatchable();
            return () -> new Tuple1<T1>(
                                        it.v1);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 2 fields.
         * Wildcards are indicated by '_'.
         * $1_ matches on elements / field 1
         * 
         * @return Matcher for the selected elements
         */
        default MTuple1<T2> on$_2() {
            final Tuple2<T1, T2> it = getMatchable();
            return () -> new Tuple1<T2>(
                                        it.v2);
        }

    }

    @FunctionalInterface
    public static interface MTuple3<T1, T2, T3> extends Iterable<Object> {
        Tuple3<T1, T2, T3> getMatchable();

        @Override
        default Iterator<Object> iterator() {
            return getMatchable().iterator();
        }

        default <R> Eval<R> visit(final TriFunction<? super T1, ? super T2, ? super T3, ? extends R> match) {
            @SuppressWarnings("unchecked")
            final Tuple3<T1, T2, T3> it = getMatchable();
            return Eval.later(() -> match.apply(it.v1, it.v2, it.v3));
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        default <R> Eval<R> matches(final Function<CheckValue3<T1, T2, T3, R>, CheckValue3<T1, T2, T3, R>> fn1, final Supplier<R> otherwise) {
            if (otherwise instanceof Eval) {
                final Eval<R> tailRec = (Eval<R>) otherwise;
                return Eval.later(() -> (R) new MatchingInstance(
                                                                 new MatchableCase(
                                                                                   fn1.apply(new MatchableCase(
                                                                                                               new PatternMatcher()).withType3(getMatchable().getClass()))
                                                                                      .getPatternMatcher())).match(getMatchable())
                                                                                                            .orElse(UNSET.VOID))
                           .flatMap(i -> i == UNSET.VOID ? tailRec : Eval.now(i));
            }
            return Eval.later(() -> (R) new MatchingInstance(
                                                             new MatchableCase(
                                                                               fn1.apply(new MatchableCase(
                                                                                                           new PatternMatcher()).withType3(getMatchable().getClass()))
                                                                                  .getPatternMatcher())).match(getMatchable())
                                                                                                        .orElseGet(otherwise));
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 3 fields.
         * Wildcards are indicated by '_'.
         * $1_3 matches on elements / field 1 and 3 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple1<T1> on$1__() {
            final Tuple3<T1, T2, T3> it = getMatchable();
            return () -> new Tuple1<T1>(
                                        it.v1);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 3 fields.
         * Wildcards are indicated by '_'.
         * $1_3 matches on elements / field 1 and 3 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple1<T2> on$_2_() {
            final Tuple3<T1, T2, T3> it = getMatchable();
            return () -> new Tuple1<T2>(
                                        it.v2);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 3 fields.
         * Wildcards are indicated by '_'.
         * $1_3 matches on elements / field 1 and 3 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple1<T3> on$__3() {
            final Tuple3<T1, T2, T3> it = getMatchable();
            return () -> new Tuple1<T3>(
                                        it.v3);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 3 fields.
         * Wildcards are indicated by '_'.
         * $1_3 matches on elements / field 1 and 3 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple2<T1, T2> on$12_() {
            final Tuple3<T1, T2, T3> it = getMatchable();
            return () -> new Tuple2<T1, T2>(
                                            it.v1, it.v2);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 3 fields.
         * Wildcards are indicated by '_'.
         * $1_3 matches on elements / field 1 and 3 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple2<T1, T3> on$1_3() {
            final Tuple3<T1, T2, T3> it = getMatchable();
            return () -> new Tuple2<T1, T3>(
                                            it.v1, it.v3);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 3 fields.
         * Wildcards are indicated by '_'.
         * $1_3 matches on elements / field 1 and 3 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple2<T2, T3> on$_23() {
            final Tuple3<T1, T2, T3> it = getMatchable();
            return () -> new Tuple2<T2, T3>(
                                            it.v2, it.v3);
        }
    }

    @FunctionalInterface
    public static interface MTuple4<T1, T2, T3, T4> extends Iterable<Object> {
        Tuple4<T1, T2, T3, T4> getMatchable();

        @Override
        default Iterator<Object> iterator() {
            return getMatchable().iterator();
        }

        default <R> Eval<R> visit(final QuadFunction<? super T1, ? super T2, ? super T3, ? super T4, ? extends R> match) {
            @SuppressWarnings("unchecked")
            final Tuple4<T1, T2, T3, T4> it = getMatchable();
            return Eval.later(() -> match.apply(it.v1, it.v2, it.v3, it.v4));
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        default <R> Eval<R> matches(final Function<CheckValue4<T1, T2, T3, T4, R>, CheckValue4<T1, T2, T3, T4, R>> fn1, final Supplier<R> otherwise) {
            if (otherwise instanceof Eval) {
                final Eval<R> tailRec = (Eval<R>) otherwise;
                return Eval.later(() -> (R) new MatchingInstance(
                                                                 new MatchableCase(
                                                                                   fn1.apply(new MatchableCase(
                                                                                                               new PatternMatcher()).withType4(getMatchable().getClass()))
                                                                                      .getPatternMatcher())).match(getMatchable())
                                                                                                            .orElse(UNSET.VOID))
                           .flatMap(i -> i == UNSET.VOID ? tailRec : Eval.now(i));
            }
            return Eval.later(() -> (R) new MatchingInstance(
                                                             new MatchableCase(
                                                                               fn1.apply(new MatchableCase(
                                                                                                           new PatternMatcher()).withType4(getMatchable().getClass()))
                                                                                  .getPatternMatcher())).match(getMatchable())
                                                                                                        .orElseGet(otherwise));
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 4 fields.
         * Wildcards are indicated by '_'.
         * $1_3_ matches on elements / field 1 and 3 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple1<T1> on$1___() {
            final Tuple4<T1, T2, T3, T4> it = getMatchable();
            return () -> new Tuple1<T1>(
                                        it.v1);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 4 fields.
         * Wildcards are indicated by '_'.
         * $1_3_ matches on elements / field 1 and 3 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple1<T2> on$_2__() {
            final Tuple4<T1, T2, T3, T4> it = getMatchable();
            return () -> new Tuple1<T2>(
                                        it.v2);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 4 fields.
         * Wildcards are indicated by '_'.
         * $1_3_ matches on elements / field 1 and 3 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple1<T3> on$__3_() {
            final Tuple4<T1, T2, T3, T4> it = getMatchable();
            return () -> new Tuple1<T3>(
                                        it.v3);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 4 fields.
         * Wildcards are indicated by '_'.
         * $1_3_ matches on elements / field 1 and 3 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple1<T4> on$___4() {
            final Tuple4<T1, T2, T3, T4> it = getMatchable();
            return () -> new Tuple1<T4>(
                                        it.v4);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 4 fields.
         * Wildcards are indicated by '_'.
         * $1_3_ matches on elements / field 1 and 3 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple2<T1, T2> on$12__() {
            final Tuple4<T1, T2, T3, T4> it = getMatchable();
            return () -> new Tuple2<T1, T2>(
                                            it.v1, it.v2);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 4 fields.
         * Wildcards are indicated by '_'.
         * $1_3_ matches on elements / field 1 and 3 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple2<T1, T3> on$1_3_() {
            final Tuple4<T1, T2, T3, T4> it = getMatchable();
            return () -> new Tuple2<T1, T3>(
                                            it.v1, it.v3);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 4 fields.
         * Wildcards are indicated by '_'.
         * $1_3_ matches on elements / field 1 and 3 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple2<T1, T4> on$1__4() {
            final Tuple4<T1, T2, T3, T4> it = getMatchable();
            return () -> new Tuple2<T1, T4>(
                                            it.v1, it.v4);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 4 fields.
         * Wildcards are indicated by '_'.
         * $1_3_ matches on elements / field 1 and 3 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple2<T2, T3> on$_23_() {
            final Tuple4<T1, T2, T3, T4> it = getMatchable();
            return () -> new Tuple2<T2, T3>(
                                            it.v2, it.v3);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 4 fields.
         * Wildcards are indicated by '_'.
         * $1_3_ matches on elements / field 1 and 3 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple2<T2, T4> on$_2_4() {
            final Tuple4<T1, T2, T3, T4> it = getMatchable();
            return () -> new Tuple2<T2, T4>(
                                            it.v2, it.v4);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 4 fields.
         * Wildcards are indicated by '_'.
         * $1_3_ matches on elements / field 1 and 3 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple2<T3, T4> on$__34() {
            final Tuple4<T1, T2, T3, T4> it = getMatchable();
            return () -> new Tuple2<T3, T4>(
                                            it.v3, it.v4);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 4 fields.
         * Wildcards are indicated by '_'.
         * $1_3_ matches on elements / field 1 and 3 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple3<T1, T2, T3> on$123_() {
            final Tuple4<T1, T2, T3, T4> it = getMatchable();
            return () -> new Tuple3<T1, T2, T3>(
                                                it.v1, it.v2, it.v3);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 4 fields.
         * Wildcards are indicated by '_'.
         * $1_3_ matches on elements / field 1 and 3 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple3<T1, T2, T4> on$12_4() {
            final Tuple4<T1, T2, T3, T4> it = getMatchable();
            return () -> new Tuple3<T1, T2, T4>(
                                                it.v1, it.v2, it.v4);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 4 fields.
         * Wildcards are indicated by '_'.
         * $1_3_ matches on elements / field 1 and 3 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple3<T1, T3, T4> on$1_34() {
            final Tuple4<T1, T2, T3, T4> it = getMatchable();
            return () -> new Tuple3<T1, T3, T4>(
                                                it.v1, it.v3, it.v4);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 4 fields.
         * Wildcards are indicated by '_'.
         * $1_3_ matches on elements / field 1 and 3 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple3<T2, T3, T4> on$_234() {
            final Tuple4<T1, T2, T3, T4> it = getMatchable();
            return () -> new Tuple3<T2, T3, T4>(
                                                it.v2, it.v3, it.v4);
        }

    }

    @FunctionalInterface
    public static interface MTuple5<T1, T2, T3, T4, T5> extends Iterable<Object> {
        Tuple5<T1, T2, T3, T4, T5> getMatchable();

        @Override
        default Iterator<Object> iterator() {
            return getMatchable().iterator();
        }

        default <R> Eval<R> visit(final QuintFunction<? super T1, ? super T2, ? super T3, ? super T4, ? super T5, ? extends R> match) {
            @SuppressWarnings("unchecked")
            final Tuple5<T1, T2, T3, T4, T5> it = getMatchable();
            return Eval.later(() -> match.apply(it.v1, it.v2, it.v3, it.v4, it.v5));
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        default <R> Eval<R> matches(final Function<CheckValue5<T1, T2, T3, T4, T5, R>, CheckValue5<T1, T2, T3, T4, T5, R>> fn1,
                final Supplier<R> otherwise) {
            if (otherwise instanceof Eval) {
                final Eval<R> tailRec = (Eval<R>) otherwise;
                return Eval.later(() -> (R) new MatchingInstance(
                                                                 new MatchableCase(
                                                                                   fn1.apply(new MatchableCase(
                                                                                                               new PatternMatcher()).withType5(getMatchable().getClass()))
                                                                                      .getPatternMatcher())).match(getMatchable())
                                                                                                            .orElse(UNSET.VOID))
                           .flatMap(i -> i == UNSET.VOID ? tailRec : Eval.now(i));
            }
            return Eval.later(() -> (R) new MatchingInstance(
                                                             new MatchableCase(
                                                                               fn1.apply(new MatchableCase(
                                                                                                           new PatternMatcher()).withType5(getMatchable().getClass()))
                                                                                  .getPatternMatcher())).match(getMatchable())
                                                                                                        .orElseGet(otherwise));
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple1<T1> on$1____() {
            final Tuple5<T1, T2, T3, T4, T5> it = getMatchable();
            return () -> new Tuple1<T1>(
                                        it.v1);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple1<T2> on$_2___() {
            final Tuple5<T1, T2, T3, T4, T5> it = getMatchable();
            return () -> new Tuple1<T2>(
                                        it.v2);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple1<T3> on$__3__() {
            final Tuple5<T1, T2, T3, T4, T5> it = getMatchable();
            return () -> new Tuple1<T3>(
                                        it.v3);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple1<T4> on$___4_() {
            final Tuple5<T1, T2, T3, T4, T5> it = getMatchable();
            return () -> new Tuple1<T4>(
                                        it.v4);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple1<T5> on$____5() {
            final Tuple5<T1, T2, T3, T4, T5> it = getMatchable();
            return () -> new Tuple1<T5>(
                                        it.v5);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple2<T1, T2> on$12___() {
            final Tuple5<T1, T2, T3, T4, T5> it = getMatchable();
            return () -> new Tuple2<T1, T2>(
                                            it.v1, it.v2);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple2<T1, T3> on$1_3__() {
            final Tuple5<T1, T2, T3, T4, T5> it = getMatchable();
            return () -> new Tuple2<T1, T3>(
                                            it.v1, it.v3);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple2<T1, T4> on$1__4_() {
            final Tuple5<T1, T2, T3, T4, T5> it = getMatchable();
            return () -> new Tuple2<T1, T4>(
                                            it.v1, it.v4);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple2<T1, T5> on$1___5() {
            final Tuple5<T1, T2, T3, T4, T5> it = getMatchable();
            return () -> new Tuple2<T1, T5>(
                                            it.v1, it.v5);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple2<T2, T3> on$_23__() {
            final Tuple5<T1, T2, T3, T4, T5> it = getMatchable();
            return () -> new Tuple2<T2, T3>(
                                            it.v2, it.v3);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple2<T2, T4> on$_2_4_() {
            final Tuple5<T1, T2, T3, T4, T5> it = getMatchable();
            return () -> new Tuple2<T2, T4>(
                                            it.v2, it.v4);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple2<T2, T5> on$_2__5() {
            final Tuple5<T1, T2, T3, T4, T5> it = getMatchable();
            return () -> new Tuple2<T2, T5>(
                                            it.v2, it.v5);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple2<T3, T4> on$__34_() {
            final Tuple5<T1, T2, T3, T4, T5> it = getMatchable();
            return () -> new Tuple2<T3, T4>(
                                            it.v3, it.v4);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple2<T3, T5> on$__3_5() {
            final Tuple5<T1, T2, T3, T4, T5> it = getMatchable();
            return () -> new Tuple2<T3, T5>(
                                            it.v3, it.v5);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple2<T4, T5> on$___45() {
            final Tuple5<T1, T2, T3, T4, T5> it = getMatchable();
            return () -> new Tuple2<T4, T5>(
                                            it.v4, it.v5);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple3<T1, T2, T3> on$123__() {
            final Tuple5<T1, T2, T3, T4, T5> it = getMatchable();
            return () -> new Tuple3<T1, T2, T3>(
                                                it.v1, it.v2, it.v3);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple3<T1, T2, T4> on$12_4_() {
            final Tuple5<T1, T2, T3, T4, T5> it = getMatchable();
            return () -> new Tuple3<T1, T2, T4>(
                                                it.v1, it.v2, it.v4);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple3<T1, T2, T5> on$12__5() {
            final Tuple5<T1, T2, T3, T4, T5> it = getMatchable();
            return () -> new Tuple3<T1, T2, T5>(
                                                it.v1, it.v2, it.v5);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple3<T1, T3, T4> on$1_34_() {
            final Tuple5<T1, T2, T3, T4, T5> it = getMatchable();
            return () -> new Tuple3<T1, T3, T4>(
                                                it.v1, it.v3, it.v4);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple3<T1, T3, T5> on$1_3_5() {
            final Tuple5<T1, T2, T3, T4, T5> it = getMatchable();
            return () -> new Tuple3<T1, T3, T5>(
                                                it.v1, it.v3, it.v5);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple3<T1, T4, T5> on$1__45() {
            final Tuple5<T1, T2, T3, T4, T5> it = getMatchable();
            return () -> new Tuple3<T1, T4, T5>(
                                                it.v1, it.v4, it.v5);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple3<T2, T3, T4> on$_234_() {
            final Tuple5<T1, T2, T3, T4, T5> it = getMatchable();
            return () -> new Tuple3<T2, T3, T4>(
                                                it.v2, it.v3, it.v4);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple3<T2, T3, T5> on$_23_5() {
            final Tuple5<T1, T2, T3, T4, T5> it = getMatchable();
            return () -> new Tuple3<T2, T3, T5>(
                                                it.v2, it.v3, it.v5);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple3<T3, T4, T5> on$__345() {
            final Tuple5<T1, T2, T3, T4, T5> it = getMatchable();
            return () -> new Tuple3<T3, T4, T5>(
                                                it.v3, it.v4, it.v5);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple4<T1, T2, T3, T4> on$1234_() {
            final Tuple5<T1, T2, T3, T4, T5> it = getMatchable();
            return () -> new Tuple4<T1, T2, T3, T4>(
                                                    it.v1, it.v2, it.v3, it.v4);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple4<T1, T2, T3, T5> on$123_5() {
            final Tuple5<T1, T2, T3, T4, T5> it = getMatchable();
            return () -> new Tuple4<T1, T2, T3, T5>(
                                                    it.v1, it.v2, it.v3, it.v5);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple4<T1, T2, T4, T5> on$12_45() {
            final Tuple5<T1, T2, T3, T4, T5> it = getMatchable();
            return () -> new Tuple4<T1, T2, T4, T5>(
                                                    it.v1, it.v2, it.v4, it.v5);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple4<T1, T3, T4, T5> on$1_345() {
            final Tuple5<T1, T2, T3, T4, T5> it = getMatchable();
            return () -> new Tuple4<T1, T3, T4, T5>(
                                                    it.v1, it.v3, it.v4, it.v5);
        }
        /**
         * Match only on the selected elements / fields.
         * Fields are indexed in a 1 based index up to the first 5 fields.
         * Wildcards are indicated by '_'.
         * $1__4_ matches on elements / field 1 and 4 only
         * 
         * @return Matcher for the selected elements
         */
        default MTuple4<T2, T3, T4, T5> on$_2345() {
            final Tuple5<T1, T2, T3, T4, T5> it = getMatchable();
            return () -> new Tuple4<T2, T3, T4, T5>(
                                                    it.v2, it.v3, it.v4, it.v5);
        }
    }

    /**
     * Coerce a type to implement the MatchableObject interface
     * 
     * @author johnmcclean
     *
     */
    public class AsMatchable {

        /**
         * Coerce / wrap an Object as a Matchable instance
         * This adds match / _match methods for pattern matching against the object
         * 
         * @param toCoerce Object to convert into a Matchable
         * @return Matchable that adds functionality to the supplied object
         */
        public static <T> MatchableObject<T> asMatchable(final Object toCoerce) {
            return new CoercedMatchable<>(
                                          toCoerce);
        }

        @AllArgsConstructor
        public static class CoercedMatchable<T> implements MatchableObject<T> {
            private final Object matchable;

            @Override
            public Object getMatchable() {
                return matchable;
            }

        }
    }

    /**
     * Class used to build a pattern matching class against a single optional value 
     *  cyclops-react pattern matching uses the is /has methods to denote individual cases.

        is : indicates exact matching
    
        has : indicates that the supplied values must be present in order
    
        <pre>
        {@code 
            is(when(1,2,3),then("match")) : must have values 1,2,3 only in that order
    
            has(when(1,2,3),then("match")) : must have values 1,2,3 in that order, but may also have additional values
        }
        </pre> 
     * @author johnmcclean
     *
     * @param <T> Input type to match against
     * @param <R> Return type if matched
     */
    @AllArgsConstructor(access = AccessLevel.PUBLIC)
    public static class CheckValueOpt<T, R> {
        private final Class<T> clazz;
        protected final MatchableCase<R> simplerCase;

        @SuppressWarnings({ "rawtypes", "unchecked" })
        public final <V> CheckValueOpt<T, R> isEmpty(final Supplier<? extends R> then) {

            final Predicate predicate = it -> Maybe.ofNullable(it)
                                                   .map(v -> v.getClass()
                                                              .isAssignableFrom(clazz))
                                                   .orElse(false);
            // add wildcard support

            final Predicate<V>[] predicates = new Predicate[] { i -> i == SeqUtils.EMPTY };
            return new CheckValueOpt(
                                     clazz, new MatchableCase(
                                                              this.getPatternMatcher()
                                                                  .inCaseOfManyType(predicate, i -> then.get(), predicates)));

        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        public final CheckValueOpt<T, R> is(final MTuple1<Predicate<? super T>> when, final Supplier<? extends R> then) {
            return isWhere(then, when.getMatchable().v1);
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        private final CheckValueOpt<T, R> isWhere(final Supplier<? extends R> result, final Predicate<? super T> value) {
            final Predicate predicate = it -> it != null && Optional.of(it)
                                                                    .map(v -> v.getClass()
                                                                               .isAssignableFrom(clazz))
                                                                    .orElse(false);
            // add wildcard support

            final Predicate<T>[] predicates = ReactiveSeq.of(value)
                                                         .map(nextValue -> simplerCase.convertToPredicate(nextValue))
                                                         .toListX()
                                                         .plus(i -> SeqUtils.EMPTY == i)
                                                         .toArray(new Predicate[0]);

            return new CheckValueOpt(
                                     clazz, new MatchableCase(
                                                              this.getPatternMatcher()
                                                                  .inCaseOfManyType(predicate, i -> result.get(), predicates)));
        }

        PatternMatcher getPatternMatcher() {
            return simplerCase.getPatternMatcher();
        }
    }

    @AllArgsConstructor(access = AccessLevel.PUBLIC)
    /**
     * Class used to build a pattern matching class against a single value
     * 
     *  cyclops-react pattern matching uses the is /has methods to denote individual cases.

        is : indicates exact matching
    
        has : indicates that the supplied values must be present in order
    
        <pre>
        {@code 
            is(when(1,2,3),then("match")) : must have values 1,2,3 only in that order
    
            has(when(1,2,3),then("match")) : must have values 1,2,3 in that order, but may also have additional values
        }
        </pre>
             * 
     * @author johnmcclean
     *
     * @param <T> Input type to match against
     * @param <R> Return type if matched
     */
    public static class CheckValue1<T, R> {
        private final Class<T> clazz;
        protected final MatchableCase<R> simplerCase;

        @SuppressWarnings({ "rawtypes", "unchecked" })
        /**
         * Check that the provided Tuple-Predicate (use {@link Matchable#when(Predicate)} or {@link Matchable#when(Object)} to create it) matches this value
         * 
         * 
         * <pre>
         * {@code 
         *  Eval<Integer> result = Matchables.future(CompletableFuture.completedFuture(10))
                                         .matches(c-> 
                                                     c.is( when(some(10)), then(20)),  //success
                                                      
                                                     c->c.is(when(instanceOf(RuntimeException.class)), then(2)), //failure
                                                      
                                                     otherwise(3) //no match
                                                 );
        
             //Eval.now(20);
         * }
         * </pre>
         * @param when Tuple containing a predicate to match against this element
         * @param then Supplier to execute provide return value if match found
         * @return Case builder, allowing more cases to be chained
         */
        public final CheckValue1<T, R> is(final MTuple1<Predicate<? super T>> when, final Supplier<? extends R> then) {
            return isWhere(then, when.getMatchable().v1);
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        private final CheckValue1<T, R> isWhere(final Supplier<? extends R> result, final Predicate<? super T> value) {
            final Predicate predicate = it -> Optional.of(it)
                                                      .map(v -> v.getClass()
                                                                 .isAssignableFrom(clazz))
                                                      .orElse(false);
            // add wildcard support

            final Predicate<T>[] predicates = ReactiveSeq.of(value)
                                                         .map(nextValue -> simplerCase.convertToPredicate(nextValue))
                                                         .toListX()
                                                         .plus(i -> SeqUtils.EMPTY == i)
                                                         .toArray(new Predicate[0]);

            return new CheckValue1(
                                   clazz, new MatchableCase(
                                                            this.getPatternMatcher()
                                                                .inCaseOfManyType(predicate, i -> result.get(), predicates)));
        }

        PatternMatcher getPatternMatcher() {
            return simplerCase.getPatternMatcher();
        }
    }
    /**
     * Class used to build a pattern matching class against two values
     * 
     *  cyclops-react pattern matching uses the is /has methods to denote individual cases.

        is : indicates exact matching
    
        has : indicates that the supplied values must be present in order
    
        <pre>
        {@code 
            is(when(1,2,3),then("match")) : must have values 1,2,3 only in that order
    
            has(when(1,2,3),then("match")) : must have values 1,2,3 in that order, but may also have additional values
        }
        </pre>    * 
     * @author johnmcclean
     *
     * @param <T> Input type to match against
     * @param <R> Return type if matched
     */
    @AllArgsConstructor(access = AccessLevel.PUBLIC)
    public static class CheckValue2<T1, T2, R> {
        private final Class<Tuple2<T1, T2>> clazz;
        protected final MatchableCase<R> simplerCase;
        /**
         * Check that the provided Tuple-of-Predicates (use {@link Matchable#when(Predicate, Predicate)} or {@link Matchable#when(Object,Object)} to create it) matches the first value
         * of our target values to match on
         * <pre>
         * {@code 
           String result = Matchables.lines(new File(file))
                                  .on$12___()
                                  .matches(c->c.is(when("hello","world"),then("correct")), otherwise("miss")).get();
        
            Eval["correct"]
        
         * 
         * }
         * </pre>
         * @param when  Tuple containing a predicates to match against the first 2 elements / fields
         * @param then Supplier to execute provide return value if match found
         * @return Case builder, allowing more cases to be chained
         */
        @SuppressWarnings({ "rawtypes", "unchecked" })
        public final CheckValue2<T1, T2, R> is(final MTuple2<Predicate<? super T1>, Predicate<? super T2>> when, final Supplier<? extends R> then) {
            return isWhere(then, when.getMatchable().v1, when.getMatchable().v2);
        }

        
        @SuppressWarnings({ "rawtypes", "unchecked" })
        private final CheckValue2<T1, T2, R> isWhere(final Supplier<? extends R> result, final Predicate<? super T1> value1,
                final Predicate<? super T2> value2) {
            final Predicate predicate = it -> Optional.of(it)
                                                      .map(v -> v.getClass()
                                                                 .isAssignableFrom(clazz))
                                                      .orElse(false);
            // add wildcard support

            final Predicate[] predicates = ReactiveSeq.of(value1, value2)
                                                      .map(nextValue -> simplerCase.convertToPredicate(nextValue))
                                                      .toListX()
                                                      .plus(i -> SeqUtils.EMPTY == i)
                                                      .toArray(new Predicate[0]);

            return new CheckValue2(
                                   clazz, new MatchableCase(
                                                            this.getPatternMatcher()
                                                                .inCaseOfManyType(predicate, i -> result.get(), predicates)));
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        public final CheckValue2<T1, T2, R> isEmpty(final Supplier<? extends R> then) {

            final Predicate predicate = it -> Maybe.ofNullable(it)
                                                   .map(v -> v.getClass()
                                                              .isAssignableFrom(clazz))
                                                   .orElse(false);
            // add wildcard support

            final Predicate[] predicates = new Predicate[] { i -> i == SeqUtils.EMPTY };

            return new CheckValue2(
                                   clazz, new MatchableCase(
                                                            this.getPatternMatcher()
                                                                .inCaseOfManyType(predicate, i -> then.get(), predicates)));
        }

        PatternMatcher getPatternMatcher() {
            return simplerCase.getPatternMatcher();
        }
    }
    /**
     * Class used to build a pattern matching class against three values
     * 
     * 
     *  cyclops-react pattern matching uses the is /has methods to denote individual cases.

        is : indicates exact matching
    
        has : indicates that the supplied values must be present in order
    
        <pre>
        {@code 
            is(when(1,2,3),then("match")) : must have values 1,2,3 only in that order
    
            has(when(1,2,3),then("match")) : must have values 1,2,3 in that order, but may also have additional values
        }
        </pre>    * 
     * 
     * @author johnmcclean
     *
     * @param <T> Input type to match against
     * @param <R> Return type if matched
     */
    @AllArgsConstructor(access = AccessLevel.PUBLIC)
    public static class CheckValue3<T1, T2, T3, R> {
        private final Class<Tuple3<T1, T2, T3>> clazz;
        protected final MatchableCase<R> simplerCase;
        /**
         *  Check that the provided Tuple-of-Predicates (use {@link Matchable#when(Predicate, Predicate, Predicate)} or {@link Matchable#when(Object,Object,Object)} to create it) 
         *  matches the first 3 values  of our target values to match on
         * 
         * <pre>
         * {@code 
         * Matchables.match3(100,2,1000)
                     .matches(c->c.is(when(Predicates.greaterThan(50),Predicates.lessThan(10),Predicates.greaterThan(500)), ()->"large and small and huge"), ()->"not large and small")
                     .get(),
           //"large and small and huge"
         * 
         * }
         * </pre>
         * 
         * @param when Tuple containing a predicates to match against the first 3 elements / fields
         * @param then Supplier to execute provide return value if match found
         * @return Case builder, allowing more cases to be chained
         */
        @SuppressWarnings({ "rawtypes", "unchecked" })
        public final CheckValue3<T1, T2, T3, R> is(final MTuple3<Predicate<? super T1>, Predicate<? super T2>, Predicate<? super T3>> when,
                final Supplier<? extends R> then) {
            return isWhere(then, when.getMatchable()
                                     .v1(),
                           when.getMatchable()
                               .v2(),
                           when.getMatchable()
                               .v3());
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        private final CheckValue3<T1, T2, T3, R> isWhere(final Supplier<? extends R> result, final Predicate<? super T1> value1,
                final Predicate<? super T2> value2, final Predicate<? super T3> value3) {
            final Predicate predicate = it -> Optional.of(it)
                                                      .map(v -> v.getClass()
                                                                 .isAssignableFrom(clazz))
                                                      .orElse(false);
            // add wildcard support

            final Predicate[] predicates = ReactiveSeq.of(value1, value2, value3)
                                                      .map(nextValue -> simplerCase.convertToPredicate(nextValue))
                                                      .toListX()
                                                      .plus(i -> SeqUtils.EMPTY == i)
                                                      .toArray(new Predicate[0]);

            return new CheckValue3(
                                   clazz, new MatchableCase(
                                                            this.getPatternMatcher()
                                                                .inCaseOfManyType(predicate, i -> result.get(), predicates)));
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        public final CheckValue3<T1, T2, T3, R> isEmpty(final Supplier<? extends R> then) {

            final Predicate predicate = it -> Maybe.ofNullable(it)
                                                   .map(v -> v.getClass()
                                                              .isAssignableFrom(clazz))
                                                   .orElse(false);
            // add wildcard support

            final Predicate[] predicates = new Predicate[] { i -> i == SeqUtils.EMPTY };

            return new CheckValue3(
                                   clazz, new MatchableCase(
                                                            this.getPatternMatcher()
                                                                .inCaseOfManyType(predicate, i -> then.get(), predicates)));
        }

        PatternMatcher getPatternMatcher() {
            return simplerCase.getPatternMatcher();
        }
    }
    /**
     * Class used to build a pattern matching class against four values
     * 
     * 
     *  cyclops-react pattern matching uses the is /has methods to denote individual cases.

        is : indicates exact matching
    
        has : indicates that the supplied values must be present in order
    
        <pre>
        {@code 
            is(when(1,2,3),then("match")) : must have values 1,2,3 only in that order
    
            has(when(1,2,3),then("match")) : must have values 1,2,3 in that order, but may also have additional values
        }
        </pre>     * 
     * @author johnmcclean
     *
     * @param <T> Input type to match against
     * @param <R> Return type if matched
     */
    @AllArgsConstructor(access = AccessLevel.PUBLIC)
    public static class CheckValue4<T1, T2, T3, T4, R> {
        private final Class<Tuple4<T1, T2, T3, T4>> clazz;
        protected final MatchableCase<R> simplerCase;

        /**
         *  Check that the provided Tuple-of-Predicates (use {@link Matchable#when(Predicate, Predicate, Predicate, Predicate)} or {@link Matchable#when(Object,Object,Object, Object)} 
         *  to create it)  matches the first 4 values  of our target values to match on
         * 
         * <pre>
         * {@code 
         *  Matchables.match4(100,2,1000,1)
                      .matches(c->c.is(when(Predicates.greaterThan(50),
                                       Predicates.lessThan(10),
                                       Predicates.greaterThan(500),
                                       Predicates.lessThan(2)), ()->"large and small and huge and tiny"), ()->"not large and small")
                      .get()
             
            Eval["large and small and huge and tiny"]
         * 
         * }
         * </pre>
         * @param when Tuple containing a predicates to match against the first 4 elements / fields
         * @param then Supplier to execute provide return value if match found
         * @return Case builder, allowing more cases to be chained
         */
        @SuppressWarnings({ "rawtypes", "unchecked" })
        public final CheckValue4<T1, T2, T3, T4, R> is(
                final MTuple4<Predicate<? super T1>, Predicate<? super T2>, Predicate<? super T3>, Predicate<? super T4>> when,
                final Supplier<? extends R> then) {
            return isWhere(then, when.getMatchable()
                                     .v1(),
                           when.getMatchable()
                               .v2(),
                           when.getMatchable()
                               .v3(),
                           when.getMatchable()
                               .v4());
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        private final CheckValue4<T1, T2, T3, T4, R> isWhere(final Supplier<? extends R> result, final Predicate<? super T1> value1,
                final Predicate<? super T2> value2, final Predicate<? super T3> value3, final Predicate<? super T4> value4) {
            final Predicate predicate = it -> Optional.of(it)
                                                      .map(v -> v.getClass()
                                                                 .isAssignableFrom(clazz))
                                                      .orElse(false);
            // add wildcard support

            final Predicate[] predicates = ReactiveSeq.of(value1, value2, value3, value4)
                                                      .map(nextValue -> simplerCase.convertToPredicate(nextValue))
                                                      .toListX()
                                                      .plus(i -> SeqUtils.EMPTY == i)
                                                      .toArray(new Predicate[0]);

            return new CheckValue4(
                                   clazz, new MatchableCase(
                                                            this.getPatternMatcher()
                                                                .inCaseOfManyType(predicate, i -> result.get(), predicates)));
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        public final CheckValue4<T1, T2, T3, T4, R> isEmpty(final Supplier<? extends R> then) {

            final Predicate predicate = it -> Maybe.ofNullable(it)
                                                   .map(v -> v.getClass()
                                                              .isAssignableFrom(clazz))
                                                   .orElse(false);
            // add wildcard support

            final Predicate[] predicates = new Predicate[] { i -> i == SeqUtils.EMPTY };

            return new CheckValue4(
                                   clazz, new MatchableCase(
                                                            this.getPatternMatcher()
                                                                .inCaseOfManyType(predicate, i -> then.get(), predicates)));
        }

        PatternMatcher getPatternMatcher() {
            return simplerCase.getPatternMatcher();
        }
    }
    /**
     * Class used to build a pattern matching class against a five values
     * 
        cyclops-react pattern matching uses the is /has methods to denote individual cases.

        is : indicates exact matching
    
        has : indicates that the supplied values must be present in order
    
        <pre>
        {@code 
            is(when(1,2,3),then("match")) : must have values 1,2,3 only in that order
    
            has(when(1,2,3),then("match")) : must have values 1,2,3 in that order, but may also have additional values
        }
        </pre> 
        
     * @author johnmcclean
     *
     * @param <T> Input type to match against
     * @param <R> Return type if matched
     */
    @AllArgsConstructor(access = AccessLevel.PUBLIC)
    public static class CheckValue5<T1, T2, T3, T4, T5, R> {
        private final Class<Tuple5<T1, T2, T3, T4, T5>> clazz;
        protected final MatchableCase<R> simplerCase;
        /**
         * Check that the provided Tuple-of-Predicates (use {@link Matchable#when(Predicate, Predicate, Predicate, Predicate, Predicate)} or {@link Matchable#when(Object,Object,Object, Object, Object)} 
         * to create it)  matches the first 5 values  of our target values to match on
         * 
         * @param when  Tuple containing a predicates to match against the first 5 elements / fields
         * @param then Supplier to execute provide return value if match found
         * @return Case builder, allowing more cases to be chained
         */
        @SuppressWarnings({ "rawtypes", "unchecked" })
        public final CheckValue5<T1, T2, T3, T4, T5, R> is(
                final MTuple5<Predicate<? super T1>, Predicate<? super T2>, Predicate<? super T3>, Predicate<? super T4>, Predicate<? super T5>> when,
                final Supplier<? extends R> then) {
            return isWhere(then, when.getMatchable()
                                     .v1(),
                           when.getMatchable()
                               .v2(),
                           when.getMatchable()
                               .v3(),
                           when.getMatchable()
                               .v4(),
                           when.getMatchable()
                               .v5());
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        private final CheckValue5<T1, T2, T3, T4, T5, R> isWhere(final Supplier<? extends R> result, final Predicate<? super T1> value1,
                final Predicate<? super T2> value2, final Predicate<? super T3> value3, final Predicate<? super T4> value4,
                final Predicate<? super T5> value5) {
            final Predicate predicate = it -> Optional.of(it)
                                                      .map(v -> v.getClass()
                                                                 .isAssignableFrom(clazz))
                                                      .orElse(false);
            // add wildcard support

            final Predicate[] predicates = ReactiveSeq.of(value1, value2, value3, value4)
                                                      .map(nextValue -> simplerCase.convertToPredicate(nextValue))
                                                      .toListX()
                                                      .plus(i -> SeqUtils.EMPTY == i)
                                                      .toArray(new Predicate[0]);

            return new CheckValue5(
                                   clazz, new MatchableCase(
                                                            this.getPatternMatcher()
                                                                .inCaseOfManyType(predicate, i -> result.get(), predicates)));
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        public final CheckValue4<T1, T2, T3, T4, R> isEmpty(final Supplier<? extends R> then) {

            final Predicate predicate = it -> Maybe.ofNullable(it)
                                                   .map(v -> v.getClass()
                                                              .isAssignableFrom(clazz))
                                                   .orElse(false);
            // add wildcard support

            final Predicate[] predicates = new Predicate[] { i -> i == SeqUtils.EMPTY };

            return new CheckValue4(
                                   clazz, new MatchableCase(
                                                            this.getPatternMatcher()
                                                                .inCaseOfManyType(predicate, i -> then.get(), predicates)));
        }

        PatternMatcher getPatternMatcher() {
            return simplerCase.getPatternMatcher();
        }
    }

    /**
     * Class used to build a pattern matching class against multiple values
     *  
     *  cyclops-react pattern matching uses the is /has methods to denote individual cases.

        is : indicates exact matching
    
        has : indicates that the supplied values must be present in order
    
        <pre>
        {@code 
            is(when(1,2,3),then("match")) : must have values 1,2,3 only in that order
    
            has(when(1,2,3),then("match")) : must have values 1,2,3 in that order, but may also have additional values
        }
        </pre>
     * @author johnmcclean
     *
     * @param <T> Input type to match against
     * @param <R> Return type if matched
     */
    @AllArgsConstructor(access = AccessLevel.PUBLIC)
    public static class CheckValues<T, R> {
        private final Class<T> clazz;
        protected final MatchableCase<R> simplerCase;

        /**
         * Check that each Predicate executed in order holds true against each element / field in order
         * If a match is found the supplier is executed and returned
         * 
         * @param when Predicates to test against our dataset
         * @param then Supplier to execute provide return value if match found
         * @return Case builder, allowing more cases to be chained
         */
        public final CheckValues<T, R> is(final Iterable<Predicate<? super T>> when, final Supplier<? extends R> then) {
            return isWhere(then, when);
        }

        /**
         * Check that the provided Tuple-Predicate (use {@link Matchable#when(Predicate)} or {@link Matchable#when(Object)} to create it) matches the first value
         * of our target values to match on
         * 
         * <pre>
         * {@code 
         *  Matchables.listOfValues(1,2,3)
                      .matches(c->c.is(when((Object i)->(i instanceof Integer)),then(2)),
                               otherwise(-1)),
            //Eval[2]
         * }
         * </pre>
         * @param when Tuple containing a predicate to match against our first element / field
         * @param then Supplier to execute provide return value if match found
         * @return Case builder, allowing more cases to be chained
         */
        public final CheckValues<T, R> is(final MTuple1<Predicate<? super T>> when, final Supplier<? extends R> then) {
            return isWhere(then, Arrays.asList(when.getMatchable().v1));
        }

        /**
         * Check that the provided Tuple-of-Predicates (use {@link Matchable#when(Predicate, Predicate)} or {@link Matchable#when(Object,Object)} to create it) 
         * matches the first two values of our target values to match on
         * <pre>
         * {@code 
         * Eval<String> result = Matchables.listOfValues(1,new MyCase(4,5,6))
                                .matches(c->c.is(when(__,Predicates.has(4,5,6)),then("rec")),
                                         otherwise("n/a"));
        
           Eval["rec"]
         * 
         * }
         * </pre>
         * @param when  Tuple containing a predicates to match against the first 2 elements / fields
         * @param then Supplier to execute provide return value if match found
         * @return Case builder, allowing more cases to be chained
         */
        public final CheckValues<T, R> is(final MTuple2<Predicate<? super T>, Predicate<? super T>> when, final Supplier<? extends R> then) {
            return isWhere(then, Arrays.asList(when.getMatchable().v1, when.getMatchable().v2));
        }

        /**
         *  Check that the provided Tuple-of-Predicates (use {@link Matchable#when(Predicate, Predicate, Predicate)} or {@link Matchable#when(Object,Object,Object)} to create it) 
         *  matches the first 3 values  of our target values to match on
         * 
         * @param when Tuple containing a predicates to match against the first 3 elements / fields
         * @param then Supplier to execute provide return value if match found
         * @return Case builder, allowing more cases to be chained
         */
        public final CheckValues<T, R> is(final MTuple3<Predicate<? super T>, Predicate<? super T>, Predicate<? super T>> when,
                final Supplier<? extends R> then) {
            return isWhere(then, Arrays.asList(when.getMatchable().v1, when.getMatchable().v2, when.getMatchable().v3));
        }

        /**
         *  Check that the provided Tuple-of-Predicates (use {@link Matchable#when(Predicate, Predicate, Predicate, Predicate)} or {@link Matchable#when(Object,Object,Object, Object)} 
         *  to create it)  matches the first 4 values  of our target values to match on
         * 
         * @param when Tuple containing a predicates to match against the first 4 elements / fields
         * @param then Supplier to execute provide return value if match found
         * @return Case builder, allowing more cases to be chained
         */
        public final CheckValues<T, R> is(final MTuple4<Predicate<? super T>, Predicate<? super T>, Predicate<? super T>, Predicate<? super T>> when,
                final Supplier<? extends R> then) {
            return isWhere(then, Arrays.asList(when.getMatchable().v1, when.getMatchable().v2, when.getMatchable().v3, when.getMatchable().v4));
        }

        /**
         * Check that the provided Tuple-of-Predicates (use {@link Matchable#when(Predicate, Predicate, Predicate, Predicate, Predicate)} or {@link Matchable#when(Object,Object,Object, Object, Object)} 
         * to create it)  matches the first 5 values  of our target values to match on
         * 
         * @param when  Tuple containing a predicates to match against the first 5 elements / fields
         * @param then Supplier to execute provide return value if match found
         * @return Case builder, allowing more cases to be chained
         */
        public final CheckValues<T, R> is(
                final MTuple5<Predicate<? super T>, Predicate<? super T>, Predicate<? super T>, Predicate<? super T>, Predicate<? super T>> when,
                final Supplier<? extends R> then) {
            return isWhere(then, Arrays.asList(when.getMatchable().v1, when.getMatchable().v2, when.getMatchable().v3, when.getMatchable().v4,
                                               when.getMatchable().v5));
        }

        public final CheckValues<T, R> has(final Iterable<Predicate<? super T>> when, final Supplier<? extends R> then) {
            return hasWhere(then, when);
        }

        public final CheckValues<T, R> has(final MTuple1<Predicate<? super T>> when, final Supplier<? extends R> then) {
            return hasWhere(then, Arrays.asList(when.getMatchable().v1));
        }

        public final CheckValues<T, R> has(final MTuple2<Predicate<? super T>, Predicate<? super T>> when, final Supplier<? extends R> then) {
            return hasWhere(then, Arrays.asList(when.getMatchable().v1, when.getMatchable().v2));
        }

        public final CheckValues<T, R> has(final MTuple3<Predicate<? super T>, Predicate<? super T>, Predicate<? super T>> when,
                final Supplier<? extends R> then) {
            return hasWhere(then, Arrays.asList(when.getMatchable().v1, when.getMatchable().v2, when.getMatchable().v3));
        }

        public final CheckValues<T, R> has(final MTuple4<Predicate<? super T>, Predicate<? super T>, Predicate<? super T>, Predicate<? super T>> when,
                final Supplier<? extends R> then) {
            return hasWhere(then, Arrays.asList(when.getMatchable().v1, when.getMatchable().v2, when.getMatchable().v3, when.getMatchable().v4));
        }

        public final CheckValues<T, R> has(
                final MTuple5<Predicate<? super T>, Predicate<? super T>, Predicate<? super T>, Predicate<? super T>, Predicate<? super T>> when,
                final Supplier<? extends R> then) {
            return hasWhere(then, Arrays.asList(when.getMatchable().v1, when.getMatchable().v2, when.getMatchable().v3, when.getMatchable().v4,
                                                when.getMatchable().v5));
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        private final CheckValues<T, R> isWhere(final Supplier<? extends R> result, final Iterable<Predicate<? super T>> values) {
            final Predicate predicate = it -> Optional.of(it)
                                                      .map(v -> v.getClass()
                                                                 .isAssignableFrom(clazz))
                                                      .orElse(false);
            // add wildcard support

            final Predicate<T>[] predicates = ReactiveSeq.fromIterable(values)
                                                         .map(nextValue -> simplerCase.convertToPredicate(nextValue))
                                                         .toListX()
                                                         .plus(i -> SeqUtils.EMPTY == i)
                                                         .toArray(new Predicate[0]);

            return new MatchableCase(
                                     this.getPatternMatcher()
                                         .inCaseOfManyType(predicate, i -> result.get(), predicates)).withType(clazz);
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        private final <V> CheckValues<T, R> hasWhere(final Supplier<? extends R> result, final Iterable<Predicate<? super T>> values) {

            final Predicate predicate = it -> Optional.of(it)
                                                      .map(v -> v.getClass()
                                                                 .isAssignableFrom(clazz))
                                                      .orElse(false);
            // add wildcard support

            final Predicate<V>[] predicates = ReactiveSeq.fromIterable(values)
                                                         .map(nextValue -> simplerCase.convertToPredicate(nextValue))
                                                         .toList()
                                                         .toArray(new Predicate[0]);

            return new MatchableCase(
                                     this.getPatternMatcher()
                                         .inCaseOfManyType(predicate, i -> result.get(), predicates)).withType(clazz);
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        public final <V> CheckValues<T, R> isEmpty(final Supplier<? extends R> then) {

            final Predicate predicate = it -> Maybe.ofNullable(it)
                                                   .map(v -> v.getClass()
                                                              .isAssignableFrom(clazz))
                                                   .orElse(false);
            // add wildcard support

            final Predicate<V>[] predicates = new Predicate[] { i -> i == SeqUtils.EMPTY };

            return new MatchableCase(
                                     this.getPatternMatcher()
                                         .inCaseOfManyType(predicate, i -> then.get(), predicates)).withType(clazz);
        }

        PatternMatcher getPatternMatcher() {
            return simplerCase.getPatternMatcher();
        }
    }

}
