package de.ahus1.reactive.context.demo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Alexander Schwartz 2018
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Restaurant {
    private String name;
    private double pricePerPerson;
}
