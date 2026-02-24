package lab1.application.services;

import lab1.domain.models.Component;
import lab1.domain.models.ComponentType;
import lab1.domain.models.SpecLine;
import lab1.domain.exceptions.*;
import lab1.application.ports.IComponentRepository;
import lab1.application.ports.ISpecRepository;
import lab1.application.ports.IStorageGateway;
import lab1.infrastructure.repository.BinaryComponentRepository;
import lab1.infrastructure.repository.BinarySpecRepository;
import lab1.infrastructure.storage.BinaryIO;
import lab1.infrastructure.storage.BinaryStorageGateway;

import java.io.RandomAccessFile;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис для работы со спецификациями.
 * Реализует все use cases системы.
 */
public class SpecificationService {
    private final IComponentRepository componentRepo;
    private final ISpecRepository specRepo;
    private final IStorageGateway storageGateway;
    private boolean isOpen;

    /**
     * Создает новый экземпляр сервиса.
     *
     * @param componentRepo репозиторий компонентов
     * @param specRepo репозиторий спецификаций
     * @param storageGateway шлюз для работы с файлами
     */
    public SpecificationService(IComponentRepository componentRepo,
                                ISpecRepository specRepo,
                                IStorageGateway storageGateway) {
        this.componentRepo = componentRepo;
        this.specRepo = specRepo;
        this.storageGateway = storageGateway;
        this.isOpen = false;
    }

    /**
     * Создает новые файлы для хранения данных.
     *
     * @param prdFile имя файла компонентов (.prd)
     * @param nameLength максимальная длина имени компонента
     * @param prsFile имя файла спецификаций (.prs)
     * @throws DomainException если файл уже существует
     */
    public void createFiles(String prdFile, int nameLength, String prsFile) {
        if (storageGateway.exists(prdFile)) {
            throw new DomainException("File already exists. Use Open command.");
        }
        storageGateway.create(prdFile, prsFile, nameLength);
        componentRepo.clear();
        specRepo.clear();
        isOpen = true;
    }

    /**
     * Открывает существующие файлы.
     *
     * @param prdFile имя файла компонентов (.prd)
     * @param prsFile имя файла спецификаций (.prs)
     * @throws DomainException если файл не найден или имеет неверный формат
     */
    public void openFiles(String prdFile, String prsFile) {
        if (!storageGateway.exists(prdFile)) {
            throw new DomainException("File not found: " + prdFile);
        }
        storageGateway.open(prdFile, prsFile);
        componentRepo.clear();
        specRepo.clear();
        isOpen = true;
    }

    /**
     * Закрывает открытые файлы.
     */
    public void close() {
        if (isOpen) {
            storageGateway.flush();
            storageGateway.close();
            isOpen = false;
        }
    }

    /**
     * Добавляет новый компонент.
     *
     * @param name имя компонента
     * @param type тип компонента
     * @throws DuplicateComponentException если компонент с таким именем уже существует
     * @throws DomainException если файлы не открыты
     */
    public void addComponent(String name, ComponentType type) {
        checkOpen();
        if (componentRepo.findByName(name).isPresent()) {
            throw new DuplicateComponentException(name);
        }
        componentRepo.save(new Component(name, type));
    }

    /**
     * Добавляет элемент в спецификацию.
     *
     * @param ownerName имя владельца спецификации
     * @param partName имя компонента (детали или узла)
     * @param quantity количество
     * @throws ComponentNotFoundException если компонент не найден
     * @throws InvalidComponentTypeException если владелец не может иметь спецификацию
     * @throws DomainException если файлы не открыты
     */
    public void addSpecItem(String ownerName, String partName, int quantity) {
        checkOpen();
        Component owner = componentRepo.findByName(ownerName)
                .orElseThrow(() -> new ComponentNotFoundException(ownerName));
        Component part = componentRepo.findByName(partName)
                .orElseThrow(() -> new ComponentNotFoundException(partName));

        if (!owner.getType().canHaveSpecification()) {
            throw new InvalidComponentTypeException("Parts cannot have specification");
        }
        if (owner.isDeleted() || part.isDeleted()) {
            throw new DomainException("Cannot use deleted components");
        }

        specRepo.save(owner, new SpecLine(part, quantity));
    }

    /**
     * Логически удаляет компонент.
     *
     * @param name имя компонента
     * @throws ComponentNotFoundException если компонент не найден
     * @throws ReferenceExistsException если на компонент есть ссылки
     * @throws DomainException если файлы не открыты
     */
    public void deleteComponent(String name) {
        checkOpen();
        Component component = componentRepo.findByName(name)
                .orElseThrow(() -> new ComponentNotFoundException(name));

        // Проверяем, есть ли ссылки на этот компонент
        if (hasReferences(component)) {
            throw new ReferenceExistsException(name);
        }

        component.setDeleted(true);
        componentRepo.update(component);
    }

