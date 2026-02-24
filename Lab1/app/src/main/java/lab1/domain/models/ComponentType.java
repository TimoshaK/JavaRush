package lab1.domain.models;

public enum ComponentType {
    PRODUCT("Изделие"),
    UNIT("Узел"),
    PART("Деталь");

    private final String displayName;

    ComponentType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean canHaveSpecification() {
        return this != PART;
    }
}