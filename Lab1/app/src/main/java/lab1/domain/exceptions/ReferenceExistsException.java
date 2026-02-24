package lab1.domain.exceptions;

public class ReferenceExistsException extends DomainException {
    public ReferenceExistsException(String name) {
        super("Cannot delete component with references: " + name);
    }
}