package com.sentinelpay.backend.simulator;

import java.util.Random;

/** Prints ten simulated payment events and exits. */
public class PaymentSimulatorDemo {

    public static void main(String[] args) {
        PaymentSimulator simulator = new PaymentSimulator(new Random());

        for (int i = 0; i < 10; i++) {
            System.out.println(simulator.generateTransaction());
        }
    }
}
