package de.tuberlin.pserver.test.core.programs;


import de.tuberlin.pserver.core.net.NetEvents;
import de.tuberlin.pserver.core.net.NetManager;
import de.tuberlin.pserver.dsl.controlflow.program.Program;
import de.tuberlin.pserver.runtime.MLProgram;

import java.util.concurrent.CyclicBarrier;

public class EventSystemSendReceiveTestJob extends MLProgram {

    public static final int NUM_MSG = 20000;

    @Override
    public void define(final Program program) {

        final NetManager netManager = slotContext.programContext.runtimeContext.netManager;

        program.process(() -> {

            CF.parScope().node(0).slot(0).exe(() -> {

                final CyclicBarrier barrier = new CyclicBarrier(2);

                netManager.addEventListener("test-pong", event -> {
                    try {
                        barrier.await();
                    } catch (Exception e) {
                        LOG.error(e.getMessage());
                    }
                });

                // TODO: We have to wait here until the receiver has
                // TODO: registered his event handler, else the test deadlocks.
                Thread.sleep(3000);

                for (int i = 0; i < NUM_MSG; ++i) {

                    netManager.sendEvent(1, new NetEvents.NetEvent("test-ping", true));

                    barrier.await();
                }

                System.out.println("-- FINISH NODE 0");
            });

            // ---------------------------------------------------

            CF.parScope().node(1).slot(0).exe(() -> {

                final CyclicBarrier barrier = new CyclicBarrier(2);

                netManager.addEventListener("test-ping", event -> {
                    try {
                        barrier.await();
                    } catch (Exception e) {
                        LOG.error(e.getMessage());
                    }
                });

                for (int i = 0; i < NUM_MSG; ++i) {

                    barrier.await();

                    netManager.sendEvent(0, new NetEvents.NetEvent("test-pong", true));
                }

                System.out.println("-- FINISH NODE 1");
            });
        });
    }
}