    /**
     * Проверяет, есть ли ссылки на компонент.
     *
     * @param component компонент для проверки
     * @return true если есть ссылки
     */
    private boolean hasReferences(Component component) {
        // Проверяем все спецификации
        return specRepo.findByOwner(null).stream()
                .filter(line -> !line.isDeleted())
                .anyMatch(line -> line.getComponent().equals(component));
    }

    /**
     * Логически удаляет элемент из спецификации.
     *
     * @param ownerName имя владельца спецификации
     * @param partName имя компонента для удаления
     * @throws ComponentNotFoundException если компонент не найден
     * @throws InvalidComponentTypeException если владелец не может иметь спецификацию
     * @throws DomainException если файлы не открыты
     */
    public void deleteSpecItem(String ownerName, String partName) {
        checkOpen();
        Component owner = componentRepo.findByName(ownerName)
                .orElseThrow(() -> new ComponentNotFoundException(ownerName));
        Component part = componentRepo.findByName(partName)
                .orElseThrow(() -> new ComponentNotFoundException(partName));

        if (!owner.getType().canHaveSpecification()) {
            throw new InvalidComponentTypeException("Parts cannot have specification");
        }

        specRepo.findByOwnerActive(owner).stream()
                .filter(line -> line.getComponent().equals(part))
                .findFirst()
                .ifPresent(line -> {
                    line.setDeleted(true);
                    specRepo.update(line);
                });
    }

    /**
     * Восстанавливает логически удаленный компонент.
     *
     * @param name имя компонента
     * @throws ComponentNotFoundException если компонент не найден
     * @throws DomainException если файлы не открыты
     */
    public void restoreComponent(String name) {
        checkOpen();
        Component component = componentRepo.findByName(name)
                .orElseThrow(() -> new ComponentNotFoundException(name));

        if (component.isDeleted()) {
            component.setDeleted(false);
            componentRepo.update(component);
        }

        // Восстанавливаем спецификации компонента
        specRepo.findByOwner(component).forEach(line -> {
            if (line.isDeleted()) {
                line.setDeleted(false);
                specRepo.update(line);
            }
        });
    }

    /**
     * Восстанавливает все логически удаленные записи.
     *
     * @throws DomainException если файлы не открыты
     */
    public void restoreAll() {
        checkOpen();
        componentRepo.findAll().forEach(c -> {
            if (c.isDeleted()) {
                c.setDeleted(false);
                componentRepo.update(c);
            }
        });
        specRepo.findByOwner(null).forEach(line -> {
            if (line.isDeleted()) {
                line.setDeleted(false);
                specRepo.update(line);
            }
        });

        // Восстанавливаем алфавитный порядок
        reorderAlphabetically();
    }

    /**
     * Восстанавливает алфавитный порядок компонентов.
     */
    private void reorderAlphabetically() {
        List<Component> activeComponents = componentRepo.findAllActive();
        activeComponents.sort(Comparator.comparing(Component::getName));

        // Перестраиваем список в памяти
        // В бинарном файле порядок определяется указателями,
        // поэтому нужно обновить nextComponentOffset у всех записей
        try {
            BinaryStorageGateway binaryGateway = (BinaryStorageGateway) storageGateway;
            BinaryComponentRepository binaryRepo = (BinaryComponentRepository) componentRepo;

            // Получаем прямой доступ к файлу
            RandomAccessFile prdFile = binaryGateway.getPrdFile();

            // Обновляем указатель на первую запись
            if (!activeComponents.isEmpty()) {
                long firstOffset = activeComponents.get(0).getFileOffset();
                BinaryIO.writeInt(prdFile, 4, (int) firstOffset);
            }

            // Обновляем указатели nextComponentOffset
            for (int i = 0; i < activeComponents.size() - 1; i++) {
                Component current = activeComponents.get(i);
                Component next = activeComponents.get(i + 1);

                prdFile.seek(current.getFileOffset() + BinaryComponentRepository.getNextComponentOffset());
                prdFile.writeInt((int) next.getFileOffset());

                current.setNextComponentOffset((int) next.getFileOffset());
            }

            // Последний компонент указывает на -1
            if (!activeComponents.isEmpty()) {
                Component last = activeComponents.get(activeComponents.size() - 1);
                prdFile.seek(last.getFileOffset() + BinaryComponentRepository.getNextComponentOffset());
                prdFile.writeInt(BinaryIO.getNullPointer());
                last.setNextComponentOffset(BinaryIO.getNullPointer());
            }

        } catch (Exception e) {
            throw new DomainException("Failed to reorder components: " + e.getMessage());
        }
    }

