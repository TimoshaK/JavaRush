package lab1.adapters.cli;

import lab1.application.services.SpecificationService;
import lab1.domain.exceptions.DomainException;
import lab1.domain.models.ComponentType;
import lab1.infrastructure.repository.BinaryComponentRepository;
import lab1.infrastructure.repository.BinarySpecRepository;
import lab1.infrastructure.storage.BinaryStorageGateway;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Scanner;
import java.util.logging.*;

/**
 * Консольное приложение для работы со спецификациями.
 * Реализует интерфейс командной строки.
 */
public class ConsoleApp {
    private static final Logger log = Logger.getLogger(ConsoleApp.class.getName());
    private static final String PROMPT = "PS> ";

    private final SpecificationService service;
    private final CommandParser parser;
    private boolean running;

    /**
     * Создает новое консольное приложение.
     */
    public ConsoleApp() {
        BinaryStorageGateway gateway = new BinaryStorageGateway();
        BinaryComponentRepository componentRepo = new BinaryComponentRepository(gateway);
        BinarySpecRepository specRepo = new BinarySpecRepository(gateway, componentRepo);

        this.service = new SpecificationService(componentRepo, specRepo, gateway);
        this.parser = new CommandParser();
        this.running = true;

        setupLogger();
    }

    /**
     * Настраивает логирование.
     */
    private void setupLogger() {
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.WARNING);
        handler.setFormatter(new SimpleFormatter() {
            @Override
            public String format(LogRecord record) {
                return record.getMessage() + "\n";
            }
        });

        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.WARNING);
        for (Handler h : rootLogger.getHandlers()) {
            rootLogger.removeHandler(h);
        }
        rootLogger.addHandler(handler);
    }

    /**
     * Запускает приложение.
     */
    public void run() {
        System.out.println("Система управления спецификациями");
        System.out.println("Введите 'Help' для списка команд");

        try (Scanner scanner = new Scanner(System.in)) {
            while (running) {
                System.out.print(PROMPT);
                String input = scanner.nextLine().trim();

                if (input.isEmpty()) {
                    continue;
                }

                try {
                    processCommand(input);
                } catch (DomainException e) {
                    System.out.println("Ошибка: " + e.getMessage());
                } catch (Exception e) {
                    System.out.println("Неожиданная ошибка: " + e.getMessage());
                    log.log(Level.SEVERE, "Unexpected error", e);
                }
            }
        }

        System.out.println("Программа завершена.");
    }

    /**
     * Обрабатывает команду.
     *
     * @param input строка команды
     */
    private void processCommand(String input) {
        CommandParser.ParseResult result = parser.parse(input);

        switch (result.getType()) {
            case CREATE:
                cmdCreate(result);
                break;

            case OPEN:
                cmdOpen(result);
                break;

            case INPUT_COMPONENT:
                cmdInputComponent(result);
                break;

            case INPUT_SPEC:
                cmdInputSpec(result);
                break;

            case DELETE_COMPONENT:
                cmdDeleteComponent(result);
                break;

            case DELETE_SPEC:
                cmdDeleteSpec(result);
                break;

            case RESTORE_COMPONENT:
                cmdRestoreComponent(result);
                break;

            case RESTORE_ALL:
                cmdRestoreAll();
                break;

            case PRINT_COMPONENT:
                cmdPrintComponent(result);
                break;

            case PRINT_ALL:
                cmdPrintAll();
                break;

            case TRUNCATE:
                cmdTruncate();
                break;

            case HELP:
                cmdHelp(result);
                break;

            case EXIT:
                cmdExit();
                break;

            default:
                System.out.println("Неизвестная команда. Введите 'Help' для списка команд.");
        }
    }

    /**
     * Обрабатывает команду Create.
     *
     * @param result результат парсинга
     */
    private void cmdCreate(lab1.adapters.cli.CommandParser.ParseResult result) {
        String prdFile = result.getParameter(0);
        String prsFile = result.getParameter(2);
        int nameLength = result.getNamedParam("nameLength", Integer.class);

        // Проверяем, не открыты ли уже файлы
        if (service.isOpen()) {
            System.out.println("Файлы уже открыты. Сначала закройте их командой Exit.");
            return;
        }

        // Проверяем существование файла
        BinaryStorageGateway gateway = (BinaryStorageGateway) service.getStorageGateway();
        if (gateway.exists(prdFile)) {
            System.out.print("Файл уже существует. Перезаписать? (y/N): ");
            Scanner scanner = new Scanner(System.in);
            String answer = scanner.nextLine().trim().toLowerCase();
            if (!answer.equals("y") && !answer.equals("yes")) {
                System.out.println("Операция отменена.");
                return;
            }
        }

        service.createFiles(prdFile, nameLength, prsFile);
        System.out.println("Файлы созданы и открыты.");
    }

    /**
     * Обрабатывает команду Open.
     *
     * @param result результат парсинга
     */
    private void cmdOpen(CommandParser.ParseResult result) {
        String prdFile = result.getParameter(0);
        String prsFile = result.getParameter(1);

        if (service.isOpen()) {
            System.out.println("Файлы уже открыты. Сначала закройте их командой Exit.");
            return;
        }

        service.openFiles(prdFile, prsFile);
        System.out.println("Файлы открыты.");
    }

    /**
     * Обрабатывает команду Input для компонента.
     *
     * @param result результат парсинга
     */
    private void cmdInputComponent(CommandParser.ParseResult result) {
        String name = result.getParameter(0);
        ComponentType type = result.getNamedParam("type", ComponentType.class);

        service.addComponent(name, type);
        System.out.println("Компонент добавлен: " + name + " (" + type.getDisplayName() + ")");
    }

    /**
     * Обрабатывает команду Input для спецификации.
     *
     * @param result результат парсинга
     */
    private void cmdInputSpec(CommandParser.ParseResult result) {
        String owner = result.getParameter(0);
        String part = result.getParameter(1);
        int quantity = result.getNamedParam("quantity", Integer.class);

        service.addSpecItem(owner, part, quantity);
        System.out.println("Спецификация обновлена: " + owner + " <- " + part + " x" + quantity);
    }

    /**
     * Обрабатывает команду Delete для компонента.
     *
     * @param result результат парсинга
     */
    private void cmdDeleteComponent(CommandParser.ParseResult result) {
        String name = result.getParameter(0);

        service.deleteComponent(name);
        System.out.println("Компонент удален (логически): " + name);
    }

    /**
     * Обрабатывает команду Delete для спецификации.
     *
     * @param result результат парсинга
     */
    private void cmdDeleteSpec(CommandParser.ParseResult result) {
        String owner = result.getParameter(0);
        String part = result.getParameter(1);

        service.deleteSpecItem(owner, part);
        System.out.println("Элемент удален из спецификации: " + owner + " <- " + part);
    }

    /**
     * Обрабатывает команду Restore для компонента.
     *
     * @param result результат парсинга
     */
    private void cmdRestoreComponent(CommandParser.ParseResult result) {
        String name = result.getParameter(0);

        service.restoreComponent(name);
        System.out.println("Компонент восстановлен: " + name);
    }

    /**
     * Обрабатывает команду Restore всех.
     */
    private void cmdRestoreAll() {
        service.restoreAll();
        System.out.println("Все удаленные записи восстановлены.");
    }

    /**
     * Обрабатывает команду Print для компонента.
     *
     * @param result результат парсинга
     */
    private void cmdPrintComponent(CommandParser.ParseResult result) {
        String name = result.getParameter(0);

        String output = service.printComponent(name);
        System.out.println(output);
    }

    /**
     * Обрабатывает команду Print всех компонентов.
     */
    private void cmdPrintAll() {
        String output = service.printAll();
        if (output.isEmpty()) {
            System.out.println("Нет активных компонентов.");
        } else {
            System.out.print(output);
        }
    }

    /**
     * Обрабатывает команду Truncate.
     */
    private void cmdTruncate() {
        System.out.print("Вы уверены? Это физически удалит помеченные записи. (y/N): ");
        Scanner scanner = new Scanner(System.in);
        String answer = scanner.nextLine().trim().toLowerCase();
        if (!answer.equals("y") && !answer.equals("yes")) {
            System.out.println("Операция отменена.");
            return;
        }

        service.truncate();
        System.out.println("Компактация выполнена.");
    }

    /**
     * Обрабатывает команду Help.
     *
     * @param result результат парсинга
     */
    private void cmdHelp(CommandParser.ParseResult result) {
        String helpText = CommandParser.getHelp();

        if (result.getParameters().isEmpty()) {
            // Вывод на экран
            System.out.println(helpText);
        } else {
            // Вывод в файл
            String filename = result.getParameter(0);
            try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
                writer.print(helpText);
                System.out.println("Справка сохранена в файл: " + filename);
            } catch (IOException e) {
                System.out.println("Ошибка при записи в файл: " + e.getMessage());
            }
        }
    }

    /**
     * Обрабатывает команду Exit.
     */
    private void cmdExit() {
        if (service.isOpen()) {
            service.close();
            System.out.println("Файлы закрыты.");
        }
        running = false;
    }

    /**
     * Точка входа в приложение.
     *
     * @param args аргументы командной строки
     */
    public static void main(String[] args) {
        new ConsoleApp().run();
    }
}