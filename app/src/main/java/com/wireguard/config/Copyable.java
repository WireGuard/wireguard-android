package com.wireguard.config;

/**
 * Interface for classes that can perform a deep copy of their objects.
 */

public interface Copyable<T> {
    T copy();
    void copyFrom(T source);
}
