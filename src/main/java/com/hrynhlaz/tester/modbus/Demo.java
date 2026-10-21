/*
 *  Пример использования
 */

package com.hrynhlaz.tester.modbus;

public class Demo {
    public static void main(String[] args) {
        String host = "192.168.0.10";
        int port = 502;
        int unitId = 1;

        try (ModbusTcpMaster master = new ModbusTcpMaster(host, port, unitId, 3000)) {
            master.connect();

            // Чтение 8 выходов (coils) с адреса 0
            boolean[] coils = master.readCoils(0, 8);
            System.out.println("Coils:");
            for (int i = 0; i < coils.length; i++) {
                System.out.printf("  %d = %s%n", i, coils[i]);
            }

            int[] holding = master.readHoldingRegisters(0, 4);
            System.out.println("HR0 = " + holding[0]);

            master.writeSingleRegister(10, 1234);

            master.writeMultipleRegisters(20, new int[]{100, 200, 300});

            // Установка выхода 0 в 1
            master.writeSingleCoil(0, true);
            System.out.println("Coil 0 set to ON");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
