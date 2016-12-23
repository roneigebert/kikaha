package kikaha.core.modules;

import io.undertow.UndertowMessages;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

/**
 * Handler that allows for graceful server shutdown. Basically it provides a way to prevent the server from
 * accepting new requests, and wait for existing requests to complete.
 * <p>
 * The handler itself does not shut anything down.
 * <p>
 * Import: The thread safety semantics of the handler are very important. Don't touch anything unless you know
 * what you are doing.
 *
 * <p>
 *     <b>Note</b>: this is a copy from the original {@link io.undertow.server.handlers.GracefulShutdownHandler}, only
 *     minor changes was made related to {@link ShutdownListener}s' life cycle.
 * </p>
 *
 * @author Stuart Douglas
 */
public class GracefulShutdownHandler implements HttpHandler {

    private volatile boolean shutdown = false;
    private final GracefulShutdownListener listener = new GracefulShutdownListener();
    private final List<ShutdownListener> shutdownListeners = new ArrayList<>();

    private final Object lock = new Object();

    private volatile long activeRequests = 0;
    private static final AtomicLongFieldUpdater<GracefulShutdownHandler> activeRequestsUpdater = AtomicLongFieldUpdater.newUpdater(GracefulShutdownHandler.class, "activeRequests");

    private final HttpHandler next;

    public GracefulShutdownHandler(HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        activeRequestsUpdater.incrementAndGet(this);
        if (shutdown) {
            decrementRequests();
            exchange.setStatusCode(StatusCodes.SERVICE_UNAVAILABLE);
            exchange.endExchange();
            return;
        }
        exchange.addExchangeCompleteListener(listener);
        next.handleRequest(exchange);
    }


    public void shutdown() {
        shutdown = true;
    }

    void forceShutdown(){
        synchronized (lock) {
            shutdown();
            shutdownComplete();
        }
    }

    private void shutdownComplete() {
        assert Thread.holdsLock(lock);
        lock.notifyAll();
        for (ShutdownListener listener : shutdownListeners) {
            listener.shutdown(true);
        }
        shutdownListeners.clear();
    }

    /**
     * Waits a set length of time for the handler to shut down
     *
     * @param millis The length of time
     * @return <code>true</code> If the handler successfully shut down
     */
    public boolean awaitShutdown(long millis) throws InterruptedException {
        synchronized (lock) {
            if (!shutdown) {
                throw UndertowMessages.MESSAGES.handlerNotShutdown();
            }
            long end = System.currentTimeMillis() + millis;
            int count = (int) activeRequestsUpdater.get(this);
            while (count != 0) {
                long left = end - System.currentTimeMillis();
                if (left <= 0) {
                    return false;
                }
                lock.wait(left);
                count = (int) activeRequestsUpdater.get(this);
            }
            return true;
        }
    }

    /**
     * Adds a shutdown listener that will be invoked when all requests have finished. If all requests have already been finished
     * the listener will be invoked immediately.
     *
     * @param shutdownListener The shutdown listener
     */
    public void addShutdownListener(final ShutdownListener shutdownListener) {
        synchronized (lock) {
            if (!shutdown) {
                shutdownListeners.add(shutdownListener);
                return;
            }
            long count = activeRequestsUpdater.get(this);
            if (count == 0) {
                shutdownListener.shutdown(true);
            } else {
                shutdownListeners.add(shutdownListener);
            }
        }
    }

    private void decrementRequests() {
        long active = activeRequestsUpdater.decrementAndGet(this);
        if (shutdown) {
            synchronized (lock) {
                if (active == 0) {
                    shutdownComplete();
                }
            }
        }
    }

    private final class GracefulShutdownListener implements ExchangeCompletionListener {

        @Override
        public void exchangeEvent(HttpServerExchange exchange, NextListener nextListener) {
            try {
                decrementRequests();
            } finally {
                nextListener.proceed();
            }
        }
    }

    /**
     * A listener which can be registered with the handler to be notified when all pending requests have finished.
     */
    public interface ShutdownListener {

        /**
         * Notification that the container has shutdown.
         *
         * @param shutdownSuccessful If the shutdown succeeded or not
         */
        void shutdown(boolean shutdownSuccessful);
    }
}