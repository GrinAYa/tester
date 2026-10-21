/*
 *  Что делает этот Poller:
 *
 *  Работает в отдельном потоке.
 *
 *  Опрос выполняется строго по таймеру.
 *
 *  Ошибки не ломают поток — передаются слушателю.
 *
 *  Можно подписать UI, логгер, SCADA‑точки.
 *
 *  Можно опрашивать любые FC (добавь свои методы).
 */

package com.hrynhlaz.tester.modbus;

public class ModbusPoller {
    private final ModbusTcpMaster master;
    private final int pollIntervalMs;
    private final int coilsStart;
    private final int coilsQty;
    private final int hrStart;
    private final int hrQty;

    private ModbusPollListener listener;
    private Thread thread;
    private volatile boolean running = false;

    public ModbusPoller(ModbusTcpMaster master,
                        int pollIntervalMs,
                        int coilsStart, int coilsQty,
                        int hrStart, int hrQty) {

        this.master = master;
        this.pollIntervalMs = pollIntervalMs;
        this.coilsStart = coilsStart;
        this.coilsQty = coilsQty;
        this.hrStart = hrStart;
        this.hrQty = hrQty;
    }

    public void setListener(ModbusPollListener listener) {
        this.listener = listener;
    }

    public void start() {
        if (running) return;
        running = true;

        thread = new Thread(this::runLoop, "ModbusPoller");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void runLoop() {
        while (running) {
            try {
                // Опрос coils
                boolean[] coils = master.readCoils(coilsStart, coilsQty);
                if (listener != null) {
                    listener.onCoilsUpdate(coils);
                }

                // Опрос holding registers
                int[] hr = master.readHoldingRegisters(hrStart, hrQty);
                if (listener != null) {
                    listener.onHoldingRegistersUpdate(hr);
                }

            } catch (Exception e) {
                if (listener != null) {
                    listener.onError(e);
                }
            }

            try {
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException ignored) {
                // выход из цикла при stop()
            }
        }
    }
}
