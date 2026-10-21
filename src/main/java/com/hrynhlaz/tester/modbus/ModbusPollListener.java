/*
 *  Интерфейс слушателя данных (опционально)
 *  Чтобы SCADA могла реагировать на новые данные
 */

package com.hrynhlaz.tester.modbus;

public interface ModbusPollListener {
    void onCoilsUpdate(boolean[] coils);
    void onHoldingRegistersUpdate(int[] registers);
    void onError(Exception e);
}
