package de.ahus1.reactive.context.demo;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.annotation.ContinueSpan;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author Alexander Schwartz 2018
 */
@RestController
@Slf4j
@AllArgsConstructor
public class RestaurantController {

    private static final String API_ID = "apiID";
    private final RestaurantService restaurantService;
    private final Tracer tracer;

    @GetMapping("/byPriceHowItShouldWork")
    @ContinueSpan
    public Flux<Restaurant> byPriceHowItShouldWork(@RequestParam Double maxPrice,
                                    @RequestHeader(required = false, name = "X-UserId") String uid) {
        String apiId = uid == null ? "unknown" : uid;

        // if we want to add something to the MDC, we just do it.
        // We clean up like we would do in normal synchronous programming using finally.
        MDC.put(API_ID, apiId);
        try {

            // just a log statement to show the current context
            log.debug("starting statement");

            return restaurantService.byPrice(maxPrice)
                    .delayElements(Duration.ofSeconds(1))
                    .doOnNext(r ->
                            log.debug("found restaurant {} for ${}", r.getName(), r.getPricePerPerson())
                    )
                    .doOnComplete(() ->
                            log.debug("done!")
                    )
                    .doOnError(e ->
                            log.error("failure", e)
                    );
        } finally {
            MDC.remove(API_ID);
        }
    }

    @GetMapping("/byPrice")
    @ContinueSpan
    public Flux<Restaurant> byPrice(@RequestParam Double maxPrice,
                                    @RequestHeader(required = false, name = "X-UserId") String uid) {
        String apiId = uid == null ? "unknown" : uid;

        // if we want to add something to the MDC, we just do it.
        // We clean up like we would do in normal synchronous programming using finally.
        MDC.put(API_ID, apiId);
        try {

            // just a log statement to show the current context
            log.debug("starting statement");

            // we create a new span here to cover everything that is done in the reactive part.
            // we close it once the reactive processing is done.
            // If we wouldn't create a new span, everything would be executed in the current span
            Span backupSpan = tracer.getCurrentSpan();
            Span span = tracer.createSpan("internal", tracer.getCurrentSpan());
            try {

                log.debug("inside new span");

                return restaurantService.byPrice(maxPrice)
                        .delayElements(Duration.ofSeconds(1))
                        .doOnNext(wrap(r ->
                                log.debug("found restaurant {} for ${}", r.getName(), r.getPricePerPerson())
                        ))
                        .doOnComplete(wrap(() ->
                                log.debug("done!")
                        ))
                        .doOnError(wrap(e ->
                                log.error("failure", e)
                        ))
                        .doFinally(wrap(t -> {
                            tracer.close(span);
                        }));
            } finally {
                tracer.detach(span);
                tracer.continueSpan(backupSpan);
            }
        } finally {
            MDC.remove(API_ID);
        }
    }


    private Runnable wrap(final Runnable r) {
        Map<String, String> copyOfContextMap = MDC.getCopyOfContextMap();
        Span span = tracer.getCurrentSpan();
        return () -> {
            Map<String, String> backupOfContextMap = MDC.getCopyOfContextMap();
            Span backupSpan = tracer.getCurrentSpan();
            tracer.detach(backupSpan);
            MDC.setContextMap(copyOfContextMap);
            tracer.continueSpan(span);
            try {
                r.run();
            } finally {
                if (backupOfContextMap == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(backupOfContextMap);
                }
                tracer.detach(span);
                tracer.continueSpan(backupSpan);
            }
        };
    }

    private <T> Consumer<T> wrap(final Consumer<? super T> consumer) {
        Map<String, String> copyOfContextMap = MDC.getCopyOfContextMap();
        Span span = tracer.getCurrentSpan();
        return t -> {
            Map<String, String> backupOfContextMap = MDC.getCopyOfContextMap();
            Span backupSpan = tracer.getCurrentSpan();
            tracer.detach(backupSpan);
            MDC.setContextMap(copyOfContextMap);
            tracer.continueSpan(span);
            try {
                consumer.accept(t);
            /* the following might be helpful for debugging to indicate the element that raised the problem
            } catch (RuntimeException e) {
                throw new RuntimeException("unable to handle " + t, e);
            */
            } finally {
                if (backupOfContextMap == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(backupOfContextMap);
                }
                tracer.detach(span);
                tracer.continueSpan(backupSpan);
            }
        };
    }

    /** This is the idea to use the Hooks, but it tends to use a lot of classes */

    /*

    @PostConstruct
    @SuppressWarnings("unchecked")
    public <T> void setup() {
        Hooks.onEachOperator("log", new OnOperatorDebug());
    }

    final static class OnOperatorDebug<T>
            implements Function<Publisher<T>, Publisher<T>> {

        @Override
        @SuppressWarnings("unchecked")
        public Publisher<T> apply(Publisher<T> publisher) {
            if (publisher instanceof Callable) {
                if (publisher instanceof Mono) {
                    return new MonoCallableOnAssembly<>((Mono<T>) publisher);
                }
                return new FluxCallableOnAssembly<>((Flux<T>) publisher);
            }
            if (publisher instanceof Mono) {
                return new MonoOnAssembly<>((Mono<T>) publisher);
            }
            if (publisher instanceof ParallelFlux) {
                return new ParallelFluxOnAssembly<>((ParallelFlux<T>) publisher);
            }
            if (publisher instanceof ConnectableFlux) {
                return new ConnectableFluxOnAssembly<>((ConnectableFlux<T>) publisher);
            }
            return new FluxOnAssembly<>((Flux<T>) publisher);
        }
    }

    @PreDestroy
    public void teardown() {
        Hooks.resetOnEachOperator("log");
    }

    @GetMapping("/byPrice")
    public Flux<Restaurant> byPrice2(@RequestParam Double maxPrice,
                                     @RequestHeader(required = false, name = "X-UserId") String uid) {
        String apiId = uid == null ? "unknown" : uid;
        MDC.put("apiID", apiId);
        try {
            log.debug("starting statement");
            return restaurantService.byPrice(maxPrice)
                    .doOnNext(r -> log.debug("found restaurant {} for ${}", r.getName(), r.getPricePerPerson()));
        } finally {
            MDC.remove(API_ID);
        }
    }
    */

}
