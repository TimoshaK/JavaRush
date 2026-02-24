package lab1;

import lab1.adapters.cli.ConsoleApp;
import lab1.adapters.gui.MainFrame;

import javax.swing.*;
import java.util.logging.*;

/**
 * Главный класс приложения.
 * Запускает консольную или GUI версию программы.
 */
public class Main {
    private static final Logger log = Logger.getLogger(Main.class.getName());

    static {
        // Настройка логирования
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.WARNING);
        for (Handler handler : rootLogger.getHandlers()) {
            rootLogger.removeHandler(handler);
        }

        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.WARNING);
        handler.setFormatter(new SimpleFormatter() {
            @Override
            public String format(LogRecord record) {
                return record.getMessage() + "\n";
            }
        });
        rootLogger.addHandler(handler);
    }

    /**
     * Точка входа в программу.
     *
     * @param args аргументы командной строки
     */
    public static void main(String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("--cli")) {
            // Запуск CLI версии
            ConsoleApp.main(args);
        } else {
            // Запуск GUI версии (по умолчанию)
            SwingUtilities.invokeLater(() -> {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception e) {
                    // Игнорируем
                }
                new MainFrame().setVisible(true);
            });
        }
    }
}