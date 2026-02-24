package lab1.infrastructure.storage;

import lab1.application.ports.IStorageGateway;
import lab1.domain.exceptions.DomainException;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

/**
 * Реализация шлюза для работы с бинарными файлами.
 * Обеспечивает создание, открытие, закрытие и удаление файлов .prd и .prs.
 */
public class BinaryStorageGateway implements IStorageGateway {
    private static final Logger log = Logger.getLogger(BinaryStorageGateway.class.getName());
    private static final String PRD_EXTENSION = ".prd";
    private static final String PRS_EXTENSION = ".prs";

    private RandomAccessFile prdFile;
    private RandomAccessFile prsFile;
    private String prdFilename;
    private String prsFilename;
    private int nameLength;
    private boolean isOpen;

    /**
     * Создает новый экземпляр шлюза.
     */
    public BinaryStorageGateway() {
        this.isOpen = false;
    }

    @Override
    public void create(String prdFile, String prsFile, int nameLength) {
        try {
            // Проверяем расширения файлов
            if (!prdFile.endsWith(PRD_EXTENSION)) {
                prdFile += PRD_EXTENSION;
            }
            if (!prsFile.endsWith(PRS_EXTENSION)) {
                prsFile += PRS_EXTENSION;
            }

            this.prdFilename = prdFile;
            this.prsFilename = prsFile;
            this.nameLength = nameLength;

            // Создаем новые файлы
            this.prdFile = new RandomAccessFile(prdFile, "rw");
            this.prsFile = new RandomAccessFile(prsFile, "rw");

            // Инициализируем структуру .prd файла
            initializePrdFile();

            // Инициализируем структуру .prs файла
            initializePrsFile();

            this.isOpen = true;
            log.info("Created files: " + prdFile + ", " + prsFile + " with name length: " + nameLength);
        } catch (IOException e) {
            throw new DomainException("Failed to create files: " + e.getMessage());
        }
    }

    /**
     * Инициализирует заголовок .prd файла.
     *
     * @throws IOException при ошибках записи
     */
    private void initializePrdFile() throws IOException {
        prdFile.setLength(0); // Очищаем файл

        // Пишем сигнатуру (2 байта)
        BinaryIO.writeSignature(prdFile);

        // Пишем длину записи данных (2 байта)
        BinaryIO.writeShort(prdFile, 2, (short) nameLength);

        // Пишем указатель на первую запись (-1 = нет записей) (4 байта)
        BinaryIO.writeInt(prdFile, 4, BinaryIO.getNullPointer());

        // Пишем указатель на свободную область (после заголовка) (4 байта)
        int firstFreeOffset = 2 + 2 + 4 + 4 + 16; // сигнатура + длина + firstPtr + freePtr + prsFilename
        BinaryIO.writeInt(prdFile, 8, firstFreeOffset);

        // Пишем имя файла спецификаций (16 байт)
        BinaryIO.writeString(prdFile, 12, prsFilename, 16);
    }

    /**
     * Инициализирует заголовок .prs файла.
     *
     * @throws IOException при ошибках записи
     */
    private void initializePrsFile() throws IOException {
        prsFile.setLength(0); // Очищаем файл

        // Пишем указатель на первую запись (-1 = нет записей) (4 байта)
        BinaryIO.writeInt(prsFile, 0, BinaryIO.getNullPointer());

        // Пишем указатель на свободную область (после заголовка) (4 байта)
        BinaryIO.writeInt(prsFile, 4, 8); // заголовок 8 байт
    }

    @Override
    public void open(String prdFile, String prsFile) {
        try {
            if (!prdFile.endsWith(PRD_EXTENSION)) {
                prdFile += PRD_EXTENSION;
            }
            if (!prsFile.endsWith(PRS_EXTENSION)) {
                prsFile += PRS_EXTENSION;
            }

            this.prdFilename = prdFile;
            this.prsFilename = prsFile;

            // Открываем файлы в режиме чтения/записи
            this.prdFile = new RandomAccessFile(prdFile, "rw");
            this.prsFile = new RandomAccessFile(prsFile, "rw");

            // Проверяем сигнатуру .prd файла
            if (!BinaryIO.checkSignature(this.prdFile)) {
                throw new DomainException("Invalid PRD file signature");
            }

            // Читаем длину имени из заголовка
            this.nameLength = BinaryIO.readShort(this.prdFile, 2);

            this.isOpen = true;
            log.info("Opened files: " + prdFile + ", " + prsFile);
        } catch (IOException e) {
            throw new DomainException("Failed to open files: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        try {
            if (prdFile != null) {
                prdFile.close();
                prdFile = null;
            }
            if (prsFile != null) {
                prsFile.close();
                prsFile = null;
            }
            isOpen = false;
            log.info("Closed files");
        } catch (IOException e) {
            throw new DomainException("Failed to close files: " + e.getMessage());
        }
    }

    @Override
    public void flush() {
        try {
            if (prdFile != null) {
                prdFile.getFD().sync(); // Принудительная запись на диск
            }
            if (prsFile != null) {
                prsFile.getFD().sync();
            }
        } catch (IOException e) {
            throw new DomainException("Failed to flush files: " + e.getMessage());
        }
    }

    @Override
    public boolean exists(String filename) {
        if (!filename.endsWith(PRD_EXTENSION)) {
            filename += PRD_EXTENSION;
        }
        Path path = Paths.get(filename);
        return Files.exists(path);
    }

    @Override
    public void delete(String filename) {
        try {
            if (!filename.endsWith(PRD_EXTENSION)) {
                filename += PRD_EXTENSION;
            }
            Files.deleteIfExists(Paths.get(filename));

            // Также удаляем соответствующий .prs файл
            String prsFilename = filename.replace(PRD_EXTENSION, PRS_EXTENSION);
            Files.deleteIfExists(Paths.get(prsFilename));

            log.info("Deleted files: " + filename + ", " + prsFilename);
        } catch (IOException e) {
            throw new DomainException("Failed to delete files: " + e.getMessage());
        }
    }

    /**
     * Получает текущий RandomAccessFile для .prd.
     *
     * @return RandomAccessFile или null если файл не открыт
     */
    public RandomAccessFile getPrdFile() {
        return prdFile;
    }

    /**
     * Получает текущий RandomAccessFile для .prs.
     *
     * @return RandomAccessFile или null если файл не открыт
     */
    public RandomAccessFile getPrsFile() {
        return prsFile;
    }

    /**
     * Получает длину имени компонента.
     *
     * @return длина имени
     */
    public int getNameLength() {
        return nameLength;
    }

    /**
     * Проверяет, открыты ли файлы.
     *
     * @return true если файлы открыты
     */
    public boolean isOpen() {
        return isOpen;
    }

    /**
     * Получает имя .prd файла.
     *
     * @return имя файла
     */
    public String getPrdFilename() {
        return prdFilename;
    }

    /**
     * Получает имя .prs файла.
     *
     * @return имя файла
     */
    public String getPrsFilename() {
        return prsFilename;
    }
}