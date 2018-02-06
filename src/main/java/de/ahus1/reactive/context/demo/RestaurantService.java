package de.ahus1.reactive.context.demo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.sleuth.SpanName;
import org.springframework.cloud.sleuth.annotation.NewSpan;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * @author Alexander Schwartz 2018
 */
@Service
@Slf4j
public class RestaurantService {

    @NewSpan("byPrice")
    public Flux<Restaurant> byPrice(Double maxPrice) {
        log.debug("inside byPrice");
        return Flux.fromArray(new Restaurant[] { new Restaurant("McDonalds", 1) });
    }
}
