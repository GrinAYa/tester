package com.hrynhlaz.tester.modbus;

public class PollerTestDrive {
    public static void main(String[] args) {
        try(ModbusTcpMaster master = new ModbusTcpMaster("192.168.0.10", 502, 1, 3000)) {
            master.connect();




        ModbusPoller poller = new ModbusPoller(
                master,
                1000,   // интервал опроса 1 сек
                0, 8,   // coils: адрес 0, количество 8
                0, 4    // holding registers: адрес 0, количество 4
        );

        poller.setListener(new ModbusPollListener() {
            @Override
            public void onCoilsUpdate(boolean[] coils) {
                System.out.println("Coils updated:");
                for (int i = 0; i < coils.length; i++) {
                    System.out.println("  " + i + " = " + coils[i]);
                }
            }

            @Override
            public void onHoldingRegistersUpdate(int[] registers) {
                System.out.println("HR updated:");
                for (int i = 0; i < registers.length; i++) {
                    System.out.println("  HR" + i + " = " + registers[i]);
                }
            }

            @Override
            public void onError(Exception e) {
                System.err.println("Polling error: " + e.getMessage());
            }
        });

        poller.start();

// ... позже
// poller.stop();
    } catch (Exception e) {
        e.printStackTrace();
    }

    }
}
