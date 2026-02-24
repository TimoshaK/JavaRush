package lab1.application.ports;

import lab1.domain.models.Component;
import java.util.List;
import java.util.Optional;

public interface IComponentRepository {
    void save(Component component);
    Optional<Component> findByName(String name);
    List<Component> findAll();
    List<Component> findAllActive();
    void delete(Component component);
    void update(Component component);
    void clear();
}