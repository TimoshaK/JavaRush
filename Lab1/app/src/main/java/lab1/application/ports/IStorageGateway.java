package lab1.application.ports;

public interface IStorageGateway {
    void create(String prdFile, String prsFile, int nameLength);
    void open(String prdFile, String prsFile);
    void close();
    void flush();
    boolean exists(String filename);
    void delete(String filename);
}