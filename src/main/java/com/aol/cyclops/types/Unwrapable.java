package com.aol.cyclops.types;

/**
 * 
 * Data type that represents a wrapper type
 * 
 * @author johnmcclean
 *
 */
public interface Unwrapable {
    /**
     * Unwrap a wrapped value
     * 
     * @return wrapped value
     */
    default <R> R unwrap() {
        return (R) this;
    }
}
