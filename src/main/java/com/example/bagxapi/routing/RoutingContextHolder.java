package com.example.bagxapi.routing;

/**
 * Request-scoped holder for the {@link RoutingContext}.
 *
 * <p>A ThreadLocal is enough here because every request is handled synchronously on a single
 * servlet thread. If a layer ever goes reactive or hands work to an executor, this is the piece
 * that has to be replaced (context propagation), not the interceptor above it.</p>
 */
public final class RoutingContextHolder {

    private static final ThreadLocal<RoutingContext> CURRENT = new ThreadLocal<>();

    private RoutingContextHolder() {
    }

    public static void set(RoutingContext context) {
        CURRENT.set(context);
    }

    public static RoutingContext get() {
        RoutingContext context = CURRENT.get();
        return context != null ? context : RoutingContext.empty();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
