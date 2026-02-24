package lab1.domain.models;

public class SpecLine {
    private Component component;
    private int quantity;
    private boolean deleted;
    private long fileOffset;          // смещение в .prs
    private long nextSpecOffset;       // смещение следующей записи (-1 если последняя)

    public SpecLine(Component component, int quantity) {
        this.component = component;
        this.quantity = quantity;
        this.deleted = false;
        this.fileOffset = -1;
        this.nextSpecOffset = -1;
    }

    public Component getComponent() { return component; }
    public int getQuantity() { return quantity; }
    public boolean isDeleted() { return deleted; }
    public long getFileOffset() { return fileOffset; }
    public long getNextSpecOffset() { return nextSpecOffset; }

    public void setQuantity(int quantity) { this.quantity = quantity; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
    public void setFileOffset(long offset) { this.fileOffset = offset; }
    public void setNextSpecOffset(long offset) { this.nextSpecOffset = offset; }
}