package com.sentinelpay.backend.simulator;

import java.util.Random;
import java.io.PrintStream;

/** Runs a finite console simulation; defaults to ten events without a delay. */
public class PaymentSimulatorDemo {

    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args, PrintStream output, PrintStream error) {
        if (args.length == 1 && args[0].equals("--help")) {
            output.println(DemoOptions.USAGE);
            return 0;
        }
        try {
            DemoOptions options = DemoOptions.parse(args);
            Random random = options.seed() == null ? new Random() : new Random(options.seed());
            var simulator = new PaymentSimulator(random, options.scenario().settings());
            SimulationRunner.run(simulator, options.count(), options.intervalMs(), output::println);
            if (output.checkError()) {
                error.println("Unable to write simulation output.");
                return 1;
            }
            return 0;
        } catch (IllegalArgumentException ex) {
            error.println("Invalid demo options: " + ex.getMessage());
            error.println(DemoOptions.USAGE);
            return 2;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            error.println("Simulation interrupted.");
            return 130;
        }
    }
}
