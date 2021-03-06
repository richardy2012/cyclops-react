package com.aol.cyclops.types;

import java.util.stream.Collectors;

import com.aol.cyclops.internal.invokedynamic.ReflectionCache;
import com.aol.cyclops.util.ExceptionSoftener;

/**
 * Unapply returns an ordered Iterable of the values of this types fields
 * 
 * unapply uses Reflection by default, but clients can override it if neccessary
 * 
 * <pre>
 * {@code 
 *  @Value 
 *  public class DefaultDecomposable implements Decomposable{ 
 *      int num; 
 *      String name; 
 *      int num2;
 *   }
 *   
 *   new DefaultDecomposable(1,"hello",2).unapply();
 *   //[1,"hello",2]

 * }
 * </pre>
 * 
 * 
 * @author johnmcclean
 *
 */
public interface Decomposable {

    /**
     * unwrap will return any Wrapped typed. Classes may implement Decomposable directly,
     * in which case the default behaviour is to return this
     * 
     * @return The unwrapped Decomposable
     */
    default <T> T unwrap() {
        return (T) this;
    }

    /**
     * @return Values of the fields of this Decomposable instance
     */
    @SuppressWarnings("unchecked")
    default <I extends Iterable<?>> I unapply() {

        if (unwrap() instanceof Iterable)
            return (I) unwrap();
        try {

            return (I) ReflectionCache.getFields(unwrap().getClass())
                                      .stream()
                                      .map(f -> {
                                          try {

                                              return f.get(unwrap());
                                          } catch (final Exception e) {
                                              throw ExceptionSoftener.throwSoftenedException(e);

                                          }
                                      })
                                      .collect(Collectors.toList());
        } catch (final Exception e) {
            throw ExceptionSoftener.throwSoftenedException(e);

        }

    }

}
