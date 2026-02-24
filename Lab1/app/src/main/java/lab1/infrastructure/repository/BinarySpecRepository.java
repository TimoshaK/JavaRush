package lab1.infrastructure.repository;

import lab1.application.ports.ISpecRepository;
import lab1.domain.models.Component;
import lab1.domain.models.SpecLine;
import lab1.infrastructure.storage.BinaryIO;
import lab1.infrastructure.storage.BinaryStorageGateway;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;

/**
 * Реализация репозитория спецификаций с хранением в бинарном .prs файле.
 * Поддерживает прямые ссылки (оффсеты) для быстрого доступа к записям.
 */
public class BinarySpecRepository implements ISpecRepository {
    private final BinaryStorageGateway gateway;
    private final BinaryComponentRepository componentRepo;
    private final Map<Long, SpecLine> cache = new HashMap<>();
    private final Map<Component, List<SpecLine>> ownerCache = new HashMap<>();
    private boolean isLoaded = false;

    // Смещения полей в записи спецификации (после заголовка)
    private static final int DELETED_FLAG_OFFSET = 0;       // 1 байт
    private static final int COMPONENT_PTR_OFFSET = 1;      // 4 байта
    private static final int QUANTITY_OFFSET = 5;           // 2 байта
    private static final int NEXT_SPEC_OFFSET = 7;          // 4 байта
    private static final int RECORD_SIZE = 11;              // общий размер записи

    /**
     * Создает новый экземпляр репозитория.
     *
     * @param gateway шлюз для доступа к файлам
     * @param componentRepo репозиторий компонентов для разрешения ссылок
     */
    public BinarySpecRepository(BinaryStorageGateway gateway, BinaryComponentRepository componentRepo) {
        this.gateway = gateway;
        this.componentRepo = componentRepo;
    }

    @Override
    public void save(Component owner, SpecLine line) {
        ensureOpen();
        try {
            RandomAccessFile prsFile = gateway.getPrsFile();

            // Получаем указатель на свободную область из заголовка
            int freeOffset = BinaryIO.readInt(prsFile, 4);

            // Перемещаемся в свободную область
            prsFile.seek(freeOffset);
            long currentOffset = prsFile.getFilePointer();

            // Записываем спецификацию
            // Бит удаления (0 - активен)
            prsFile.writeByte(0);

            // Указатель на компонент
            Component part = line.getComponent();
            long componentOffset = part.getFileOffset();
            if (componentOffset < 0) {
                throw new IllegalStateException("Component not saved: " + part.getName());
            }
            prsFile.writeInt((int) componentOffset);

            // Кратность вхождения
            prsFile.writeShort(line.getQuantity());

            // Указатель на следующую запись (-1)
            prsFile.writeInt(BinaryIO.getNullPointer());

            // Обновляем указатель на свободную область
            int newFreeOffset = (int) (freeOffset + RECORD_SIZE);
            BinaryIO.writeInt(prsFile, 4, newFreeOffset);

            // Обновляем связи в списке спецификаций для владельца
            updateOwnerSpecList(owner, currentOffset);

            // Обновляем первую запись спецификации владельца в .prd
            updateOwnerFirstSpec(owner, currentOffset);

            // Устанавливаем оффсеты в объекте
            line.setFileOffset(currentOffset);
            line.setNextSpecOffset(BinaryIO.getNullPointer());

            // Обновляем кэш
            cache.put(currentOffset, line);
            ownerCache.computeIfAbsent(owner, k -> new ArrayList<>()).add(line);

        } catch (IOException e) {
            throw new RuntimeException("Failed to save spec line: " + e.getMessage(), e);
        }
    }

