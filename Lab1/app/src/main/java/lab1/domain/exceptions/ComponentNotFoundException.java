package lab1.domain.exceptions;

public class ComponentNotFoundException extends DomainException {
    public ComponentNotFoundException(String name) {
        super("Component not found: " + name);
    }
}