    /**
     * Выполняет физическое удаление помеченных записей.
     * Перестраивает файлы, делая все записи смежными.
     *
     * @throws DomainException если файлы не открыты
     */
    public void truncate() {
        checkOpen();
        List<Component> activeComponents = componentRepo.findAllActive();
        List<Component> deletedComponents = componentRepo.findAll().stream()
                .filter(Component::isDeleted)
                .collect(Collectors.toList());

        // Удаляем спецификации удаленных компонентов
        deletedComponents.forEach(specRepo::deleteByOwner);

        // Сортируем активные компоненты по алфавиту
        activeComponents.sort(Comparator.comparing(Component::getName));

        // Собираем все активные спецификации
        Map<Component, List<SpecLine>> activeSpecs = new HashMap<>();
        for (Component owner : activeComponents) {
            List<SpecLine> ownerSpecs = specRepo.findByOwnerActive(owner);
            if (!ownerSpecs.isEmpty()) {
                ownerSpecs.sort(Comparator.comparing(l -> l.getComponent().getName()));
                activeSpecs.put(owner, ownerSpecs);
            }
        }

        // Выполняем компактацию через шлюз
        // Для этого нужно создать новые файлы и переписать в них данные
        performPhysicalTruncate(activeComponents, activeSpecs);

        // Очищаем кэши и перезагружаем данные
        componentRepo.clear();
        specRepo.clear();

        // Переоткрываем файлы для загрузки новых данных
        BinaryStorageGateway binaryGateway = (BinaryStorageGateway) storageGateway;
        openFiles(binaryGateway.getPrdFilename(), binaryGateway.getPrsFilename());

        storageGateway.flush();
    }

    /**
     * Выполняет физическую компактацию файлов.
     *
     * @param activeComponents список активных компонентов
     * @param activeSpecs карта активных спецификаций
     */
    private void performPhysicalTruncate(List<Component> activeComponents,
                                         Map<Component, List<SpecLine>> activeSpecs) {
        BinaryStorageGateway binaryGateway = (BinaryStorageGateway) storageGateway;
        String oldPrdFile = binaryGateway.getPrdFilename();
        String oldPrsFile = binaryGateway.getPrsFilename();
        int nameLength = binaryGateway.getNameLength();

        // Закрываем текущие файлы
        close();

        // Создаем временные файлы
        String tempPrd = "temp_" + oldPrdFile;
        String tempPrs = "temp_" + oldPrsFile;

        try {
            // Создаем новые файлы
            BinaryStorageGateway tempGateway = new BinaryStorageGateway();
            tempGateway.create(tempPrd, tempPrs, nameLength);

            // Записываем компоненты
            RandomAccessFile tempPrdFile = tempGateway.getPrdFile();
            for (Component comp : activeComponents) {
                long offset = tempPrdFile.getFilePointer();

                // Запись компонента
                tempPrdFile.writeByte(0); // активен
                tempPrdFile.writeInt(BinaryIO.getNullPointer()); // firstSpec (временно)
                tempPrdFile.writeInt(BinaryIO.getNullPointer()); // next (будет обновлено позже)

                // Имя
                byte[] nameBytes = comp.getName().getBytes();
                for (int i = 0; i < nameLength; i++) {
                    if (i < nameBytes.length) {
                        tempPrdFile.writeByte(nameBytes[i]);
                    } else {
                        tempPrdFile.writeByte(' ');
                    }
                }

                comp.setFileOffset(offset);
            }

            // Обновляем указатели next
            for (int i = 0; i < activeComponents.size() - 1; i++) {
                Component current = activeComponents.get(i);
                Component next = activeComponents.get(i + 1);

                tempPrdFile.seek(current.getFileOffset() + BinaryComponentRepository.getNextComponentOffset());
                tempPrdFile.writeInt((int) next.getFileOffset());
            }

            // Обновляем указатель на первую запись
            if (!activeComponents.isEmpty()) {
                BinaryIO.writeInt(tempPrdFile, 4, (int) activeComponents.get(0).getFileOffset());
            }

            // Записываем спецификации
            RandomAccessFile tempPrsFile = tempGateway.getPrsFile();
            Map<Component, Long> firstSpecOffsets = new HashMap<>();

            for (Map.Entry<Component, List<SpecLine>> entry : activeSpecs.entrySet()) {
                Component owner = entry.getKey();
                List<SpecLine> specs = entry.getValue();

                Long firstOffset = null;
                Long prevOffset = null;

                for (SpecLine line : specs) {
                    long offset = tempPrsFile.getFilePointer();

                    tempPrsFile.writeByte(0); // активен
                    tempPrsFile.writeInt((int) owner.getFileOffset());
                    tempPrsFile.writeShort(line.getQuantity());
                    tempPrsFile.writeInt(BinaryIO.getNullPointer()); // next (временно)

                    if (firstOffset == null) {
                        firstOffset = offset;
                    }
                    if (prevOffset != null) {
                        // Обновляем next предыдущей записи
                        tempPrsFile.seek(prevOffset + NEXT_SPEC_OFFSET);
                        tempPrsFile.writeInt((int) offset);
                    }
                    prevOffset = offset;
                }

                if (firstOffset != null) {
                    firstSpecOffsets.put(owner, firstOffset);
                }
            }

            // Обновляем указатели firstSpec в компонентах
            for (Map.Entry<Component, Long> entry : firstSpecOffsets.entrySet()) {
                Component owner = entry.getKey();
                tempPrdFile.seek(owner.getFileOffset() + BinaryComponentRepository.getFirstSpecOffset());
                tempPrdFile.writeInt(entry.getValue().intValue());
            }

            // Обновляем указатель на свободную область в .prs
            long prsEnd = tempPrsFile.length();
            BinaryIO.writeInt(tempPrsFile, 4, (int) prsEnd);

            tempGateway.flush();
            tempGateway.close();

            // Заменяем старые файлы новыми
            java.nio.file.Files.move(
                    java.nio.file.Paths.get(tempPrd),
                    java.nio.file.Paths.get(oldPrdFile),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );
            java.nio.file.Files.move(
                    java.nio.file.Paths.get(tempPrs),
                    java.nio.file.Paths.get(oldPrsFile),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );

        } catch (Exception e) {
            throw new DomainException("Failed to truncate: " + e.getMessage());
        }
    }

