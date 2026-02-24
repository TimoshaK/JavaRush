package lab1.domain.models;

public class Component {
    private String name;
    private ComponentType type;
    private boolean deleted;
    private long fileOffset;          // смещение в .prd
    private long firstSpecOffset;      // смещение первой записи в .prs (-1 если нет)
    private long nextComponentOffset;  // смещение следующего компонента (-1 если последний)

    public Component(String name, ComponentType type) {
        this.name = name;
        this.type = type;
        this.deleted = false;
        this.fileOffset = -1;
        this.firstSpecOffset = -1;
        this.nextComponentOffset = -1;
    }

    public String getName() { return name; }
    public ComponentType getType() { return type; }
    public boolean isDeleted() { return deleted; }
    public long getFileOffset() { return fileOffset; }
    public long getFirstSpecOffset() { return firstSpecOffset; }
    public long getNextComponentOffset() { return nextComponentOffset; }

    public void setDeleted(boolean deleted) { this.deleted = deleted; }
    public void setFileOffset(long offset) { this.fileOffset = offset; }
    public void setFirstSpecOffset(long offset) { this.firstSpecOffset = offset; }
    public void setNextComponentOffset(long offset) { this.nextComponentOffset = offset; }
}