    /**
     * Обновляет список спецификаций владельца в .prs файле.
     *
     * @param owner владелец спецификации
     * @param newSpecOffset оффсет новой записи
     * @throws IOException при ошибках ввода/вывода
     */
    private void updateOwnerSpecList(Component owner, long newSpecOffset) throws IOException {
        RandomAccessFile prsFile = gateway.getPrsFile();
        long firstSpecOffset = owner.getFirstSpecOffset();

        if (BinaryIO.isNullPointer((int) firstSpecOffset)) {
            // У владельца еще нет спецификаций, эта будет первой
            owner.setFirstSpecOffset((int) newSpecOffset);
        } else {
            // Ищем последнюю запись в списке владельца
            long current = firstSpecOffset;
            while (true) {
                prsFile.seek(current + NEXT_SPEC_OFFSET);
                int next = prsFile.readInt();
                if (BinaryIO.isNullPointer(next)) {
                    // Это последняя запись, обновляем её указатель
                    prsFile.seek(current + NEXT_SPEC_OFFSET);
                    prsFile.writeInt((int) newSpecOffset);
                    break;
                }
                current = next;
            }
        }
    }

    /**
     * Обновляет указатель на первую спецификацию владельца в .prd файле.
     *
     * @param owner владелец спецификации
     * @param firstSpecOffset оффсет первой записи
     * @throws IOException при ошибках ввода/вывода
     */
    private void updateOwnerFirstSpec(Component owner, long firstSpecOffset) throws IOException {
        RandomAccessFile prdFile = gateway.getPrdFile();
        long ownerOffset = owner.getFileOffset();
        if (ownerOffset >= 0) {
            prdFile.seek(ownerOffset + BinaryComponentRepository.getFirstSpecOffset());
            prdFile.writeInt((int) firstSpecOffset);
        }
    }

    @Override
    public List<SpecLine> findByOwner(Component owner) {
        ensureOpen();
        if (!isLoaded) {
            loadAll();
        }

        if (owner == null) {
            // Возвращаем все спецификации
            return new ArrayList<>(cache.values());
        }

        return ownerCache.getOrDefault(owner, Collections.emptyList());
    }

    @Override
    public List<SpecLine> findByOwnerActive(Component owner) {
        return findByOwner(owner).stream()
                .filter(line -> !line.isDeleted())
                .sorted(Comparator.comparing(l -> l.getComponent().getName()))
                .toList();
    }

    @Override
    public void delete(SpecLine line) {
        ensureOpen();
        try {
            RandomAccessFile prsFile = gateway.getPrsFile();
            long offset = line.getFileOffset();

            if (offset >= 0) {
                // Устанавливаем бит удаления в -1
                BinaryIO.setDeleted(prsFile, offset, true);
            }

            line.setDeleted(true);
            // Кэш обновляется автоматически

        } catch (IOException e) {
            throw new RuntimeException("Failed to delete spec line: " + e.getMessage(), e);
        }
    }

