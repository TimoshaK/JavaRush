package lab1.application.ports;

import lab1.domain.models.Component;
import lab1.domain.models.SpecLine;
import java.util.List;

public interface ISpecRepository {
    void save(Component owner, SpecLine line);
    List<SpecLine> findByOwner(Component owner);
    List<SpecLine> findByOwnerActive(Component owner);
    void delete(SpecLine line);
    void update(SpecLine line);
    void deleteByOwner(Component owner);
    void clear();
}