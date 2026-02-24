package lab1.infrastructure.repository;

import lab1.application.ports.IComponentRepository;
import lab1.domain.models.Component;
import lab1.domain.models.ComponentType;
import lab1.infrastructure.storage.BinaryIO;
import lab1.infrastructure.storage.BinaryStorageGateway;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;

/**
 * Реализация репозитория компонентов с хранением в бинарном .prd файле.
 * Поддерживает прямые ссылки (оффсеты) для быстрого доступа к записям.
 */
public class BinaryComponentRepository implements IComponentRepository {
    private final BinaryStorageGateway gateway;
    private final Map<String, Component> cache = new HashMap<>();
    private final Map<Long, Component> offsetCache = new HashMap<>();
    private boolean isLoaded = false;

    // Смещения полей в записи компонента
    private static final int DELETED_FLAG_OFFSET = 0;       // 1 байт
    private static final int FIRST_SPEC_OFFSET = 1;        // 4 байта
    private static final int NEXT_COMPONENT_OFFSET = 5;    // 4 байта
    private static final int DATA_OFFSET = 9;               // начало данных (имени)

    /**
     * Создает новый экземпляр репозитория.
     *
     * @param gateway шлюз для доступа к файлам
     */
    public BinaryComponentRepository(BinaryStorageGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public void save(Component component) {
        ensureOpen();
        try {
            RandomAccessFile prdFile = gateway.getPrdFile();
            int nameLength = gateway.getNameLength();
            int recordSize = 1 + 4 + 4 + nameLength; // deleted(1) + firstSpec(4) + next(4) + name

            // Получаем указатель на свободную область из заголовка
            int freeOffset = BinaryIO.readInt(prdFile, 8);

            // Перемещаемся в свободную область
            prdFile.seek(freeOffset);

            // Записываем компонент
            long currentOffset = prdFile.getFilePointer();

            // Бит удаления (0 - активен)
            prdFile.writeByte(0);

            // Указатель на первую запись спецификации (-1)
            prdFile.writeInt(BinaryIO.getNullPointer());

            // Указатель на следующий компонент (-1)
            prdFile.writeInt(BinaryIO.getNullPointer());

            // Имя компонента
            byte[] nameBytes = component.getName().getBytes();
            for (int i = 0; i < nameLength; i++) {
                if (i < nameBytes.length) {
                    prdFile.writeByte(nameBytes[i]);
                } else {
                    prdFile.writeByte(' ');
                }
            }

            // Обновляем указатель на свободную область
            int newFreeOffset = (int) (freeOffset + recordSize);
            BinaryIO.writeInt(prdFile, 8, newFreeOffset);

            // Обновляем связи в списке
            int firstComponentOffset = BinaryIO.readInt(prdFile, 4);
            if (BinaryIO.isNullPointer(firstComponentOffset)) {
                // Это первая запись
                BinaryIO.writeInt(prdFile, 4, (int) currentOffset);
            } else {
                // Ищем последнюю запись и добавляем ссылку
                int current = firstComponentOffset;
                while (true) {
                    prdFile.seek(current + NEXT_COMPONENT_OFFSET);
                    int next = prdFile.readInt();
                    if (BinaryIO.isNullPointer(next)) {
                        // Это последняя запись, обновляем её указатель
                        prdFile.seek(current + NEXT_COMPONENT_OFFSET);
                        prdFile.writeInt((int) currentOffset);
                        break;
                    }
                    current = next;
                }
            }

            // Устанавливаем оффсеты в объекте
            component.setFileOffset(currentOffset);
            component.setFirstSpecOffset(BinaryIO.getNullPointer());
            component.setNextComponentOffset(BinaryIO.getNullPointer());

            // Обновляем кэш
            cache.put(component.getName(), component);
            offsetCache.put(currentOffset, component);

        } catch (IOException e) {
            throw new RuntimeException("Failed to save component: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Component> findByName(String name) {
        ensureOpen();

        // Сначала проверяем кэш
        Component cached = cache.get(name);
        if (cached != null) {
            return Optional.of(cached);
        }

        // Если кэш не загружен, загружаем все компоненты
        if (!isLoaded) {
            loadAll();
            cached = cache.get(name);
            return Optional.ofNullable(cached);
        }

        return Optional.empty();
    }

    @Override
    public List<Component> findAll() {
        ensureOpen();
        if (!isLoaded) {
            loadAll();
        }
        return new ArrayList<>(cache.values());
    }

    @Override
    public List<Component> findAllActive() {
        ensureOpen();
        if (!isLoaded) {
            loadAll();
        }
        return cache.values().stream()
                .filter(c -> !c.isDeleted())
                .sorted(Comparator.comparing(Component::getName))
                .toList();
    }

    @Override
    public void delete(Component component) {
        ensureOpen();
        try {
            RandomAccessFile prdFile = gateway.getPrdFile();
            long offset = component.getFileOffset();

            if (offset >= 0) {
                // Устанавливаем бит удаления в -1
                BinaryIO.setDeleted(prdFile, offset, true);
            }

            component.setDeleted(true);
            // Кэш обновляется автоматически (та же ссылка)

        } catch (IOException e) {
            throw new RuntimeException("Failed to delete component: " + e.getMessage(), e);
        }
    }

    @Override
    public void update(Component component) {
        ensureOpen();
        try {
            RandomAccessFile prdFile = gateway.getPrdFile();
            long offset = component.getFileOffset();

            if (offset >= 0) {
                // Обновляем бит удаления
                BinaryIO.setDeleted(prdFile, offset, component.isDeleted());

                // Обновляем имя (если оно изменилось - хотя по логике имя не должно меняться)
                int nameLength = gateway.getNameLength();
                BinaryIO.writeString(prdFile, offset + DATA_OFFSET, component.getName(), nameLength);
            }

            // Кэш обновляется автоматически

        } catch (IOException e) {
            throw new RuntimeException("Failed to update component: " + e.getMessage(), e);
        }
    }

    @Override
    public void clear() {
        cache.clear();
        offsetCache.clear();
        isLoaded = false;
    }

    /**
     * Загружает все компоненты из файла в кэш.
     */
    private void loadAll() {
        try {
            cache.clear();
            offsetCache.clear();

            RandomAccessFile prdFile = gateway.getPrdFile();
            int nameLength = gateway.getNameLength();
            int recordSize = 1 + 4 + 4 + nameLength;

            // Читаем указатель на первую запись
            int currentOffset = BinaryIO.readInt(prdFile, 4);

            while (!BinaryIO.isNullPointer(currentOffset)) {
                prdFile.seek(currentOffset);

                // Читаем бит удаления
                byte deleted = prdFile.readByte();

                // Читаем указатель на первую запись спецификации
                int firstSpecOffset = prdFile.readInt();

                // Читаем указатель на следующий компонент
                int nextOffset = prdFile.readInt();

                // Читаем имя
                byte[] nameBytes = new byte[nameLength];
                prdFile.readFully(nameBytes);
                String name = new String(nameBytes).trim();

                if (!name.isEmpty()) {
                    // Определяем тип по наличию спецификации
                    // В реальном файле тип должен храниться, но по заданию его нет
                    // Поэтому определяем по наличию спецификации
                    ComponentType type;
                    if (!BinaryIO.isNullPointer(firstSpecOffset)) {
                        // Если есть спецификация - это Изделие или Узел
                        // TODO: в реальном проекте нужно хранить тип в файле
                        type = ComponentType.UNIT; // По умолчанию узел
                    } else {
                        type = ComponentType.PART; // Деталь
                    }

                    Component component = new Component(name, type);
                    component.setDeleted(deleted == -1);
                    component.setFileOffset(currentOffset);
                    component.setFirstSpecOffset(firstSpecOffset);
                    component.setNextComponentOffset(nextOffset);

                    cache.put(name, component);
                    offsetCache.put((long) currentOffset, component);
                }

                currentOffset = nextOffset;
            }

            isLoaded = true;

        } catch (IOException e) {
            throw new RuntimeException("Failed to load components: " + e.getMessage(), e);
        }
    }

    /**
     * Получает смещение поля nextComponentOffset в записи компонента.
     *
     * @return смещение в байтах от начала записи
     */
    public static int getNextComponentOffset() {
        return NEXT_COMPONENT_OFFSET;
    }
    /**
     * Проверяет, открыты ли файлы.
     *
     * @throws IllegalStateException если файлы не открыты
     */
    private void ensureOpen() {
        if (!gateway.isOpen() || gateway.getPrdFile() == null) {
            throw new IllegalStateException("Files are not open");
        }
    }
    /**
     * Получает смещение поля firstSpecOffset в записи компонента.
     *
     * @return смещение в байтах от начала записи
     */
    public static int getFirstSpecOffset() {
        return FIRST_SPEC_OFFSET;
    }
    /**
     * Получает компонент по его оффсету в файле.
     *
     * @param offset оффсет записи
     * @return Optional с компонентом или пустой
     */
    public Optional<Component> findByOffset(long offset) {
        ensureOpen();
        if (!isLoaded) {
            loadAll();
        }
        return Optional.ofNullable(offsetCache.get(offset));
    }
}