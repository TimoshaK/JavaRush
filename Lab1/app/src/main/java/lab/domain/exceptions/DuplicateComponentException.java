package lab.domain.exceptions;

public class DuplicateComponentException extends DomainException {
    public DuplicateComponentException(String name) {
        super("Component already exists: " + name);
    }
}