    // Добавляем недостающие константы
    private static final int NEXT_SPEC_OFFSET = 7;

    /**
     * Выводит спецификацию компонента в виде дерева.
     *
     * @param name имя компонента
     * @return строковое представление спецификации
     * @throws ComponentNotFoundException если компонент не найден
     * @throws InvalidComponentTypeException если компонент не может иметь спецификацию
     * @throws DomainException если файлы не открыты
     */
    public String printComponent(String name) {
        checkOpen();
        Component component = componentRepo.findByName(name)
                .orElseThrow(() -> new ComponentNotFoundException(name));

        if (!component.getType().canHaveSpecification()) {
            throw new InvalidComponentTypeException("Parts have no specification");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(component.getName()).append("\n");
        printSpecTree(component, sb, 1);
        return sb.toString();
    }

    /**
     * Рекурсивно выводит дерево спецификации.
     *
     * @param owner владелец спецификации
     * @param sb StringBuilder для накопления результата
     * @param level уровень вложенности
     */
    private void printSpecTree(Component owner, StringBuilder sb, int level) {
        String indent = "| ".repeat(level);
        for (SpecLine line : specRepo.findByOwnerActive(owner)) {
            Component c = line.getComponent();
            sb.append(indent).append(c.getName());
            if (c.getType() == ComponentType.PART) {
                sb.append(" [x").append(line.getQuantity()).append("]");
            }
            sb.append("\n");

            if (c.getType().canHaveSpecification() && !specRepo.findByOwnerActive(c).isEmpty()) {
                printSpecTree(c, sb, level + 1);
            }
        }
    }

    /**
     * Выводит список всех активных компонентов.
     *
     * @return строковое представление списка компонентов
     * @throws DomainException если файлы не открыты
     */
    public String printAll() {
        checkOpen();
        StringBuilder sb = new StringBuilder();
        componentRepo.findAllActive().stream()
                .sorted(Comparator.comparing(Component::getName))
                .forEach(c -> sb.append(c.getName())
                        .append(" ")
                        .append(c.getType().getDisplayName())
                        .append("\n"));
        return sb.toString();
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
     * Возвращает шлюз для работы с файлами.
     *
     * @return шлюз хранения
     */
    public IStorageGateway getStorageGateway() {
        return storageGateway;
    }
    /**
     * Проверяет, что файлы открыты.
     *
     * @throws DomainException если файлы не открыты
     */
    private void checkOpen() {
        if (!isOpen) {
            throw new DomainException("No open file. Use Create or Open first.");
        }
    }
}