    @Override
    public void update(SpecLine line) {
        ensureOpen();
        try {
            RandomAccessFile prsFile = gateway.getPrsFile();
            long offset = line.getFileOffset();

            if (offset >= 0) {
                // Обновляем бит удаления
                BinaryIO.setDeleted(prsFile, offset, line.isDeleted());

                // Обновляем кратность
                prsFile.seek(offset + QUANTITY_OFFSET);
                prsFile.writeShort(line.getQuantity());
            }

            // Кэш обновляется автоматически

        } catch (IOException e) {
            throw new RuntimeException("Failed to update spec line: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteByOwner(Component owner) {
        ensureOpen();
        try {
            RandomAccessFile prsFile = gateway.getPrsFile();
            List<SpecLine> lines = findByOwner(owner);

            for (SpecLine line : lines) {
                long offset = line.getFileOffset();
                if (offset >= 0) {
                    BinaryIO.setDeleted(prsFile, offset, true);
                }
                line.setDeleted(true);
            }

            // Обновляем указатель владельца в .prd
            if (!lines.isEmpty()) {
                RandomAccessFile prdFile = gateway.getPrdFile();
                long ownerOffset = owner.getFileOffset();
                if (ownerOffset >= 0) {
                    prdFile.seek(ownerOffset + BinaryComponentRepository.getFirstSpecOffset());
                    prdFile.writeInt(BinaryIO.getNullPointer());
                }
                owner.setFirstSpecOffset(BinaryIO.getNullPointer());
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to delete specs by owner: " + e.getMessage(), e);
        }
    }
    @Override
    public void clear() {
        cache.clear();
        ownerCache.clear();
        isLoaded = false;
    }
    /**
     * Загружает все спецификации из файла в кэш.
     */
    private void loadAll() {
        try {
            cache.clear();
            ownerCache.clear();

            RandomAccessFile prsFile = gateway.getPrsFile();
            long fileLength = prsFile.length();

            if (fileLength <= 8) {
                isLoaded = true;
                return; // Файл пустой (только заголовок)
            }

            // Начинаем с первой записи после заголовка
            long currentOffset = 8; // После заголовка (8 байт)

            while (currentOffset < fileLength) {
                prsFile.seek(currentOffset);

                // Читаем бит удаления
                byte deleted = prsFile.readByte();

                // Читаем указатель на компонент
                int componentOffset = prsFile.readInt();

                // Читаем кратность
                short quantity = prsFile.readShort();

                // Читаем указатель на следующую запись
                int nextOffset = prsFile.readInt();

                if (componentOffset >= 0) {
                    // Получаем компонент по оффсету
                    Optional<Component> optComponent = componentRepo.findByOffset(componentOffset);
                    if (optComponent.isPresent()) {
                        Component component = optComponent.get();

                        SpecLine line = new SpecLine(component, quantity);
                        line.setDeleted(deleted == -1);
                        line.setFileOffset(currentOffset);
                        line.setNextSpecOffset(nextOffset);

                        cache.put(currentOffset, line);

                        // Находим владельца этой спецификации
                        findAndCacheOwnerForLine(line, currentOffset);
                    }
                }

                // Переходим к следующей записи
                if (nextOffset >= 0) {
                    currentOffset = nextOffset;
                } else {
                    // Если это последняя запись в списке, ищем следующую по порядку
                    currentOffset += RECORD_SIZE;
                    // Пропускаем удаленные записи
                    while (currentOffset < fileLength) {
                        prsFile.seek(currentOffset);
                        byte del = prsFile.readByte();
                        if (del != -1) { // Не удалена? но мы не знаем владельца
                            // Проверим, может это начало новой цепочки
                            // В реальном проекте нужен более сложный алгоритм
                            break;
                        }
                        currentOffset += RECORD_SIZE;
                    }
                }
            }

            isLoaded = true;

        } catch (IOException e) {
            throw new RuntimeException("Failed to load specifications: " + e.getMessage(), e);
        }
    }

    /**
     * Находит владельца для записи спецификации и добавляет в кэш.
     *
     * @param line запись спецификации
     * @param lineOffset оффсет записи
     */
    private void findAndCacheOwnerForLine(SpecLine line, long lineOffset) {
        // Этот метод требует поиска по .prd файлу
        // В реальном проекте нужно найти все компоненты и проверить их firstSpecOffset
        try {
            RandomAccessFile prdFile = gateway.getPrdFile();
            int firstComponentOffset = BinaryIO.readInt(prdFile, 4);

            int current = firstComponentOffset;
            while (!BinaryIO.isNullPointer(current)) {
                prdFile.seek(current);
                byte deleted = prdFile.readByte();
                int firstSpec = prdFile.readInt();
                int next = prdFile.readInt();

                if (firstSpec == lineOffset) {
                    // Нашли владельца
                    Optional<Component> optOwner = componentRepo.findByOffset(current);
                    if (optOwner.isPresent()) {
                        Component owner = optOwner.get();
                        ownerCache.computeIfAbsent(owner, k -> new ArrayList<>()).add(line);
                        return;
                    }
                }

                current = next;
            }
        } catch (IOException e) {
            // Игнорируем, кэш владельцев может быть неполным
        }
    }

    /**
     * Проверяет, открыты ли файлы.
     *
     * @throws IllegalStateException если файлы не открыты
     */
    private void ensureOpen() {
        if (!gateway.isOpen() || gateway.getPrsFile() == null) {
            throw new IllegalStateException("Files are not open");
        }
    }
}