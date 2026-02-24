package lab1.infrastructure.storage;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Утилитарный класс для бинарного ввода/вывода с поддержкой прямого доступа к файлу.
 * Предоставляет методы для чтения/записи примитивных типов и строк в RandomAccessFile.
 */
public class BinaryIO {
    private static final String SIGNATURE = "PS";
    private static final byte DELETED = -1;
    private static final byte ACTIVE = 0;
    private static final int NULL_POINTER = -1;

    /**
     * Проверяет сигнатуру файла.
     *
     * @param raf файл для проверки
     * @return true если сигнатура корректна
     * @throws IOException при ошибках ввода/вывода
     */
    public static boolean checkSignature(RandomAccessFile raf) throws IOException {
        raf.seek(0);
        byte[] signature = new byte[2];
        raf.read(signature);
        return signature[0] == 'P' && signature[1] == 'S';
    }

    /**
     * Записывает сигнатуру в начало файла.
     *
     * @param raf файл для записи
     * @throws IOException при ошибках ввода/вывода
     */
    public static void writeSignature(RandomAccessFile raf) throws IOException {
        raf.seek(0);
        raf.writeBytes(SIGNATURE);
    }

    /**
     * Читает 2-байтовое целое (short).
     *
     * @param raf   файл для чтения
     * @param offset смещение от начала файла
     * @return прочитанное значение
     * @throws IOException при ошибках ввода/вывода
     */
    public static short readShort(RandomAccessFile raf, long offset) throws IOException {
        raf.seek(offset);
        return raf.readShort();
    }

    /**
     * Записывает 2-байтовое целое (short).
     *
     * @param raf    файл для записи
     * @param offset смещение от начала файла
     * @param value  значение для записи
     * @throws IOException при ошибках ввода/вывода
     */
    public static void writeShort(RandomAccessFile raf, long offset, short value) throws IOException {
        raf.seek(offset);
        raf.writeShort(value);
    }

    /**
     * Читает 4-байтовое целое (int).
     *
     * @param raf   файл для чтения
     * @param offset смещение от начала файла
     * @return прочитанное значение
     * @throws IOException при ошибках ввода/вывода
     */
    public static int readInt(RandomAccessFile raf, long offset) throws IOException {
        raf.seek(offset);
        return raf.readInt();
    }

    /**
     * Записывает 4-байтовое целое (int).
     *
     * @param raf    файл для записи
     * @param offset смещение от начала файла
     * @param value  значение для записи
     * @throws IOException при ошибках ввода/вывода
     */
    public static void writeInt(RandomAccessFile raf, long offset, int value) throws IOException {
        raf.seek(offset);
        raf.writeInt(value);
    }

    /**
     * Читает 1-байтовое целое (byte).
     *
     * @param raf   файл для чтения
     * @param offset смещение от начала файла
     * @return прочитанное значение
     * @throws IOException при ошибках ввода/вывода
     */
    public static byte readByte(RandomAccessFile raf, long offset) throws IOException {
        raf.seek(offset);
        return raf.readByte();
    }

    /**
     * Записывает 1-байтовое целое (byte).
     *
     * @param raf    файл для записи
     * @param offset смещение от начала файла
     * @param value  значение для записи
     * @throws IOException при ошибках ввода/вывода
     */
    public static void writeByte(RandomAccessFile raf, long offset, byte value) throws IOException {
        raf.seek(offset);
        raf.writeByte(value);
    }

    /**
     * Читает строку фиксированной длины.
     *
     * @param raf    файл для чтения
     * @param offset смещение от начала файла
     * @param length длина строки в байтах
     * @return прочитанная строка (обрезанная по пробелам)
     * @throws IOException при ошибках ввода/вывода
     */
    public static String readString(RandomAccessFile raf, long offset, int length) throws IOException {
        raf.seek(offset);
        byte[] bytes = new byte[length];
        raf.read(bytes);
        String result = new String(bytes, StandardCharsets.UTF_8).trim();
        return result.isEmpty() ? null : result;
    }

    /**
     * Записывает строку фиксированной длины (дополняет пробелами при необходимости).
     *
     * @param raf    файл для записи
     * @param offset смещение от начала файла
     * @param value  строка для записи
     * @param length требуемая длина в байтах
     * @throws IOException при ошибках ввода/вывода
     */
    public static void writeString(RandomAccessFile raf, long offset, String value, int length) throws IOException {
        raf.seek(offset);
        byte[] bytes = new byte[length];
        byte[] strBytes = value.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(strBytes, 0, bytes, 0, Math.min(strBytes.length, length));
        for (int i = strBytes.length; i < length; i++) {
            bytes[i] = ' ';
        }
        raf.write(bytes);
    }

    /**
     * Получает значение бита удаления.
     *
     * @param raf   файл для чтения
     * @param offset смещение записи
     * @return true если запись помечена на удаление
     * @throws IOException при ошибках ввода/вывода
     */
    public static boolean isDeleted(RandomAccessFile raf, long offset) throws IOException {
        return readByte(raf, offset) == DELETED;
    }

    /**
     * Устанавливает бит удаления.
     *
     * @param raf     файл для записи
     * @param offset  смещение записи
     * @param deleted true если нужно пометить на удаление
     * @throws IOException при ошибках ввода/вывода
     */
    public static void setDeleted(RandomAccessFile raf, long offset, boolean deleted) throws IOException {
        writeByte(raf, offset, (byte) (deleted ? DELETED : ACTIVE));
    }

    /**
     * Проверяет, является ли указатель пустым.
     *
     * @param pointer значение указателя
     * @return true если указатель пустой (-1)
     */
    public static boolean isNullPointer(int pointer) {
        return pointer == NULL_POINTER;
    }

    /**
     * Возвращает значение пустого указателя.
     *
     * @return -1
     */
    public static int getNullPointer() {
        return NULL_POINTER;
    }
}