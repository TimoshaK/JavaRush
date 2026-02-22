package lab;

import java.util.logging.Logger;

public class Main {
    private static final Logger log = Logger.getLogger(Main.class.getName());

    public String getGreeting() {
        return "Hello World!";
    }

    public static void main(String[] args) {
        log.info(new Main().getGreeting());
    }
}
