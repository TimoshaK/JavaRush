package lab.application.services;

import lab1.domain.models.Component;
import lab1.domain.models.ComponentType;
import lab1.domain.models.SpecLine;
import lab1.domain.exceptions.*;
import lab1.application.ports.IComponentRepository;
import lab1.application.ports.ISpecRepository;
import lab1.application.ports.IStorageGateway;

import java.util.*;
import java.util.stream.Collectors;

public class SpecificationService {
    private final IComponentRepository componentRepo;
    private final ISpecRepository specRepo;
    private final IStorageGateway storageGateway;
    private boolean isOpen;

    public SpecificationService(IComponentRepository componentRepo,
                                ISpecRepository specRepo,
                                IStorageGateway storageGateway) {
        this.componentRepo = componentRepo;
        this.specRepo = specRepo;
        this.storageGateway = storageGateway;
        this.isOpen = false;
    }

    public void createFiles(String prdFile, int nameLength, String prsFile) {
        if (storageGateway.exists(prdFile)) {
            throw new DomainException("File already exists. Use Open command.");
        }
        storageGateway.create(prdFile, prsFile, nameLength);
        componentRepo.clear();
        specRepo.clear();
        isOpen = true;
    }

    public void openFiles(String prdFile, String prsFile) {
        if (!storageGateway.exists(prdFile)) {
            throw new DomainException("File not found: " + prdFile);
        }
        storageGateway.open(prdFile, prsFile);
        componentRepo.clear();
        specRepo.clear();
        isOpen = true;
    }

    public void close() {
        if (isOpen) {
            storageGateway.flush();
            storageGateway.close();
            isOpen = false;
        }
    }

    public void addComponent(String name, ComponentType type) {
        checkOpen();
        if (componentRepo.findByName(name).isPresent()) {
            throw new DuplicateComponentException(name);
        }
        componentRepo.save(new Component(name, type));
    }

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

    public void deleteComponent(String name) {
        checkOpen();
        Component component = componentRepo.findByName(name)
                .orElseThrow(() -> new ComponentNotFoundException(name));

        if (!specRepo.findByOwnerActive(component).isEmpty()) {
            throw new ReferenceExistsException(name);
        }

        component.setDeleted(true);
        componentRepo.update(component);
    }

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

    public void restoreComponent(String name) {
        checkOpen();
        Component component = componentRepo.findByName(name)
                .orElseThrow(() -> new ComponentNotFoundException(name));

        if (component.isDeleted()) {
            component.setDeleted(false);
            componentRepo.update(component);
        }

        // Restore its spec lines
        specRepo.findByOwner(component).forEach(line -> {
            if (line.isDeleted()) {
                line.setDeleted(false);
                specRepo.update(line);
            }
        });
    }

    public void restoreAll() {
        checkOpen();
        componentRepo.findAll().forEach(c -> {
            if (c.isDeleted()) {
                c.setDeleted(false);
                componentRepo.update(c);
            }
        });
        specRepo.findByOwner(null).forEach(line -> { // null = all owners
            if (line.isDeleted()) {
                line.setDeleted(false);
                specRepo.update(line);
            }
        });
    }

    public void truncate() {
        checkOpen();
        List<Component> activeComponents = componentRepo.findAllActive();
        List<Component> deletedComponents = componentRepo.findAll().stream()
                .filter(Component::isDeleted)
                .collect(Collectors.toList());

        // Remove spec lines of deleted components
        deletedComponents.forEach(specRepo::deleteByOwner);

        // Rebuild in alphabetical order
        activeComponents.sort(Comparator.comparing(Component::getName));
        componentRepo.clear();
        activeComponents.forEach(componentRepo::save);

        // Also rebuild specs for remaining components
        Map<Component, List<SpecLine>> specs = new HashMap<>();
        activeComponents.forEach(c -> specs.put(c, specRepo.findByOwnerActive(c)));
        specRepo.clear();
        specs.forEach((owner, lines) -> {
            lines.sort(Comparator.comparing(l -> l.getComponent().getName()));
            lines.forEach(line -> {
                line.setDeleted(false);
                specRepo.save(owner, line);
            });
        });

        storageGateway.flush();
    }

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

    public boolean isOpen() { return isOpen; }

    private void checkOpen() {
        if (!isOpen) {
            throw new DomainException("No open file. Use Create or Open first.");
        }
    }
}