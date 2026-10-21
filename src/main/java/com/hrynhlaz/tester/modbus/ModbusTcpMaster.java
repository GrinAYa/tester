/*
 *  Минимальный каркас Modbus TCP master на Java
 *  Простой, самодостаточный каркас без сторонних библиотек. Он умеет:
 *
 *  - устанавливать TCP‑соединение,
 *
 *  - формировать Modbus TCP ADU,
 *
 *  - выполнять базовые функции: чтение дискретных входов/выходов и запись одиночного выхода.
 */

package com.hrynhlaz.tester.modbus;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

public class ModbusTcpMaster implements AutoCloseable{

    private final String host;
    private final int port;
    private final int unitId;
    private final int timeoutMs;

    private final AtomicInteger transactionId = new AtomicInteger(1);

    private Socket socket;
    private InputStream in;
    private OutputStream out;

    public ModbusTcpMaster(String host, int port, int unitId, int timeoutMs) {
        this.host = host;
        this.port = port;
        this.unitId = (unitId & 0xFF);
        this.timeoutMs = timeoutMs;
    }

    public void connect() throws IOException {
        if (socket != null && socket.isConnected() && !socket.isClosed()) {
            return;
        }
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setSoTimeout(timeoutMs);
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    @Override
    public void close() throws IOException {
        if (socket != null) {
            socket.close();
        }
    }

    // ---------- Публичные методы протокола ----------

    /** Read Coils (FC 0x01) */
    public boolean[] readCoils(int startAddress, int quantity) throws IOException {
        byte functionCode = 0x01;
        byte[] pdu = buildReadRequestPdu(functionCode, startAddress, quantity);
        byte[] response = sendAndReceive(pdu);
        return parseCoilsResponse(response, quantity);
    }

    /** Read Discrete Inputs (FC 0x02) */
    public boolean[] readDiscreteInputs(int startAddress, int quantity) throws IOException {
        byte functionCode = 0x02;
        byte[] pdu = buildReadRequestPdu(functionCode, startAddress, quantity);
        byte[] response = sendAndReceive(pdu);
        return parseCoilsResponse(response, quantity);
    }

    /** Write Single Coil (FC 0x05) */
    public void writeSingleCoil(int address, boolean value) throws IOException {
        byte functionCode = 0x05;
        byte[] pdu = new byte[5];
        pdu[0] = functionCode;
        pdu[1] = (byte) ((address >> 8) & 0xFF);
        pdu[2] = (byte) (address & 0xFF);
        pdu[3] = (byte) (value ? 0xFF : 0x00);
        pdu[4] = 0x00;

        byte[] response = sendAndReceive(pdu);
        // Для каркаса достаточно проверить, что функция и адрес совпали
        if (response.length < 5 || response[0] != functionCode) {
            throw new IOException("Invalid response for Write Single Coil");
        }
    }

    /** Read Holding Registers (FC 0x03) */
    public int[] readHoldingRegisters(int startAddress, int quantity) throws IOException {
        byte functionCode = 0x03;
        byte[] pdu = buildReadRequestPdu(functionCode, startAddress, quantity);
        byte[] response = sendAndReceive(pdu);
        return parseRegistersResponse(response, quantity);
    }

    /** Read Input Registers (FC 0x04) */
    public int[] readInputRegisters(int startAddress, int quantity) throws IOException {
        byte functionCode = 0x04;
        byte[] pdu = buildReadRequestPdu(functionCode, startAddress, quantity);
        byte[] response = sendAndReceive(pdu);
        return parseRegistersResponse(response, quantity);
    }

    /** Write Single Register (FC 0x06) */
    public void writeSingleRegister(int address, int value) throws IOException {
        byte functionCode = 0x06;
        byte[] pdu = new byte[5];
        pdu[0] = functionCode;
        pdu[1] = (byte) ((address >> 8) & 0xFF);
        pdu[2] = (byte) (address & 0xFF);
        pdu[3] = (byte) ((value >> 8) & 0xFF);
        pdu[4] = (byte) (value & 0xFF);

        byte[] response = sendAndReceive(pdu);
        if (response.length < 5 || response[0] != functionCode) {
            throw new IOException("Invalid response for Write Single Register");
        }
    }

    /** Write Multiple Registers (FC 0x10) */
    public void writeMultipleRegisters(int startAddress, int[] values) throws IOException {
        byte functionCode = 0x10;
        int quantity = values.length;

        byte byteCount = (byte) (quantity * 2);
        byte[] pdu = new byte[6 + byteCount];

        pdu[0] = functionCode;
        pdu[1] = (byte) ((startAddress >> 8) & 0xFF);
        pdu[2] = (byte) (startAddress & 0xFF);
        pdu[3] = (byte) ((quantity >> 8) & 0xFF);
        pdu[4] = (byte) (quantity & 0xFF);
        pdu[5] = byteCount;

        int pos = 6;
        for (int v : values) {
            pdu[pos++] = (byte) ((v >> 8) & 0xFF);
            pdu[pos++] = (byte) (v & 0xFF);
        }

        byte[] response = sendAndReceive(pdu);
        if (response.length < 5 || response[0] != functionCode) {
            throw new IOException("Invalid response for Write Multiple Registers");
        }
    }







    // ---------- Внутренняя логика ----------

    // Парсер ответа для регистров
    private int[] parseRegistersResponse(byte[] pdu, int quantity) throws IOException {
        if (pdu.length < 2) {
            throw new IOException("Invalid registers response length");
        }

        byte functionCode = pdu[0];
        int byteCount = pdu[1] & 0xFF;

        if (byteCount != quantity * 2) {
            throw new IOException("Unexpected byte count in response");
        }

        int[] result = new int[quantity];
        int pos = 2;

        for (int i = 0; i < quantity; i++) {
            int hi = pdu[pos++] & 0xFF;
            int lo = pdu[pos++] & 0xFF;
            result[i] = (hi << 8) | lo;
        }

        return result;
    }


    private byte[] buildReadRequestPdu(byte functionCode, int startAddress, int quantity) {
        byte[] pdu = new byte[5];
        pdu[0] = functionCode;
        pdu[1] = (byte) ((startAddress >> 8) & 0xFF);
        pdu[2] = (byte) (startAddress & 0xFF);
        pdu[3] = (byte) ((quantity >> 8) & 0xFF);
        pdu[4] = (byte) (quantity & 0xFF);
        return pdu;
    }

    private byte[] sendAndReceive(byte[] pdu) throws IOException {
        ensureConnected();

        int tid = transactionId.getAndIncrement() & 0xFFFF;

        // MBAP header: 7 bytes
        byte[] adu = new byte[7 + pdu.length];
        // Transaction ID
        adu[0] = (byte) ((tid >> 8) & 0xFF);
        adu[1] = (byte) (tid & 0xFF);
        // Protocol ID = 0
        adu[2] = 0x00;
        adu[3] = 0x00;
        // Length = PDU length + UnitId (1 byte)
        int length = pdu.length + 1;
        adu[4] = (byte) ((length >> 8) & 0xFF);
        adu[5] = (byte) (length & 0xFF);
        // Unit ID
        adu[6] = (byte) unitId;
        // PDU
        System.arraycopy(pdu, 0, adu, 7, pdu.length);

        // Send
        out.write(adu);
        out.flush();

        // Read MBAP header
        byte[] header = readFully(7);
        int respTid = ((header[0] & 0xFF) << 8) | (header[1] & 0xFF);
        if (respTid != tid) {
            throw new IOException("Transaction ID mismatch");
        }
        int respLength = ((header[4] & 0xFF) << 8) | (header[5] & 0xFF);
        byte respUnitId = header[6];
        if ((respUnitId & 0xFF) != unitId) {
            throw new IOException("Unit ID mismatch");
        }

        // respLength = UnitId(1) + PDU
        int pduLen = respLength - 1;
        byte[] respPdu = readFully(pduLen);

        // Проверка на Exception Response (FC | 0x80)
        byte fc = respPdu[0];
        if ((fc & 0x80) != 0) {
            int excCode = respPdu[1] & 0xFF;
            throw new IOException("Modbus exception: " + excCode);
        }

        return respPdu;
    }

    private void ensureConnected() throws IOException {
        if (socket == null || !socket.isConnected() || socket.isClosed()) {
            connect();
        }
    }

    private byte[] readFully(int len) throws IOException {
        byte[] buf = new byte[len];
        int off = 0;
        while (off < len) {
            int r = in.read(buf, off, len - off);
            if (r < 0) {
                throw new IOException("Stream closed");
            }
            off += r;
        }
        return buf;
    }

    private boolean[] parseCoilsResponse(byte[] pdu, int quantity) throws IOException {
        if (pdu.length < 2) {
            throw new IOException("Invalid coils response length");
        }
        byte functionCode = pdu[0];
        byte byteCount = pdu[1];

        int expectedBytes = (quantity + 7) / 8;
        if ((byteCount & 0xFF) != expectedBytes) {
            throw new IOException("Unexpected byte count in response");
        }
        if (pdu.length < 2 + expectedBytes) {
            throw new IOException("Response too short");
        }

        boolean[] result = new boolean[quantity];
        for (int i = 0; i < quantity; i++) {
            int byteIndex = 2 + (i / 8);
            int bitIndex = i % 8;
            int b = pdu[byteIndex] & 0xFF;
            result[i] = ((b >> bitIndex) & 0x01) != 0;
        }
        return result;
    }

}
